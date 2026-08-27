package lk.sunrise.clinic.domain;

import java.math.BigDecimal;

/**
 * A treatment the clinic offers.
 *
 * Price and duration live in the database, not in Java (ASM-11, ASM-07).
 * Billing errors were a stated failure of the manual system, and a
 * hard-coded price means a correction requires a redeployment - so in
 * practice the wrong price stays in use.
 */
public class TreatmentType {

    private Long id;
    private String code;
    private String name;
    private BigDecimal price;
    private int durationMinutes;
    private boolean active;

    public TreatmentType(Long id, String code, String name,
                         BigDecimal price, int durationMinutes, boolean active) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.price = price;
        this.durationMinutes = durationMinutes;
        this.active = active;
    }

    public Long getId()              { return id; }
    public String getCode()          { return code; }
    public String getName()          { return name; }
    public BigDecimal getPrice()     { return price; }
    public int getDurationMinutes()  { return durationMinutes; }
    public boolean isActive()        { return active; }
}
