package lk.sunrise.clinic.service;

import lk.sunrise.clinic.dto.Dtos;
import lk.sunrise.clinic.exception.ClinicExceptions.*;
import lk.sunrise.clinic.pattern.AppointmentObserver;
import lk.sunrise.clinic.repository.ClinicRepository;
import lk.sunrise.clinic.repository.Sql;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The heart of tier 2. FR-07 to FR-16.
 *
 * The clash rule is checked here AND enforced by trg_appointment_no_overlap.
 * That duplication is deliberate: this check gives the receptionist an
 * immediate, helpful message with alternatives, while the trigger closes the
 * window between this check and the INSERT in which another transaction could
 * take the same slot (NFR-05). The report evaluates the trade-off.
 */
@Service
public class AppointmentService {

    private final ClinicRepository repository;
    private final List<AppointmentObserver> observers;

    /** Spring injects every AppointmentObserver bean - the Observer registry. */
    public AppointmentService(ClinicRepository repository, List<AppointmentObserver> observers) {
        this.repository = repository;
        this.observers = observers;
    }

    // =================================================================
    //  UC-07 Book Appointment
    // =================================================================
    public Dtos.AppointmentDTO book(Dtos.BookingRequest req, long createdByStaffId) {

        // ---- validation (NFR-04) ------------------------------------
        if (req.patientNo() == null || req.patientNo().isBlank()) {
            throw new ValidationException("Select a patient before booking");
        }
        if (req.dentistId() == null) {
            throw new ValidationException("Select a dentist");
        }
        if (req.treatmentCode() == null || req.treatmentCode().isBlank()) {
            throw new ValidationException("Select a treatment");
        }
        if (req.appointmentDate() == null || req.startTime() == null) {
            throw new ValidationException("Appointment date and time are required");
        }
        Dtos.PatientDTO patient = repository.findPatientByNo(req.patientNo())
                .orElseThrow(() -> new NotFoundException(
                        "No patient with number " + req.patientNo()));

        Map<String, Object> treatment = repository.findTreatmentByCode(req.treatmentCode())
                .orElseThrow(() -> new NotFoundException(
                        "No active treatment with code " + req.treatmentCode()));

        int duration = Sql.asInt(treatment.get("duration_minutes"));
        LocalTime end = req.startTime().plusMinutes(duration);

        assertWithinOpeningHours(req.appointmentDate(), req.startTime(), end);

        // ---- FR-10 clash check, with FR-11 alternatives -------------
        int buffer = Integer.parseInt(repository.readSetting("BUFFER_MINUTES"));
        List<Map<String, Object>> sameDay =
                repository.findDayBookings(req.dentistId(), req.appointmentDate());

        if (clashes(sameDay, req.startTime(), end, buffer)) {
            List<LocalTime> alternatives = nextFreeSlots(sameDay, duration, buffer, 3);
            throw new SlotUnavailableException(
                    "That dentist is already booked at " + req.startTime()
                            + ". Next free slots: " + describe(alternatives),
                    alternatives);
        }

        String appointmentNo = repository.insertAppointment(
                req.patientNo(), req.dentistId(), req.appointmentDate(),
                req.startTime(), end, req.notes(), createdByStaffId,
                Sql.asLong(treatment.get("treatment_id")),
                (BigDecimal) treatment.get("price"));

        Dtos.AppointmentDTO saved = repository.findAppointmentByNo(appointmentNo)
                .orElseThrow(() -> new NotFoundException("Appointment could not be read back"));

        // ---- Observer: the service does not know who is listening ----
        String whenText = saved.appointmentDate() + " at " + saved.startTime();
        observers.forEach(o -> o.onAppointmentCreated(
                saved.appointmentNo(), saved.patientName(), saved.contactNumber(),
                patient.email(), saved.dentistName(), whenText));

        return saved;
    }

    // =================================================================
    //  UC-10 Search, UC-11 Cancel, UC-13 Complete
    // =================================================================

    public Dtos.AppointmentDTO findByNumber(String appointmentNo) {
        return repository.findAppointmentByNo(appointmentNo)
                .orElseThrow(() -> new NotFoundException(
                        "No appointment with number " + appointmentNo));
    }

