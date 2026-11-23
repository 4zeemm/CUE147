package service;

import dao.BillDAO;
import exception.BusinessException;
import exception.ClubException;
import model.Bill;
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
        if (bill.getPaymentMethod().equals(newMethod)) throw new BusinessException("The payment method is already " + newMethod + ".");
        if (!billDAO.changePaymentMethod(bill.getId(), newMethod)) throw new BusinessException("The payment could not be changed.");
        AppLogger.info("Bill " + bill.getBillNumber() + " payment method changed from " + bill.getPaymentMethod()
                + " to " + newMethod + " by " + user.getUsername());
    }

    private void requireOwner(User user, String action) throws BusinessException {
        if (!user.canCorrectFinancialRecords()) throw new BusinessException("Only the owner can " + action + ".");
    }
}
