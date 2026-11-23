package service;

import dao.BillDAO;
import dao.BookingDAO;
import dao.GameTableDAO;
import dao.MembershipDAO;
import dao.PriceDAO;
import dao.SessionDAO;
import exception.BusinessException;
import exception.ClubException;
import exception.InvalidInputException;
import model.Bill;
import model.Booking;
import model.Customer;
import model.GameSession;
import model.GameTable;
import model.User;
import util.AppLogger;
import util.Validator;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;

/** Business logic for starting a game, ending it, creating the bill and correcting mistakes. */
public class SessionService {
    /** Staff may cancel a game that ran for less than this many minutes (a mistaken start). */
    public static final int MISTAKE_MINUTES = 10;
    /** Staff may reopen an ended game for this many minutes after the bill was made. */
    public static final int REOPEN_MINUTES = 30;
    /** The start time of a running game can be moved back by at most this many hours. */
    public static final int MAX_BACKDATE_HOURS = 12;

    private final SessionDAO sessionDAO = new SessionDAO();
    private final GameTableDAO tableDAO = new GameTableDAO();
    private final PriceDAO priceDAO = new PriceDAO();
    private final MembershipDAO membershipDAO = new MembershipDAO();
    private final BillDAO billDAO = new BillDAO();
    private final BookingDAO bookingDAO = new BookingDAO();
    private final BillingCalculator calculator = new BillingCalculator();

    public int startSession(GameTable table, Customer customer, User user) throws SQLException, BusinessException {
        return startSession(table, customer, user, LocalDateTime.now());
    }

    /** The extra startTime parameter lets the automatic tests create old sessions. */
    public int startSession(GameTable table, Customer customer, User user, LocalDateTime startTime)
            throws SQLException, BusinessException {
        if (tableDAO.isUnderMaintenance(table.getId())) {
            throw new BusinessException(table.getName() + " is under maintenance. Games cannot be started on it.");
        }
        // a booked table can only be used by the customer who booked it (for the booked time)
        Booking booking = bookingDAO.findBookingAt(table.getId(), startTime);
        if (booking != null && booking.getCustomerId() != customer.getId()) {
            LocalTime end = booking.getTime().plusMinutes(Math.round(booking.getDurationHours().doubleValue() * 60));
            throw new BusinessException(table.getName() + " is booked for " + booking.getCustomerName() + " from "
                    + booking.getTime() + " to " + end + ".\nStart the game for " + booking.getCustomerName()
                    + ", or cancel the booking first.");
        }
        BigDecimal rate = priceDAO.getRate(table.getType(), customer.getGender());
        if (rate == null) throw new BusinessException("No price is set for " + table.getType() + " / " + customer.getGender());
        try {
            // MySQL DATETIME keeps whole seconds only, so we cut the fraction ourselves
            // (otherwise MySQL would round it and billing could be off by a second)
            return sessionDAO.start(table.getId(), customer.getId(), user.getId(), startTime.withNano(0), rate);
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {   // duplicate key = our "one active session per table" rule
                throw new BusinessException(table.getName() + " already has a running session.");
            }
            throw e;
        }
    }

    public Bill endSession(int sessionId, User user) throws SQLException, BusinessException {
        return endSession(sessionId, LocalDateTime.now(), user);
    }

    /** Ends the session, calculates the bill and saves everything. Returns the saved bill. */
    public Bill endSession(int sessionId, LocalDateTime endTime, User user) throws SQLException, BusinessException {
        endTime = endTime.withNano(0);
        GameSession session = sessionDAO.findById(sessionId);
        if (session == null || !session.isActive()) throw new BusinessException("This session is not running.");

        long minutes = Math.max(0, Duration.between(session.getStartTime(), endTime).toMinutes());
        // Which discount rule applies? Both classes below implement the DiscountPolicy interface.
        BigDecimal memberPercent = membershipDAO.discountFor(session.getCustomerId(), endTime.toLocalDate());
        DiscountPolicy policy = memberPercent.signum() > 0 ? new MembershipDiscount(memberPercent) : new NoDiscount();
        BigDecimal discount = policy.getDiscountPercent();
        BigDecimal subtotal = calculator.calculateSubtotal(minutes, session.getHourlyRate());
        BigDecimal finalAmount = calculator.calculateFinalAmount(minutes, session.getHourlyRate(), policy);

        int billId = sessionDAO.endSession(sessionId, session.getCustomerId(), endTime, (int) minutes,
                session.getHourlyRate(), subtotal, discount, finalAmount, user.getId());
        return billDAO.findById(billId);
    }

