package lk.sunrise.clinic.repository;

import lk.sunrise.clinic.domain.TreatmentType;

import java.util.List;
import java.util.Optional;

/**
 * REPOSITORY pattern (architectural).
 *
 * The service tier depends on this interface, never on JDBC. That keeps
 * SQL out of the business logic and lets services be unit-tested with a
 * stub implementation instead of a live database.
 */
public interface TreatmentTypeRepository {

    List<TreatmentType> findAllActive();

    /** FR-21. Administrators see inactive treatments too, so they can restore one. */
    List<TreatmentType> findAll();

    Optional<TreatmentType> findByCode(String code);

    /** FR-21. Returns the code of the treatment created. */
    String insert(String code, String name, java.math.BigDecimal price, int durationMinutes);

    /**
     * FR-21. Updates the standing price, duration and availability.
     *
     * Existing appointments are untouched: appointment_treatment holds a price
     * SNAPSHOT taken at booking time, so a price rise never silently re-prices
     * work already booked. That is the whole reason the snapshot column exists.
     */
    int update(String code, String name, java.math.BigDecimal price,
               int durationMinutes, boolean active);
}
