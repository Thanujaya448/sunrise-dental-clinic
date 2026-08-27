package lk.sunrise.clinic.pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * FR-20 - appointment confirmations and cancellation notices.
 *
 * STUB. This class logs the message it WOULD have sent. No SMS gateway or
 * mail server is wired up, and the report says so explicitly rather than
 * implying a working integration. Substituting a real gateway means changing
 * this one class and nothing else - which is the point of the Observer.
 */
@Component
public class NotificationObserver implements AppointmentObserver {

    private static final Logger log = LoggerFactory.getLogger(NotificationObserver.class);

    @Override
    public void onAppointmentCreated(String appointmentNo, String patientName,
                                     String contactNumber, String dentistName,
                                     String whenText) {
        log.info("[SMS STUB -> {}] Dear {}, your appointment {} with {} is confirmed for {}. "
                       + "Sunrise Dental Clinic.",
                contactNumber, patientName, appointmentNo, dentistName, whenText);
    }

    @Override
    public void onAppointmentCancelled(String appointmentNo, String reason) {
        log.info("[SMS STUB] Appointment {} has been cancelled. Reason: {}", appointmentNo, reason);
    }
}