    /** Cancels a game that was started by mistake. No bill is created. */
    public void cancelSession(int sessionId, User user, String reason) throws SQLException, ClubException {
        String cleanReason = Validator.requireNotEmpty(reason, "Reason");
        GameSession session = sessionDAO.findById(sessionId);
        if (session == null || !session.isActive()) throw new BusinessException("This game is not running.");
        if (session.getMinutes(LocalDateTime.now()) > MISTAKE_MINUTES && !user.canCorrectFinancialRecords()) {
            throw new BusinessException("Only the owner can cancel a game that has run for more than " + MISTAKE_MINUTES
                    + " minutes.\nEnd the game normally, or ask the owner.");
        }
        if (!sessionDAO.cancelSession(sessionId, user.getId(), cleanReason)) {
            throw new BusinessException("This game is not running any more.");
        }
        AppLogger.info("Game " + sessionId + " (" + session.getTableName() + ") cancelled by " + user.getUsername() + ": " + cleanReason);
    }

    /** Moves the start time of a running game (for example the staff forgot to press Start). */
    public void adjustStartTime(int sessionId, LocalDateTime newStart, User user) throws SQLException, ClubException {
        LocalDateTime now = LocalDateTime.now().withNano(0);
        newStart = newStart.withNano(0);
        if (newStart.isAfter(now)) throw new InvalidInputException("The start time cannot be in the future.");
        if (newStart.isBefore(now.minusHours(MAX_BACKDATE_HOURS))) {
            throw new InvalidInputException("The start time cannot be more than " + MAX_BACKDATE_HOURS + " hours ago.");
        }
        GameSession session = sessionDAO.findById(sessionId);
        if (session == null || !session.isActive()) throw new BusinessException("This game is not running.");
        if (!sessionDAO.adjustStartTime(sessionId, newStart)) throw new BusinessException("This game is not running any more.");
        AppLogger.info("Game " + sessionId + " (" + session.getTableName() + ") start time changed from "
                + session.getStartTime() + " to " + newStart + " by " + user.getUsername());
    }

    /** Brings an accidentally ended game back. Only possible while its bill is still unpaid. */
    public void reopenSession(Bill bill, User user) throws SQLException, BusinessException {
        if (bill.isCancelled()) throw new BusinessException("This bill is already cancelled.");
        if (!bill.getPaymentLabel().equals("UNPAID")) {
            throw new BusinessException("This bill is already paid. Ask the owner to cancel (refund) the bill first.");
        }
        long minutesSinceEnd = Duration.between(bill.getCreatedAt(), LocalDateTime.now()).toMinutes();
        if (minutesSinceEnd > REOPEN_MINUTES && !user.canCorrectFinancialRecords()) {
            throw new BusinessException("Only the owner can reopen a game that ended more than " + REOPEN_MINUTES + " minutes ago.");
        }
        GameSession session = sessionDAO.findById(bill.getSessionId());
        try {
            sessionDAO.reopenSession(session.getId(), bill.getId(), session.getCustomerId(), user.getId(),
                    "Game reopened by " + user.getUsername());
        } catch (SQLException e) {
            if (e.getErrorCode() == 1062) {   // the table was given to somebody else in the meantime
                throw new BusinessException(session.getTableName() + " is in use again, so this game cannot be reopened.");
            }
            throw e;
        }
        AppLogger.info("Game " + session.getId() + " reopened (bill " + bill.getBillNumber() + " cancelled) by " + user.getUsername());
    }

    /** The bill so far for a running session (shown live on the screen). */
    public BigDecimal estimateAmount(GameSession session, LocalDateTime now) {
        return calculator.calculateFinalAmount(session.getMinutes(now), session.getHourlyRate(), BigDecimal.ZERO);
    }
}
