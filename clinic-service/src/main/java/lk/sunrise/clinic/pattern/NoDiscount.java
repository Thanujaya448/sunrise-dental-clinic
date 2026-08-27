package lk.sunrise.clinic.pattern;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * NULL OBJECT variant of the Strategy: the "no rule applies" case is itself
 * a strategy, so BillingFacade never has to test for null.
 */
public class NoDiscount implements DiscountStrategy {

    @Override
    public BigDecimal calculate(BigDecimal treatmentSubtotal) {
        return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getLabel() {
        return "No discount";
    }
}