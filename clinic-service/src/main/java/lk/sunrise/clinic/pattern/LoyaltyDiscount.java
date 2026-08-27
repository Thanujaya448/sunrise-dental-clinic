package lk.sunrise.clinic.pattern;

/** 5% from a patient's fifth completed visit onwards (ASM-12). */
public class LoyaltyDiscount extends PercentageDiscount {
    public LoyaltyDiscount() {
        super("0.05", "Returning patient 5%");
    }
}