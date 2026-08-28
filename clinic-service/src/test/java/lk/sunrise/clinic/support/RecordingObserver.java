package lk.sunrise.clinic.support;

import lk.sunrise.clinic.pattern.AppointmentObserver;

import java.util.ArrayList;
import java.util.List;

/**
 * A test double for the OBSERVER pattern.
 *
 * The production listeners send messages and write audit rows, neither of
 * which a unit test should do. This one only records that it was told, which
 * is exactly what the test needs to assert: AppointmentService publishes the
 * event without knowing or caring who is listening.
 */
public class RecordingObserver implements AppointmentObserver {

    public final List<String> created = new ArrayList<>();
    public final List<String> cancelled = new ArrayList<>();

    @Override
    public void onAppointmentCreated(String appointmentNo, String patientName,
                                     String contactNumber, String dentistName,
                                     String whenText) {
        created.add(appointmentNo + " -> " + patientName + " with " + dentistName + " on " + whenText);
    }

    @Override
    public void onAppointmentCancelled(String appointmentNo, String reason) {
        cancelled.add(appointmentNo + " -> " + reason);
    }
}
