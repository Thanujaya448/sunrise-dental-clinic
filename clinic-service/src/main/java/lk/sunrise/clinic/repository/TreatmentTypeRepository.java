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

    Optional<TreatmentType> findByCode(String code);
}
