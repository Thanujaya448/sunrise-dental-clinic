package lk.sunrise.clinic.pattern;

/** 10% for patients aged 65 or over (ASM-12). */
public class SeniorCitizenDiscount extends PercentageDiscount {
    public SeniorCitizenDiscount() {
        super("0.10", "Senior citizen 10%");
    }
}