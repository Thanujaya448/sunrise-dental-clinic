package lk.sunrise.clinic.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

/**
 * A single patient visit.
 *
 * The clash rule lives HERE rather than in AppointmentService because
 * whether two appointments overlap is a property of an appointment, not
 * of the service that happens to be scheduling one. Keeping it on the
 * entity keeps the service thin and makes the rule unit-testable without
 * a Spring context or a database.
 *
 * JPA mapping is added in the next TDD cycle, when the repository test
 * first requires it.
 */
public class Appointment {

    private Long id;
    private String appointmentNo;
    private Long patientId;
    private Long dentistId;
    private LocalDate appointmentDate;
    private LocalTime startTime;
    private LocalTime endTime;
    private AppointmentStatus status = AppointmentStatus.BOOKED;

    protected Appointment() {
        // required by JPA later
    }

    public Appointment(Long dentistId, LocalDate appointmentDate,
                       LocalTime startTime, LocalTime endTime,
                       AppointmentStatus status) {
        this.dentistId = Objects.requireNonNull(dentistId, "dentistId");
        this.appointmentDate = Objects.requireNonNull(appointmentDate, "appointmentDate");
        this.startTime = Objects.requireNonNull(startTime, "startTime");
        this.endTime = Objects.requireNonNull(endTime, "endTime");
        this.status = Objects.requireNonNull(status, "status");

        if (!endTime.isAfter(startTime)) {
            throw new IllegalArgumentException("endTime must be after startTime");
        }
    }

    /**
     * True when this appointment cannot coexist with {@code other}.
     *
     * Mirrors trg_appointment_no_overlap in the database exactly. The rule
     * is deliberately implemented twice: here for fast, clear feedback to
     * the user, and in the trigger because between this check and the
     * INSERT another transaction can commit the same slot (NFR-05).
     *
     * @param other         an existing appointment to compare against
     * @param bufferMinutes turnaround gap required between two appointments
     *                      for the same dentist (clinic_setting.BUFFER_MINUTES)
     */
    public boolean overlapsWith(Appointment other, int bufferMinutes) {
        if (other == null) {
            return false;
        }
        if (!this.dentistId.equals(other.dentistId)) {
            return false;
        }
        if (!this.appointmentDate.equals(other.appointmentDate)) {
            return false;
        }
        if (this.status != AppointmentStatus.BOOKED
                || other.status != AppointmentStatus.BOOKED) {
            return false;
        }
        return this.startTime.isBefore(other.endTime.plusMinutes(bufferMinutes))
            && this.endTime.plusMinutes(bufferMinutes).isAfter(other.startTime);
    }

    public int getDurationMinutes() {
        return (int) java.time.Duration.between(startTime, endTime).toMinutes();
    }

    public boolean isBillable() {
        return status == AppointmentStatus.COMPLETED;
    }

    public Long getId()                       { return id; }
    public String getAppointmentNo()          { return appointmentNo; }
    public Long getPatientId()                { return patientId; }
    public Long getDentistId()                { return dentistId; }
    public LocalDate getAppointmentDate()     { return appointmentDate; }
    public LocalTime getStartTime()           { return startTime; }
    public LocalTime getEndTime()             { return endTime; }
    public AppointmentStatus getStatus()      { return status; }

    public void setPatientId(Long patientId)  { this.patientId = patientId; }
    public void setStatus(AppointmentStatus s){ this.status = s; }
}