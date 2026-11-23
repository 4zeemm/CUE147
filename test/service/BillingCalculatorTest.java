package service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * JUnit tests for the club's billing rules (run by Maven: ./mvnw test).
 * Rules: under 1 hour = 1 hour; 5 free minutes after each full hour; then rate/60 per minute;
 * member discount; final amount rounded DOWN to Rs. 5.
 */
class BillingCalculatorTest {
    private final BillingCalculator calc = new BillingCalculator();

    @ParameterizedTest(name = "{0} min at Rs.{1}/h, {2}% off -> Rs.{3}")
    @CsvSource({
            "0,   300, 0,  300",    // minimum one hour
            "20,  300, 0,  300",
            "59,  300, 0,  300",
            "60,  300, 0,  300",
            "63,  300, 0,  300",    // inside the 5 free minutes
            "65,  300, 0,  300",    // last free minute
            "66,  300, 0,  305",    // 1 charged minute = 5
            "70,  300, 0,  325",
            "90,  300, 0,  425",
            "119, 300, 0,  570",
            "120, 300, 0,  600",
            "126, 300, 0,  605",
            "225, 270, 0,  990",
            "66,  270, 0,  270",    // 274.50 rounds down to 270
            "68,  320, 0,  335",    // 336.00 rounds down to 335
            "150, 120, 0,  290",
            "60,  300, 10, 270",
            "90,  300, 10, 380",    // 425 - 42.50 = 382.50 -> 380
    })
    void finalAmountFollowsTheClubRules(long minutes, String rate, String discount, String expected) {
        BigDecimal actual = calc.calculateFinalAmount(minutes, new BigDecimal(rate), new BigDecimal(discount));
        assertEquals(0, actual.compareTo(new BigDecimal(expected)), "got " + actual);
    }

    @Test
    void discountPoliciesGiveTheSameAnswerAsThePercent() {
        assertEquals(0, calc.calculateFinalAmount(90, new BigDecimal("300"), new NoDiscount()).compareTo(new BigDecimal("425")));
        assertEquals(0, calc.calculateFinalAmount(90, new BigDecimal("300"), new MembershipDiscount(new BigDecimal("10")))
                .compareTo(new BigDecimal("380")));
    }

    @Test
    void roundDownToFive() {
        assertEquals(0, calc.roundDownToFive(new BigDecimal("274.99")).compareTo(new BigDecimal("270")));
        assertEquals(0, calc.roundDownToFive(new BigDecimal("275.00")).compareTo(new BigDecimal("275")));
        assertEquals(0, calc.roundDownToFive(new BigDecimal("4.99")).compareTo(BigDecimal.ZERO));
    }
}