    public List<Dtos.AppointmentDTO> findOn(LocalDate date) {
        return repository.findAppointmentsOn(date);
    }

    public void cancel(String appointmentNo, String reason) {
        Dtos.AppointmentDTO appt = findByNumber(appointmentNo);
        if (!"BOOKED".equals(appt.status())) {
            throw new ValidationException(
                    "Only a booked appointment can be cancelled. This one is " + appt.status() + ".");
        }
        if (reason == null || reason.isBlank()) {
            throw new ValidationException("A cancellation reason is required");
        }
        repository.updateStatus(appointmentNo, "CANCELLED", reason);
        observers.forEach(o -> o.onAppointmentCancelled(appointmentNo, reason));
    }

    public void markCompleted(String appointmentNo) {
        Dtos.AppointmentDTO appt = findByNumber(appointmentNo);
        if (!"BOOKED".equals(appt.status())) {
            throw new ValidationException(
                    "Only a booked appointment can be completed. This one is " + appt.status() + ".");
        }
        repository.updateStatus(appointmentNo, "COMPLETED", null);
    }

    public void markNoShow(String appointmentNo) {
        findByNumber(appointmentNo);
        repository.updateStatus(appointmentNo, "NO_SHOW", null);
    }

    // =================================================================
    //  Scheduling helpers - the same arithmetic as the database trigger
    // =================================================================

    private void assertWithinOpeningHours(LocalDate date, LocalTime start, LocalTime end) {
        LocalTime opening = LocalTime.parse(repository.readSetting("OPENING_TIME"));
        LocalTime closing = LocalTime.parse(repository.readSetting("CLOSING_TIME"));
        int closedDay = Integer.parseInt(repository.readSetting("CLOSED_WEEKDAY"));
        int grain = Integer.parseInt(repository.readSetting("SLOT_GRANULARITY"));

        if (date.isBefore(LocalDate.now())) {
            throw new ValidationException("Appointments cannot be booked in the past");
        }
        if (date.getDayOfWeek() == DayOfWeek.of(closedDay)) {
            throw new ValidationException("The clinic is closed on "
                    + date.getDayOfWeek().name().charAt(0)
                    + date.getDayOfWeek().name().substring(1).toLowerCase() + "s");
        }
        if (start.isBefore(opening) || end.isAfter(closing)) {
            throw new ValidationException("Appointments must fall between "
                    + opening + " and " + closing);
        }
        if (start.getMinute() % grain != 0) {
            throw new ValidationException("Appointments start every " + grain + " minutes");
        }
    }

    /** Ranges widened by the buffer intersect - identical to the trigger. */
    private boolean clashes(List<Map<String, Object>> existing,
                            LocalTime start, LocalTime end, int buffer) {
        for (Map<String, Object> row : existing) {
            LocalTime bs = Sql.asLocalTime(row.get("start_time"));
            LocalTime be = Sql.asLocalTime(row.get("end_time"));
            if (start.isBefore(be.plusMinutes(buffer)) && end.plusMinutes(buffer).isAfter(bs)) {
                return true;
            }
        }
        return false;
    }

    /** FR-11 / ASM-09 - refusing solves double booking; offering solves waiting. */
    private List<LocalTime> nextFreeSlots(List<Map<String, Object>> existing,
                                          int duration, int buffer, int wanted) {
        LocalTime opening = LocalTime.parse(repository.readSetting("OPENING_TIME"));
        LocalTime closing = LocalTime.parse(repository.readSetting("CLOSING_TIME"));
        int grain = Integer.parseInt(repository.readSetting("SLOT_GRANULARITY"));

        List<LocalTime> free = new ArrayList<>();
        for (LocalTime t = opening; !t.plusMinutes(duration).isAfter(closing); t = t.plusMinutes(grain)) {
            if (!clashes(existing, t, t.plusMinutes(duration), buffer)) {
                free.add(t);
                if (free.size() == wanted) {
                    break;
                }
            }
        }
        return free;
    }

    private String describe(List<LocalTime> slots) {
        return slots.isEmpty() ? "none left today" : String.join(", ", slots.stream().map(LocalTime::toString).toList());
    }
}
