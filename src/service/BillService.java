package service;

import dao.BillDAO;
import exception.BusinessException;
import exception.ClubException;
import exception.InvalidInputException;
import model.Bill;
import model.StaffMember;
import model.User;
import util.AppLogger;
import util.Validator;

import java.sql.SQLException;

/** Business rules for payments and for correcting bills. */
public class BillService {
    private final BillDAO billDAO = new BillDAO();

    /** Records the payment of an unpaid bill and remembers which staff member took the money. */
    public void recordPayment(Bill bill, String method, User user) throws SQLException, BusinessException {
        if (bill.isCancelled()) throw new BusinessException("This bill is cancelled and cannot be paid.");
        if (!method.equals("CASH") && !method.equals("UPI")) throw new BusinessException("Payment method must be Cash or UPI.");
        try {
            billDAO.recordPayment(bill.getId(), method, user.getId());
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("already paid")) {
                throw new BusinessException("This bill is already paid.");
            }
            throw e;
        }
    }

    /** A Checkout for an unpaid bill, with the discount that was saved for it earlier (if any). */
    public Checkout startCheckout(Bill bill) {
        Checkout c = new Checkout(bill.getChargesSubtotal());
        try {
            c.setDiscount(Checkout.DiscountType.valueOf(bill.getBillDiscountType()), bill.getBillDiscountValue());
            c.setOccasion(bill.getDiscountOccasion());
        } catch (InvalidInputException e) {
            AppLogger.info("Saved discount of bill " + bill.getBillNumber() + " ignored: " + e.getMessage());
        }
        return c;
    }

    /** Closes the bill: discount, every payment part and "Billed by" are saved together (one transaction). */
    public void completeCheckout(Bill bill, Checkout c, StaffMember billedBy, User user) throws SQLException, BusinessException {
        checkOpen(bill, billedBy);
        if (!c.isFullyPaid()) {
            throw new BusinessException(String.format("Rs. %,.2f is still to be paid.%nThe bill can be closed when the payments equal the final total.", c.getRemaining()));
        }
        try {
            billDAO.completeCheckout(bill.getId(), c.getDiscountType().name(), c.getDiscountValue(), c.getDiscountAmount(),
                    c.getOccasion(), c.getFinalTotal(), c.getMethodLabel(), c.getParts(), billedBy.getId(), user.getId());
        } catch (SQLException e) {
            throw alreadyPaid(e);
        }
        AppLogger.info("Bill " + bill.getBillNumber() + " paid: " + c.getFinalTotal() + " (" + c.getMethodLabel() + "), billed by " + billedBy.getName());
    }

    /** "Pay later": the bill stays unpaid, but its discount and "Billed by" are kept. */
    public void saveForLater(Bill bill, Checkout c, StaffMember billedBy) throws SQLException, BusinessException {
        checkOpen(bill, billedBy);
        try {
            billDAO.saveForLater(bill.getId(), c.getDiscountType().name(), c.getDiscountValue(), c.getDiscountAmount(),
                    c.getOccasion(), c.getFinalTotal(), billedBy.getId());
        } catch (SQLException e) {
            throw alreadyPaid(e);
        }
    }

    private void checkOpen(Bill bill, StaffMember billedBy) throws BusinessException {
        if (bill.isCancelled()) throw new BusinessException("This bill is cancelled and cannot be paid.");
        if (bill.isPaid()) throw new BusinessException("This bill is already paid.");
        if (billedBy == null) throw new BusinessException("Choose who is billing (\"Billed by\").");
    }

    private SQLException alreadyPaid(SQLException e) throws BusinessException {
        if (e.getMessage() != null && e.getMessage().contains("already paid")) {
            throw new BusinessException("This bill was already paid or cancelled (maybe on another computer).\nOpen it again from the Billing screen.");
        }
        return e;
    }

    /**
     * Cancels a wrong bill (and refunds it if it was paid). Only the owner may do this.
     * The bill stays in the database with status CANCELLED, the reason and who cancelled it.
     */
    public void cancelBill(Bill bill, User user, String reason) throws SQLException, ClubException {
        requireOwner(user, "cancel or refund a bill");
        String cleanReason = Validator.requireNotEmpty(reason, "Reason");
        if (bill.isCancelled()) throw new BusinessException("This bill is already cancelled.");
        try {
            billDAO.cancelBill(bill.getId(), user.getId(), cleanReason);
        } catch (SQLException e) {
            if (e.getMessage() != null && e.getMessage().contains("already cancelled")) {
                throw new BusinessException("This bill is already cancelled.");
            }
            throw e;
        }
        AppLogger.info("Bill " + bill.getBillNumber() + " cancelled by " + user.getUsername() + ": " + cleanReason);
    }

    /** Corrects the method (Cash/UPI) of a bill that is already paid. Only the owner may do this. */
    public void changePaymentMethod(Bill bill, String newMethod, User user) throws SQLException, BusinessException {
        requireOwner(user, "change a payment method");
        if (!newMethod.equals("CASH") && !newMethod.equals("UPI")) throw new BusinessException("Payment method must be Cash or UPI.");
        if (!bill.isPaid()) throw new BusinessException("Only a PAID bill has a payment method to change.");
        if ("SPLIT".equals(bill.getPaymentMethod())) {
            throw new BusinessException("This bill was paid with Cash AND UPI, so its method cannot be changed.\nCancel the bill and bill it again if it is wrong.");
        }
        if (bill.getPaymentMethod().equals(newMethod)) throw new BusinessException("The payment method is already " + newMethod + ".");
        if (!billDAO.changePaymentMethod(bill.getId(), newMethod)) throw new BusinessException("The payment could not be changed.");
        AppLogger.info("Bill " + bill.getBillNumber() + " payment method changed from " + bill.getPaymentMethod()
                + " to " + newMethod + " by " + user.getUsername());
    }

    private void requireOwner(User user, String action) throws BusinessException {
        if (!user.canCorrectFinancialRecords()) throw new BusinessException("Only the owner can " + action + ".");
    }
}
