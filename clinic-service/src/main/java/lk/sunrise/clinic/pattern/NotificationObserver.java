package lk.sunrise.clinic.pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * FR-20 - appointment confirmations and cancellation notices, sent by email.
 *
 * THREE DECISIONS ARE WORTH DEFENDING HERE.
 *
 * 1. It is @Async. A mail server that is slow, or briefly unreachable, must
 *    not make the receptionist wait: the booking has already been committed
 *    by the time this runs, so the confirmation is sent on a separate thread
 *    and the HTTP response returns immediately.
 *
 * 2. Every failure is swallowed and logged, never rethrown. A confirmation
 *    that could not be delivered is a nuisance; an exception that rolled back
 *    a committed appointment would be a defect. Notification is not part of
 *    the booking transaction, and this class is written so that it cannot
 *    become part of it by accident.
 *
 * 3. Sending is switched by configuration, not by code. With
 *    clinic.notifications.email.enabled=false the message is logged rather
 *    than sent, which is what the automated tests and the CI pipeline use.
 *    The same class serves both, so the tested path is the shipped path.
 *
 * AppointmentService knows none of this. Adding an SMS gateway alongside it
 * means adding a class that implements AppointmentObserver and changing
 * nothing else - which is the point of the Observer pattern.
 */
@Component
public class NotificationObserver implements AppointmentObserver {

    private static final Logger log = LoggerFactory.getLogger(NotificationObserver.class);

    private final JavaMailSender mailSender;
    private final boolean enabled;
    private final String from;
    private final String clinicName;

    public NotificationObserver(JavaMailSender mailSender,
                                @Value("${clinic.notifications.email.enabled:false}") boolean enabled,
                                @Value("${clinic.notifications.email.from:noreply@sunrisedental.lk}") String from,
                                @Value("${clinic.name:Sunrise Dental Clinic}") String clinicName) {
        this.mailSender = mailSender;
        this.enabled = enabled;
        this.from = from;
        this.clinicName = clinicName;
    }

    @Async
    @Override
    public void onAppointmentCreated(String appointmentNo, String patientName,
                                     String contactNumber, String patientEmail,
                                     String dentistName, String whenText) {

        String subject = "Appointment confirmed - " + appointmentNo;
        String body = """
                Dear %s,

                Your appointment at %s is confirmed.

                    Appointment number : %s
                    Dentist            : %s
                    Date and time      : %s

                Please arrive ten minutes early. To change or cancel this
                appointment, quote the appointment number above.

                %s
                """.formatted(patientName, clinicName, appointmentNo,
                              dentistName, whenText, clinicName);

        send(patientEmail, subject, body, appointmentNo);
    }

    @Async
    @Override
    public void onAppointmentCancelled(String appointmentNo, String reason) {
        // The cancellation path does not carry the patient's address, so this
        // event is recorded rather than emailed. Widening the contract for it
        // is noted as future work in the report.
        log.info("Appointment {} cancelled. Reason: {}", appointmentNo, reason);
    }

    private void send(String to, String subject, String body, String appointmentNo) {
        if (to == null || to.isBlank()) {
            log.info("No email address held for appointment {} - confirmation not sent", appointmentNo);
            return;
        }
        if (!enabled) {
            log.info("Email disabled. Would have sent to {}: {}", to, subject);
            return;
        }
        try {
            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(from);
            message.setTo(to);
            message.setSubject(subject);
            message.setText(body);
            mailSender.send(message);
            log.info("Confirmation email sent to {} for appointment {}", to, appointmentNo);
        } catch (Exception ex) {
            // Deliberately broad: no delivery problem may ever surface as a
            // failed booking. The appointment is already committed.
            log.warn("Could not send confirmation for appointment {}: {}",
                    appointmentNo, ex.getMessage());
        }
    }
}
