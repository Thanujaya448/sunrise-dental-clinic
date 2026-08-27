package lk.sunrise.clinic.domain;

/**
 * The appointment lifecycle (ASM-05).
 *
 * The brief never mentions cancellation, but without a lifecycle there is
 * no defined moment at which a bill becomes valid, and a clinic that
 * cannot cancel fills its diary with dead slots.
 */
public enum AppointmentStatus {
    BOOKED,
    COMPLETED,
    CANCELLED,
    NO_SHOW
}
