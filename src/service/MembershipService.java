package service;

import dao.MembershipDAO;
import exception.BusinessException;
import model.Customer;
import model.Membership;
import model.MembershipPlan;
import model.StaffMember;
import model.User;
import util.AppLogger;

import java.sql.SQLException;
import java.time.LocalDate;

/** Business rules for selling and cancelling memberships. */
public class MembershipService {
    private final MembershipDAO membershipDAO = new MembershipDAO();

    /**
     * The first day of a new membership: today, or - if the customer is still a member -
     * the day after the current membership ends (so buying early never loses days).
     */
    public LocalDate startDateFor(Customer c) throws SQLException {
        LocalDate today = LocalDate.now();
        if (c == null || c.isWalkIn()) return today;
        Membership current = membershipDAO.findLatestForCustomer(c.getId());
        if (current != null && current.getDisplayStatus().equals("ACTIVE") && !current.getExpiryDate().isBefore(today)) {
            return current.getExpiryDate().plusDays(1);
        }
        return today;
    }

    /** Sells a plan. The start and end dates are worked out here; the payment may be split. Returns the new membership. */
    public Membership sell(Customer c, MembershipPlan plan, Checkout payment, StaffMember billedBy, User user)
            throws SQLException, BusinessException {
        if (c == null) throw new BusinessException("Choose the customer first.");
        if (c.isWalkIn()) throw new BusinessException("A walk-in guest cannot become a member. Add the customer with name and phone first.");
        if (plan == null) throw new BusinessException("Choose a plan.");
        if (plan.getPrice().signum() <= 0) {
            throw new BusinessException("The price of the " + plan.getName() + " plan is not set yet.\nThe owner sets it in Settings > Membership Plans.");
        }
        if (billedBy == null) throw new BusinessException("Choose who is billing (\"Billed by\").");
        if (payment.getFinalTotal().compareTo(plan.getPrice()) != 0) throw new BusinessException("The amount does not match the plan price.");
        if (!payment.isFullyPaid()) throw new BusinessException(String.format("Rs. %,.2f is still to be paid.", payment.getRemaining()));
        LocalDate start = startDateFor(c);
        LocalDate end = plan.endDateFor(start);
        int id = membershipDAO.sell(c.getId(), plan.getId(), start, end, plan.getDiscountPercent(), plan.getPrice(),
                payment.getMethodLabel(), payment.getParts(), billedBy.getId(), user.getId());
        AppLogger.info("Membership " + id + " (" + plan.getName() + ") sold to " + c.getName() + " for " + plan.getPrice()
                + " (" + payment.getMethodLabel() + "), billed by " + billedBy.getName());
        return membershipDAO.findLatestForCustomer(c.getId());
    }

    /** Owner only. The membership stops giving a discount; the money already paid stays in the revenue. */
    public void cancel(Membership m, User user) throws SQLException, BusinessException {
        if (!user.canCorrectFinancialRecords()) throw new BusinessException("Only the owner can cancel a membership.");
        if (m.getDisplayStatus().equals("CANCELLED")) throw new BusinessException("This membership is already cancelled.");
        membershipDAO.cancel(m.getId());
        AppLogger.info("Membership " + m.getId() + " of " + m.getCustomerName() + " cancelled by " + user.getUsername());
    }
}
