package lk.sunrise.clinic.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;

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
     * @param other         an existing appointment to compare against
     * @param bufferMinutes turnaround gap required between two appointments
     *                      for the same dentist (clinic_setting.BUFFER_MINUTES)
     */
    public boolean overlapsWith(Appointment other, int bufferMinutes) {
        return false;
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