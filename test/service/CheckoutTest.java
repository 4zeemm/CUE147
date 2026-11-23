package service;

import exception.InvalidInputException;
import org.junit.jupiter.api.Test;
import service.Checkout.DiscountType;

import model.OrderItem;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/** Discounts, totals and split payments of the checkout screen. */
class CheckoutTest {
    private static BigDecimal rs(String v) { return new BigDecimal(v); }
    private static void same(String expected, BigDecimal actual) {
        assertEquals(0, rs(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }

    @Test
    void noDiscountByDefault() {
        Checkout c = new Checkout(rs("500"));
        same("0", c.getDiscountAmount());
        same("500", c.getFinalTotal());
        same("500", c.getRemaining());
        assertFalse(c.isFullyPaid());
        assertNull(c.getMethodLabel());
    }

    @Test
    void flatDiscount() throws Exception {
        Checkout c = new Checkout(rs("500"));
        c.setDiscount(DiscountType.FLAT, rs("50"));
        same("50", c.getDiscountAmount());
        same("450", c.getFinalTotal());
    }

    @Test
    void percentDiscountIsRoundedToPaise() throws Exception {
        Checkout c = new Checkout(rs("270"));
        c.setDiscount(DiscountType.PERCENT, rs("7"));
        same("18.90", c.getDiscountAmount());
        same("251.10", c.getFinalTotal());
    }

    @Test
    void hundredPercentGivesZeroAndNeedsNoPayment() throws Exception {
        Checkout c = new Checkout(rs("270"));
        c.setDiscount(DiscountType.PERCENT, rs("100"));
        same("0", c.getFinalTotal());
        assertTrue(c.isFullyPaid());
    }

    @Test
    void discountRulesAreChecked() {
        Checkout c = new Checkout(rs("500"));
        assertThrows(InvalidInputException.class, () -> c.setDiscount(DiscountType.PERCENT, rs("101")));
        assertThrows(InvalidInputException.class, () -> c.setDiscount(DiscountType.PERCENT, rs("-5")));
        assertThrows(InvalidInputException.class, () -> c.setDiscount(DiscountType.FLAT, rs("600")));   // total would be below Rs. 0
        same("500", c.getFinalTotal());   // nothing changed
    }

    @Test
    void switchingBackToNoDiscountClearsIt() throws Exception {
        Checkout c = new Checkout(rs("500"));
        c.setDiscount(DiscountType.FLAT, rs("100"));
        c.setOccasion("Christmas");
        c.setDiscount(DiscountType.NONE, rs("100"));
        same("500", c.getFinalTotal());
        assertEquals("", c.getOccasion());
    }

    @Test
    void occasionIsKeptWithADiscount() throws Exception {
        Checkout c = new Checkout(rs("500"));
        c.setDiscount(DiscountType.PERCENT, rs("10"));
        c.setOccasion("  New Year ");
        assertEquals("New Year", c.getOccasion());
        assertThrows(InvalidInputException.class, () -> c.setOccasion("x".repeat(41)));
    }

    @Test
    void splitPaymentShowsRemainingAfterEachPart() throws Exception {
        Checkout c = new Checkout(rs("500"));
        c.addPayment("UPI", rs("200"));
        same("300", c.getRemaining());
        assertFalse(c.isFullyPaid());
        c.addPayment("CASH", rs("300"));
        same("0", c.getRemaining());
        assertTrue(c.isFullyPaid());
        assertEquals("SPLIT", c.getMethodLabel());
        assertEquals(2, c.getParts().size());
    }

    @Test
    void splitIsCalculatedOnTheDiscountedTotal() throws Exception {
        Checkout c = new Checkout(rs("500"));
        c.setDiscount(DiscountType.PERCENT, rs("10"));
        c.addPayment("UPI", rs("200"));
        same("250", c.getRemaining());
        c.addPayment("CASH", rs("250"));
        assertTrue(c.isFullyPaid());
    }

    @Test
    void paymentsCannotBeMoreThanTheRemainder() throws Exception {
        Checkout c = new Checkout(rs("500"));
        c.addPayment("UPI", rs("200"));
        assertThrows(InvalidInputException.class, () -> c.addPayment("CASH", rs("400")));
        assertThrows(InvalidInputException.class, () -> c.addPayment("CASH", rs("0")));
        assertThrows(InvalidInputException.class, () -> c.addPayment("CASH", rs("-10")));
        assertThrows(InvalidInputException.class, () -> c.addPayment("CARD", rs("10")));
        assertThrows(InvalidInputException.class, () -> c.addPayment("CASH", rs("10.005")));
        same("300", c.getRemaining());
    }

    @Test
    void removingAPaymentGivesTheMoneyBackToRemaining() throws Exception {
        Checkout c = new Checkout(rs("500"));
        c.addPayment("UPI", rs("200"));
        c.addPayment("CASH", rs("300"));
        c.removePayment(0);
        same("200", c.getRemaining());
        assertEquals("CASH", c.getMethodLabel());
    }

    @Test
    void discountCannotDropTheTotalBelowWhatIsAlreadyPaid() throws Exception {
        Checkout c = new Checkout(rs("500"));
        c.addPayment("CASH", rs("480"));
        assertThrows(InvalidInputException.class, () -> c.setDiscount(DiscountType.FLAT, rs("50")));
        c.setDiscount(DiscountType.FLAT, rs("20"));
        assertTrue(c.isFullyPaid());
    }

    @Test
    void sameMethodTwiceIsNotSplit() throws Exception {
        Checkout c = new Checkout(rs("500"));
        c.addPayment("CASH", rs("100"));
        c.addPayment("CASH", rs("400"));
        assertEquals("CASH", c.getMethodLabel());
    }

    private static OrderItem item(int id, String name, String price, int qty) {
        return new OrderItem(id, name, "Beverages", rs(price), qty, LocalDateTime.now());
    }

    @Test
    void foodAndDrinksAreAddedToTheSubtotal() throws Exception {
        Checkout c = new Checkout(rs("270"));
        c.addItem(item(1, "Cold Coffee", "80", 2));
        c.addItem(item(2, "Chips", "30", 1));
        same("190", c.getItemsAmount());
        same("460", c.getSubtotal());
        same("460", c.getFinalTotal());
    }

    @Test
    void theSameItemTwiceBecomesOneLine() throws Exception {
        Checkout c = new Checkout(rs("270"));
        c.addItem(item(1, "Cold Coffee", "80", 1));
        c.addItem(item(1, "Cold Coffee", "80", 2));
        assertEquals(1, c.getItems().size());
        assertEquals(3, c.getItems().get(0).getQuantity());
        same("510", c.getSubtotal());
    }

    @Test
    void quantityMustBeBetweenOneAnd99() {
        Checkout c = new Checkout(rs("270"));
        assertThrows(InvalidInputException.class, () -> c.addItem(item(1, "Tea", "20", 0)));
        assertThrows(InvalidInputException.class, () -> c.addItem(item(1, "Tea", "20", 100)));
    }

    @Test
    void discountIsTakenFromTableTimePlusFood() throws Exception {
        Checkout c = new Checkout(rs("270"));
        c.addItem(item(1, "Cold Coffee", "80", 1));   // subtotal 350
        c.setDiscount(DiscountType.PERCENT, rs("10"));
        same("35", c.getDiscountAmount());
        same("315", c.getFinalTotal());
    }

    @Test
    void removingAnItemLowersTheTotal() throws Exception {
        Checkout c = new Checkout(rs("270"));
        c.addItem(item(1, "Cold Coffee", "80", 1));
        c.removeItem(0);
        same("270", c.getFinalTotal());
        assertTrue(c.getItems().isEmpty());
    }

    @Test
    void anItemCannotBeRemovedWhenPaymentsWouldBeMoreThanTheTotal() throws Exception {
        Checkout c = new Checkout(rs("270"));
        c.addItem(item(1, "Cold Coffee", "80", 1));
        c.addPayment("UPI", rs("300"));
        assertThrows(InvalidInputException.class, () -> c.removeItem(0));
        assertEquals(1, c.getItems().size());   // nothing changed
    }

    @Test
    void anItemCannotBeRemovedWhenTheFlatDiscountWouldBeTooBig() throws Exception {
        Checkout c = new Checkout(rs("100"));
        c.addItem(item(1, "Meal", "200", 1));
        c.setDiscount(DiscountType.FLAT, rs("150"));
        assertThrows(InvalidInputException.class, () -> c.removeItem(0));
        same("150", c.getFinalTotal());
    }
}
