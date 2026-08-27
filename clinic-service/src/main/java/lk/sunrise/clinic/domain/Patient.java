package lk.sunrise.clinic.domain;

import java.time.LocalDate;
import java.time.Period;

/**
 * A patient, persistent across visits (ASM-03).
 *
 * The brief says NEW patients must be registered, which only makes sense
 * if returning patients are looked up. Storing the patient once and
 * referencing it from each appointment is what prevents the "lost patient
 * records" failure the scenario describes.
 */
public class Patient {

    private Long id;
    private String patientNo;
    private String fullName;
    private String address;
    private String contactNumber;
    private String email;
    private LocalDate dateOfBirth;
    private boolean staffFamily;
    private int previousCompletedVisits;

    protected Patient() {
        // required by JPA later
    }

    public Patient(String fullName, LocalDate dateOfBirth,
                   boolean staffFamily, int previousCompletedVisits) {
        this.fullName = fullName;
        this.dateOfBirth = dateOfBirth;
        this.staffFamily = staffFamily;
        this.previousCompletedVisits = previousCompletedVisits;
    }

    /** Age in whole years on the given date. */
    public int getAgeOn(LocalDate on) {
        return Period.between(dateOfBirth, on).getYears();
    }

    /** ASM-12: 65 or over qualifies. */
    public boolean isSeniorCitizenOn(LocalDate on) {
        return getAgeOn(on) >= 65;
    }

    /** ASM-12: the fifth visit onwards, i.e. four or more previous ones. */
    public boolean qualifiesForLoyalty() {
        return previousCompletedVisits >= 4;
    }

    public boolean isStaffFamily()            { return staffFamily; }
    public Long getId()                       { return id; }
    public String getPatientNo()              { return patientNo; }
    public String getFullName()               { return fullName; }
    public String getAddress()                { return address; }
    public String getContactNumber()          { return contactNumber; }
    public String getEmail()                  { return email; }
    public LocalDate getDateOfBirth()         { return dateOfBirth; }
    public int getPreviousCompletedVisits()   { return previousCompletedVisits; }
}