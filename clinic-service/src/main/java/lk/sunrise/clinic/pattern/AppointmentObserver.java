package lk.sunrise.clinic.pattern;

/**
 * OBSERVER pattern (behavioural).
 *
 * AppointmentService publishes what happened and returns. It has no knowledge
 * of notifications or auditing, so a new listener can be added without
 * touching the booking logic at all.
 */
public interface AppointmentObserver {

    void onAppointmentCreated(String appointmentNo, String patientName,
                              String contactNumber, String dentistName,
                              String whenText);

    void onAppointmentCancelled(String appointmentNo, String reason);
}
