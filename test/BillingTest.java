import service.BillingCalculator;
import service.DiscountPolicy;
import service.MembershipDiscount;
import service.NoDiscount;
import java.math.BigDecimal;

/** Tests the billing rules with different durations. Run: ./test.sh */
public class BillingTest {
    static int passed = 0, failed = 0;
    static BillingCalculator calc = new BillingCalculator();

    static void check(String name, long minutes, String rate, String discount, String expected) {
        BigDecimal actual = calc.calculateFinalAmount(minutes, new BigDecimal(rate), new BigDecimal(discount));
        boolean ok = actual.compareTo(new BigDecimal(expected)) == 0;
        System.out.println((ok ? "PASS  " : "FAIL  ") + name + "  -> " + actual + (ok ? "" : "  (expected " + expected + ")"));
        if (ok) passed++; else failed++;
    }

    public static void main(String[] args) {
        System.out.println("=== Billing calculator tests ===");
        check("0 min (minimum one hour), rate 300", 0, "300", "0", "300");
        check("20 min, rate 300", 20, "300", "0", "300");
        check("59 min, rate 300", 59, "300", "0", "300");
        check("exactly 60 min, rate 300", 60, "300", "0", "300");
        check("1h 03m (inside 5 min grace), rate 300", 63, "300", "0", "300");
        check("1h 05m (last free minute), rate 300", 65, "300", "0", "300");
        check("1h 06m (spec example), rate 300", 66, "300", "0", "305");
        check("1h 10m, rate 300 = 300+25", 70, "300", "0", "325");
        check("1h 30m, rate 300 = 300+125", 90, "300", "0", "425");
        check("1h 59m, rate 300 = 300+270", 119, "300", "0", "570");
        check("2h 00m, rate 300", 120, "300", "0", "600");
        check("2h 06m, rate 300", 126, "300", "0", "605");
        check("3h 45m, rate 270 (pool men) = 810+180", 225, "270", "0", "990");
        check("rounding: 1h 06m, rate 270 = 274.50 -> 270", 66, "270", "0", "270");
        check("rounding: 1h 08m, rate 320 = 336.00 -> 335", 68, "320", "0", "335");
        check("PS5 2h 30m, rate 120 = 240+50", 150, "120", "0", "290");
        check("10% member discount, 1h, rate 300 -> 270", 60, "300", "10", "270");
        check("10% member discount, 1h 30m, rate 300: 425-42.5=382.5 -> 380", 90, "300", "10", "380");
        // The DiscountPolicy interface: two classes, one contract
        DiscountPolicy none = new NoDiscount();
        DiscountPolicy member = new MembershipDiscount(new BigDecimal("10"));
        BigDecimal rate = new BigDecimal("300");
        boolean ok1 = calc.calculateFinalAmount(90, rate, none).compareTo(new BigDecimal("425")) == 0;
        boolean ok2 = calc.calculateFinalAmount(90, rate, member).compareTo(new BigDecimal("380")) == 0;
        boolean ok3 = none.getDiscountPercent().signum() == 0 && member.getName().contains("10");
        System.out.println((ok1 ? "PASS  " : "FAIL  ") + "NoDiscount policy: 1h30m at 300 -> 425");
        System.out.println((ok2 ? "PASS  " : "FAIL  ") + "MembershipDiscount(10%) policy: 1h30m at 300 -> 380");
        System.out.println((ok3 ? "PASS  " : "FAIL  ") + "Both policies answer getDiscountPercent()/getName()");
        for (boolean ok : new boolean[]{ok1, ok2, ok3}) { if (ok) passed++; else failed++; }
        System.out.println(passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }
}
