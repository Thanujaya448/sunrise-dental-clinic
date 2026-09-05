package lk.sunrise.clinic.service;

import lk.sunrise.clinic.domain.TreatmentType;
import lk.sunrise.clinic.exception.ClinicExceptions.NotFoundException;
import lk.sunrise.clinic.exception.ClinicExceptions.ValidationException;
import lk.sunrise.clinic.repository.ClinicRepository;
import lk.sunrise.clinic.repository.TreatmentTypeRepository;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * FR-21, FR-22 and the second half of FR-03 - the Administrator's functions.
 *
 * The clinic's stated failure was BILLING ERRORS, and the cause of a billing
 * error in a paper system is usually a stale price. Putting prices in a table
 * only helps if somebody can change them without a developer, so maintenance
 * is a requirement of the system, not an afterthought.
 *
 * Unlocking closes a promise the rest of the system makes: FR-03 locks an
 * account after five failures and tells the user to ask the Administrator.
 * Until this class existed, the Administrator had no way to do it, and the
 * message asked for something impossible.
 *
 * Every rule here is checked in the service, not the controller, so both
 * clients and any future one inherit it (NFR-04).
 */
@Service
public class AdministrationService {

    private static final int MIN_PASSWORD = 8;

    private final TreatmentTypeRepository treatments;
    private final ClinicRepository repository;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdministrationService(TreatmentTypeRepository treatments, ClinicRepository repository) {
        this.treatments = treatments;
        this.repository = repository;
    }

    // =================================================================
    //  FR-21  treatments and prices
    // =================================================================

    public List<TreatmentType> allTreatments() {
        return treatments.findAll();
    }

    public TreatmentType createTreatment(String code, String name,
                                         BigDecimal price, Integer durationMinutes) {
        String c = normaliseCode(code);
        validateTreatment(name, price, durationMinutes);
        if (treatments.findByCode(c).isPresent()) {
            throw new ValidationException("A treatment with code " + c + " already exists");
        }
        treatments.insert(c, name.trim(), price, durationMinutes);
        return treatments.findByCode(c)
                .orElseThrow(() -> new NotFoundException("Treatment could not be read back"));
    }

    /**
     * Changes the STANDING price. Appointments already booked keep the price
     * snapshot taken at booking time, so a rise never silently re-prices work
     * that was quoted at the old rate (ASM-11).
     */
    public TreatmentType updateTreatment(String code, String name, BigDecimal price,
                                         Integer durationMinutes, Boolean active) {
        String c = normaliseCode(code);
        treatments.findByCode(c)
                .orElseThrow(() -> new NotFoundException("No treatment with code " + c));
        validateTreatment(name, price, durationMinutes);
        treatments.update(c, name.trim(), price, durationMinutes,
                          active == null || active);
        return treatments.findByCode(c)
                .orElseThrow(() -> new NotFoundException("Treatment could not be read back"));
    }

    private void validateTreatment(String name, BigDecimal price, Integer durationMinutes) {
        if (name == null || name.isBlank()) {
            throw new ValidationException("A treatment needs a name");
        }
        if (price == null || price.signum() < 0) {
            throw new ValidationException("Price cannot be negative");
        }
        if (durationMinutes == null || durationMinutes < 5 || durationMinutes > 480) {
            throw new ValidationException("Duration must be between 5 and 480 minutes");
        }
    }

    private String normaliseCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ValidationException("A treatment needs a code");
        }
        String c = code.trim().toUpperCase();
        if (c.length() > 6) {
            throw new ValidationException("Treatment codes are at most 6 characters");
        }
        return c;
    }

    // =================================================================
    //  FR-22  staff and dentists
    // =================================================================

    public List<Map<String, Object>> allStaff() {
        return repository.findAllStaff();
    }

    /**
     * Creates a member of staff, and the dentist row too when the role is
     * DENTIST - a dentist is a staff member with a consultation fee, and the
     * two rows are meaningless apart.
     */
    public void createStaff(String username, String rawPassword, String fullName, String role,
                            String registrationNo, String specialisation, BigDecimal fee) {

        if (username == null || username.isBlank()) {
            throw new ValidationException("A username is required");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new ValidationException("A full name is required");
        }
        if (rawPassword == null || rawPassword.length() < MIN_PASSWORD) {
            throw new ValidationException(
                    "The password must be at least " + MIN_PASSWORD + " characters");
        }
        if (!List.of("RECEPTIONIST", "DENTIST", "ADMINISTRATOR").contains(String.valueOf(role))) {
            throw new ValidationException("Role must be RECEPTIONIST, DENTIST or ADMINISTRATOR");
        }
        if (repository.findStaffByUsername(username).isPresent()) {
            throw new ValidationException("That username is already taken");
        }
        if ("DENTIST".equals(role)) {
            if (registrationNo == null || registrationNo.isBlank()) {
                throw new ValidationException("A dentist needs an SLMC registration number");
            }
            if (fee == null || fee.signum() < 0) {
                throw new ValidationException("A dentist needs a consultation fee");
            }
        }

        // The password is hashed here and never stored or logged in clear (FR-02).
        long staffId = repository.insertStaff(
                username.trim(), encoder.encode(rawPassword), fullName.trim(), role);

        if ("DENTIST".equals(role)) {
            repository.insertDentist(staffId, registrationNo.trim(),
                    specialisation == null || specialisation.isBlank()
                            ? "General Dentistry" : specialisation.trim(),
                    fee);
        }
        repository.writeAudit("ADMIN", "STAFF_CREATED", username, "Role " + role);
    }

    // =================================================================
    //  FR-03  the other half of the lockout rule
    // =================================================================

    public void unlockAccount(String username) {
        if (repository.unlockStaff(username) == 0) {
            throw new NotFoundException("No staff account called " + username);
        }
        repository.writeAudit("ADMIN", "ACCOUNT_UNLOCKED", username, "Lock cleared");
    }

    public void setActive(String username, boolean active) {
        if (repository.setStaffActive(username, active) == 0) {
            throw new NotFoundException("No staff account called " + username);
        }
        repository.writeAudit("ADMIN", active ? "STAFF_REACTIVATED" : "STAFF_DEACTIVATED",
                username, "");
    }
}
