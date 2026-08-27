package lk.sunrise.clinic.pattern;

import lk.sunrise.clinic.domain.Patient;

import java.time.LocalDate;
import java.util.List;

/**
 * Chooses WHICH DiscountStrategy applies to a patient.
 *
 * Selecting the rule is business policy and belongs in the service tier.
 * Applying the arithmetic to a bill and writing the rows belongs in
 * sp_generate_bill. Keeping that boundary is why the stored procedure
 * receives a discount AMOUNT rather than working the rule out itself.
 *
 * ASM-12: only one discount applies, and the most generous one wins.
 */
public class DiscountPolicy {

    /** Ordered most generous first, so the first match wins. */
    private static final List<String> PRECEDENCE =
            List.of("STAFF_FAMILY", "SENIOR", "LOYALTY");

    public DiscountStrategy resolve(Patient patient, LocalDate on) {
        if (patient == null) {
            return new NoDiscount();
        }
        for (String rule : PRECEDENCE) {
            switch (rule) {
                case "STAFF_FAMILY":
                    if (patient.isStaffFamily())            return new StaffFamilyDiscount();
                    break;
                case "SENIOR":
                    if (patient.isSeniorCitizenOn(on))      return new SeniorCitizenDiscount();
                    break;
                case "LOYALTY":
                    if (patient.qualifiesForLoyalty())      return new LoyaltyDiscount();
                    break;
                default:
                    break;
            }
        }
        return new NoDiscount();
    }
}