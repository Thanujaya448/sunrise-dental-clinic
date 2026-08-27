package lk.sunrise.clinic.pattern;

import lk.sunrise.clinic.domain.Patient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * TDD cycle 2 - Strategy pattern.
 *
 * Requirement under test : FR-17, ASM-12
 * Rules                  : staff/family 15%, senior citizen 10%,
 *                          returning patient 5%, otherwise none.
 *                          Only one applies; the most generous wins.
 *                          The discount never touches the consultation fee.
 *
 * Derived test data:
 *   boundary values       - age 64 / 65 / 66 around the senior threshold
 *                         - 3 / 4 / 5 previous visits around the loyalty one
 *   equivalence partition - each rule, plus the no-rule case
 *   worked example        - 28,000 subtotal at 10% must give exactly 2,800.00,
 *                           the same figure sp_generate_bill produces
 */
@DisplayName("Discount rules (FR-17, ASM-12)")
class DiscountStrategyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 1);
    private static final BigDecimal SUBTOTAL = new BigDecimal("28000.00");

    private final DiscountPolicy policy = new DiscountPolicy();

    private Patient patient(int age, boolean staffFamily, int previousVisits) {
        return new Patient("Test Patient", TODAY.minusYears(age), staffFamily, previousVisits);
    }

    // -----------------------------------------------------------------
    //  The arithmetic each rule produces
    // -----------------------------------------------------------------
    @Nested
    @DisplayName("each rule calculates the right amount")
    class Arithmetic {

        @Test
        @DisplayName("senior citizen takes 10% of 28,000 = 2,800.00")
        void senior() {
            assertEquals(new BigDecimal("2800.00"),
                    new SeniorCitizenDiscount().calculate(SUBTOTAL));
        }

        @Test
        @DisplayName("loyalty takes 5% = 1,400.00")
        void loyalty() {
            assertEquals(new BigDecimal("1400.00"),
                    new LoyaltyDiscount().calculate(SUBTOTAL));
        }

        @Test
        @DisplayName("staff and family take 15% = 4,200.00")
        void staff() {
            assertEquals(new BigDecimal("4200.00"),
                    new StaffFamilyDiscount().calculate(SUBTOTAL));
        }

        @Test
        @DisplayName("no discount is zero, not null")
        void none() {
            assertEquals(new BigDecimal("0.00"), new NoDiscount().calculate(SUBTOTAL));
        }

        @Test
        @DisplayName("rounds half up to two decimal places")
        void rounding() {
            // 1,234.55 at 10% = 123.455, which must round to 123.46
            assertEquals(new BigDecimal("123.46"),
                    new SeniorCitizenDiscount().calculate(new BigDecimal("1234.55")));
        }

        @Test
        @DisplayName("a zero subtotal earns nothing")
        void zeroSubtotal() {
            assertEquals(new BigDecimal("0.00"),
                    new SeniorCitizenDiscount().calculate(BigDecimal.ZERO));
        }

        @Test
        @DisplayName("a null subtotal is handled, not thrown")
        void nullSubtotal() {
            assertEquals(new BigDecimal("0.00"), new SeniorCitizenDiscount().calculate(null));
        }
    }

    // -----------------------------------------------------------------
    //  Boundary values on the senior-citizen threshold
    // -----------------------------------------------------------------
    @Nested
    @DisplayName("boundary values")
    class Boundaries {

        @ParameterizedTest(name = "age {0} -> {1}")
        @CsvSource({
                "64, No discount",
                "65, Senior citizen 10%",
                "66, Senior citizen 10%"
        })
        @DisplayName("the senior threshold is 65, inclusive")
        void seniorThreshold(int age, String expectedLabel) {
            assertEquals(expectedLabel,
                    policy.resolve(patient(age, false, 0), TODAY).getLabel());
        }

        @ParameterizedTest(name = "{0} previous visits -> {1}")
        @CsvSource({
                "3, No discount",
                "4, Returning patient 5%",
                "5, Returning patient 5%"
        })
        @DisplayName("loyalty starts on the fifth visit")
        void loyaltyThreshold(int previousVisits, String expectedLabel) {
            assertEquals(expectedLabel,
                    policy.resolve(patient(30, false, previousVisits), TODAY).getLabel());
        }
    }

    // -----------------------------------------------------------------
    //  Precedence when a patient qualifies for more than one rule
    // -----------------------------------------------------------------
    @Nested
    @DisplayName("only one rule applies and the most generous wins")
    class Precedence {

        @Test
        @DisplayName("staff family beats senior citizen")
        void staffBeatsSenior() {
            assertInstanceOf(StaffFamilyDiscount.class,
                    policy.resolve(patient(70, true, 0), TODAY));
        }

        @Test
        @DisplayName("senior citizen beats loyalty")
        void seniorBeatsLoyalty() {
            assertInstanceOf(SeniorCitizenDiscount.class,
                    policy.resolve(patient(70, false, 9), TODAY));
        }

        @Test
        @DisplayName("qualifying for all three still applies only the largest")
        void allThree() {
            assertEquals(new BigDecimal("4200.00"),
                    policy.resolve(patient(80, true, 20), TODAY).calculate(SUBTOTAL));
        }

        @Test
        @DisplayName("a patient qualifying for nothing gets NoDiscount, never null")
        void nothing() {
            assertInstanceOf(NoDiscount.class,
                    policy.resolve(patient(30, false, 0), TODAY));
        }

        @Test
        @DisplayName("a null patient is safe")
        void nullPatient() {
            assertInstanceOf(NoDiscount.class, policy.resolve(null, TODAY));
        }
    }
}