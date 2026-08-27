package lk.sunrise.clinic.pattern;

import java.math.BigDecimal;

/**
 * STRATEGY pattern (behavioural).
 *
 * The clinic's discount rules vary independently of how a bill is
 * assembled. Encapsulating each rule behind this interface means adding a
 * new one is adding a class, never editing BillingFacade - the open/closed
 * principle in practice.
 *
 * The alternative, a chain of if/else inside the billing code, was
 * rejected: every new rule would modify a method that is already tested
 * and working, and each modification risks the rules that already pass.
 *
 * ASM-12. The discount applies to the treatment subtotal only, never to
 * the dentist's consultation fee.
 */
public interface DiscountStrategy {

    /** Amount to deduct from the treatment subtotal, rounded to 2 dp. */
    BigDecimal calculate(BigDecimal treatmentSubtotal);

    /** Human-readable label printed on the bill, e.g. "Senior citizen 10%". */
    String getLabel();
}