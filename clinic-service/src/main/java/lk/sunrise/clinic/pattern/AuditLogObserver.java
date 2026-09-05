package lk.sunrise.clinic.pattern;

import lk.sunrise.clinic.repository.ClinicRepository;
import org.springframework.stereotype.Component;

/**
 * FR-24 - who changed what, and when.
 *
 * Records the appointment reference only. No patient name, address or contact
 * number is written to the audit table, because an audit log is read by more
 * people than the records it describes (NFR-06).
 */
@Component
public class AuditLogObserver implements AppointmentObserver {

    private final ClinicRepository repository;

    public AuditLogObserver(ClinicRepository repository) {
        this.repository = repository;
    }

    @Override
    public void onAppointmentCreated(String appointmentNo, String patientName,
                                     String contactNumber, String patientEmail,
                                     String dentistName, String whenText) {
        repository.writeAudit("SYSTEM", "APPOINTMENT_CREATED", appointmentNo,
                "Booked with " + dentistName + " for " + whenText);
    }

    @Override
    public void onAppointmentCancelled(String appointmentNo, String reason) {
        repository.writeAudit("SYSTEM", "APPOINTMENT_CANCELLED", appointmentNo, reason);
    }
}
