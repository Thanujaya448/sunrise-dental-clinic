package lk.sunrise.clinic.pattern;

/** 15% for clinic staff and their immediate family (ASM-12). */
public class StaffFamilyDiscount extends PercentageDiscount {
    public StaffFamilyDiscount() {
        super("0.15", "Staff and family 15%");
    }
}