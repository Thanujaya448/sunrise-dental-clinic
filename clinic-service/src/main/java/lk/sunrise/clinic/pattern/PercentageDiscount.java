package lk.sunrise.clinic.pattern;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Shared behaviour for every percentage-based rule.
 *
 * Rounding is HALF_UP to two decimal places, matching DECIMAL(10,2) in the
 * database. Fixing this in one place means the three concrete rules cannot
 * drift apart, and the bill total always reconciles with what MySQL stores.
 */
abstract class PercentageDiscount implements DiscountStrategy {

    private final BigDecimal rate;
    private final String label;

    protected PercentageDiscount(String rate, String label) {
        this.rate = new BigDecimal(rate);
        this.label = label;
    }

    @Override
    public BigDecimal calculate(BigDecimal treatmentSubtotal) {
        if (treatmentSubtotal == null || treatmentSubtotal.signum() <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return treatmentSubtotal.multiply(rate).setScale(2, RoundingMode.HALF_UP);
    }

    @Override
    public String getLabel() {
        return label;
    }
}