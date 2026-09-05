package lk.sunrise.clinic.support;

import lk.sunrise.clinic.dto.Dtos;
import lk.sunrise.clinic.repository.ClinicRepository;
import org.springframework.dao.DataAccessException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * A hand-written TEST DOUBLE for {@link ClinicRepository}.
 *
 * WHY A HAND-WRITTEN DOUBLE RATHER THAN MOCKITO
 * ---------------------------------------------
 * A mock built with a framework verifies that a method was CALLED. This
 * double holds state, so a test can assert on the CONSEQUENCE instead - that
 * the appointment now exists, that the status really changed, that the audit
 * trail records it. Behaviour verification is coupled to how the service is
 * written; state verification is coupled only to what it achieves, so these
 * tests survive refactoring of the service.
 *
 * It also documents the repository contract in one readable file, which a
 * chain of when(...).thenReturn(...) lines does not.
 *
 * This class is the proof that the tiers really are separated: the entire
 * service layer runs here with no MySQL, no JDBC driver and no Spring
 * context. If any business rule had leaked into a SQL string, these tests
 * could not exist.
 *
 * It is a double, not a simulator: it does NOT re-implement the overlap
 * trigger or sp_generate_bill. Those are data-tier rules and are proved by
 * 08_healthcheck.sql against the real database. Re-implementing them here
 * would only test the copy.
 */
public class InMemoryClinicRepository implements ClinicRepository {

    // ---- stored state -------------------------------------------------
    private final Map<String, String> settings = new HashMap<>();
    private final Map<String, Map<String, Object>> staffByUsername = new HashMap<>();
    private final Map<String, Dtos.PatientDTO> patients = new LinkedHashMap<>();
    private final Map<String, Map<String, Object>> patientDiscountInputs = new HashMap<>();
    private final Map<String, Map<String, Object>> treatmentsByCode = new HashMap<>();
    private final Map<String, List<Map<String, Object>>> dayBookings = new HashMap<>();
    private final Map<String, Dtos.AppointmentDTO> appointments = new LinkedHashMap<>();
    private final Map<String, Dtos.BillDTO> billsByNo = new LinkedHashMap<>();
    private final Map<String, String> billNoByAppointment = new HashMap<>();
    private final List<Dtos.DentistDTO> dentists = new ArrayList<>();

    // ---- recorded interactions (what a spy would give us) --------------
    public final List<String> auditTrail = new ArrayList<>();
    public final Map<Long, Integer> loginFailures = new HashMap<>();
    public final List<Long> failuresReset = new ArrayList<>();
    public BigDecimal lastDiscountPassedToProcedure;
    public String lastDiscountLabelPassedToProcedure;

    /** When set, callGenerateBill throws it - simulates the stored procedure signalling. */
    public DataAccessException nextBillFailure;

    private int appointmentCounter = 0;
    private int billCounter = 0;

    // =====================================================================
    //  Fixture builders - readable set-up for each test
    // =====================================================================

    /** The clinic_setting rows exactly as 04_seed_data.sql inserts them. */
    public static InMemoryClinicRepository withClinicDefaults() {
        InMemoryClinicRepository r = new InMemoryClinicRepository();
        r.settings.put("BUFFER_MINUTES", "10");
        r.settings.put("OPENING_TIME", "08:00");
        r.settings.put("CLOSING_TIME", "20:00");
        r.settings.put("CLOSED_WEEKDAY", "7");     // Sunday
        r.settings.put("SLOT_GRANULARITY", "15");
        r.settings.put("MAX_LOGIN_FAILS", "5");
        r.settings.put("SESSION_MINUTES", "20");
        return r;
    }

    public InMemoryClinicRepository setting(String key, String value) {
        settings.put(key, value);
        return this;
    }

    public InMemoryClinicRepository withPatient(String patientNo, String name) {
        patients.put(patientNo, new Dtos.PatientDTO(
                patientNo, name, "1 Galle Road, Colombo", "0771234567",
                name.toLowerCase().replace(' ', '.') + "@example.lk",
                LocalDate.of(1990, 1, 1), 0));
        return this;
    }

    /** date_of_birth / is_staff_family / completed_visits - the discount inputs. */
    public InMemoryClinicRepository withDiscountInputs(String patientNo, LocalDate dob,
                                                       boolean staffFamily, int completedVisits) {
        Map<String, Object> row = new HashMap<>();
        row.put("date_of_birth", dob);
        row.put("is_staff_family", staffFamily);
        row.put("completed_visits", completedVisits);
        patientDiscountInputs.put(patientNo, row);
        return this;
    }

    public InMemoryClinicRepository withTreatment(String code, long id,
                                                  int durationMinutes, String price) {
        Map<String, Object> row = new HashMap<>();
        row.put("treatment_id", id);
        row.put("code", code);
        row.put("duration_minutes", durationMinutes);
        row.put("price", new BigDecimal(price));
        treatmentsByCode.put(code, row);
        return this;
    }

    public InMemoryClinicRepository withDentist(long id, String name) {
        dentists.add(new Dtos.DentistDTO(id, name, "General Dentistry", new BigDecimal("1500.00")));
        return this;
    }

    /** An appointment already on the diary - what the clash check reads. */
    public InMemoryClinicRepository withExistingBooking(long dentistId, LocalDate date,
                                                        String start, String end) {
        Map<String, Object> row = new HashMap<>();
        row.put("start_time", LocalTime.parse(start));
        row.put("end_time", LocalTime.parse(end));
        row.put("status", "BOOKED");
        dayBookings.computeIfAbsent(key(dentistId, date), k -> new ArrayList<>()).add(row);
        return this;
    }

    /** Puts a ready-made appointment in the diary, for billing and status tests. */
    public InMemoryClinicRepository withAppointment(String appointmentNo, String patientNo,
                                                    String status, String treatmentSubtotal) {
        appointments.put(appointmentNo, new Dtos.AppointmentDTO(
                appointmentNo, patientNo, "Test Patient", "0771234567",
                "Dr. Perera", "General Dentistry",
                LocalDate.now(), LocalTime.of(10, 0), LocalTime.of(10, 30),
                status, "SCA - Scaling", new BigDecimal(treatmentSubtotal),
                new BigDecimal("1500.00"), null));
        return this;
    }

    /** A staff row shaped exactly as JdbcClinicRepository returns it. */
    public InMemoryClinicRepository withStaff(long staffId, String username, String hash,
                                              String role, boolean active, boolean locked,
                                              int failedAttempts) {
        Map<String, Object> row = new HashMap<>();
        row.put("staff_id", staffId);
        row.put("username", username);
        row.put("password_hash", hash);
        row.put("full_name", "Staff " + username);
        row.put("role", role);
        row.put("active", active);
        row.put("locked", locked);
        row.put("failed_attempts", failedAttempts);
        staffByUsername.put(username, row);
        return this;
    }

    private String key(long dentistId, LocalDate date) {
        return dentistId + "|" + date;
    }

    // =====================================================================
    //  ClinicRepository implementation
    // =====================================================================

    @Override
    public Optional<Map<String, Object>> findStaffByUsername(String username) {
        return Optional.ofNullable(staffByUsername.get(username));
    }

    @Override
    public void recordLoginFailure(long staffId) {
        loginFailures.merge(staffId, 1, Integer::sum);
    }

    @Override
    public void resetLoginFailures(long staffId) {
        failuresReset.add(staffId);
        loginFailures.remove(staffId);
    }

    // ---- staff administration (FR-22, FR-03) --------------------------

    @Override
    public List<Map<String, Object>> findAllStaff() {
        return List.copyOf(staffByUsername.values());
    }

    @Override
    public long insertStaff(String username, String passwordHash, String fullName, String role) {
        long id = 100L + staffByUsername.size();
        withStaff(id, username, passwordHash, role, true, false, 0);
        staffByUsername.get(username).put("full_name", fullName);
        return id;
    }

    @Override
    public void insertDentist(long staffId, String registrationNo, String specialisation,
                              BigDecimal consultationFee) {
        dentists.add(new Dtos.DentistDTO(staffId, "Dentist " + staffId,
                specialisation, consultationFee));
    }

    @Override
    public int unlockStaff(String username) {
        Map<String, Object> row = staffByUsername.get(username);
        if (row == null) {
            return 0;
        }
        row.put("locked", false);
        row.put("failed_attempts", 0);
        return 1;
    }

    @Override
    public int setStaffActive(String username, boolean active) {
        Map<String, Object> row = staffByUsername.get(username);
        if (row == null) {
            return 0;
        }
        row.put("active", active);
        return 1;
    }

    @Override
    public List<Dtos.DentistDTO> findActiveDentists() {
        return List.copyOf(dentists);
    }

    @Override
    public Optional<Map<String, Object>> findTreatmentByCode(String code) {
        return Optional.ofNullable(treatmentsByCode.get(code));
    }

    @Override
    public String readSetting(String key) {
        String v = settings.get(key);
        if (v == null) {
            throw new IllegalStateException(
                    "Test set-up did not provide clinic setting '" + key + "'");
        }
        return v;
    }

    @Override
    public Optional<Dtos.PatientDTO> findPatientByNo(String patientNo) {
        return Optional.ofNullable(patients.get(patientNo));
    }

    @Override
    public List<Dtos.PatientDTO> searchPatients(String term) {
        String t = term == null ? "" : term.toLowerCase();
        return patients.values().stream()
                .filter(p -> p.fullName().toLowerCase().contains(t)
                        || p.patientNo().toLowerCase().contains(t))
                .toList();
    }

    @Override
    public String insertPatient(Dtos.RegisterPatientRequest req) {
        String no = String.format("PAT-%d-%06d", LocalDate.now().getYear(), patients.size() + 1);
        patients.put(no, new Dtos.PatientDTO(no, req.fullName(), req.address(),
                req.contactNumber(), req.email(), req.dateOfBirth(), 0));
        return no;
    }

    @Override
    public Map<String, Object> patientDiscountInputs(String patientNo) {
        Map<String, Object> row = patientDiscountInputs.get(patientNo);
        if (row == null) {
            throw new IllegalStateException(
                    "Test set-up did not provide discount inputs for " + patientNo);
        }
        return row;
    }

    @Override
    public List<Map<String, Object>> findDayBookings(long dentistId, LocalDate date) {
        return dayBookings.getOrDefault(key(dentistId, date), List.of());
    }

    @Override
    public String insertAppointment(String patientNo, long dentistId, LocalDate date,
                                    LocalTime start, LocalTime end, String notes,
                                    long createdBy, long treatmentId, BigDecimal unitPrice) {
        appointmentCounter++;
        String no = String.format("APT-%d-%06d", date.getYear(), appointmentCounter);

        Dtos.PatientDTO p = patients.get(patientNo);
        appointments.put(no, new Dtos.AppointmentDTO(
                no, patientNo,
                p == null ? "Unknown" : p.fullName(),
                p == null ? "" : p.contactNumber(),
                "Dr. Perera", "General Dentistry",
                date, start, end, "BOOKED", "Treatment " + treatmentId,
                unitPrice, new BigDecimal("1500.00"), notes));

        // The real INSERT makes the slot unavailable to the next booking, so
        // the double must do the same or a second identical booking would pass.
        Map<String, Object> row = new HashMap<>();
        row.put("start_time", start);
        row.put("end_time", end);
        row.put("status", "BOOKED");
        dayBookings.computeIfAbsent(key(dentistId, date), k -> new ArrayList<>()).add(row);

        return no;
    }

    @Override
    public Optional<Dtos.AppointmentDTO> findAppointmentByNo(String appointmentNo) {
        return Optional.ofNullable(appointments.get(appointmentNo));
    }

    @Override
    public List<Dtos.AppointmentDTO> findAppointmentsOn(LocalDate date) {
        return appointments.values().stream()
                .filter(a -> date.equals(a.appointmentDate()))
                .toList();
    }

    @Override
    public int updateStatus(String appointmentNo, String status, String reason) {
        Dtos.AppointmentDTO a = appointments.get(appointmentNo);
        if (a == null) {
            return 0;
        }
        appointments.put(appointmentNo, new Dtos.AppointmentDTO(
                a.appointmentNo(), a.patientNo(), a.patientName(), a.contactNumber(),
                a.dentistName(), a.specialisation(), a.appointmentDate(),
                a.startTime(), a.endTime(), status, a.treatments(),
                a.treatmentSubtotal(), a.consultationFee(),
                reason == null ? a.notes() : reason));
        return 1;
    }

    @Override
    public String callGenerateBill(String appointmentNo, BigDecimal discount, String label) {
        lastDiscountPassedToProcedure = discount;
        lastDiscountLabelPassedToProcedure = label;

        if (nextBillFailure != null) {
            DataAccessException toThrow = nextBillFailure;
            nextBillFailure = null;
            throw toThrow;
        }

        Dtos.AppointmentDTO a = appointments.get(appointmentNo);
        billCounter++;
        String billNo = String.format("BIL-%d-%06d", LocalDate.now().getYear(), billCounter);

        BigDecimal consult = a.consultationFee();
        BigDecimal subtotal = a.treatmentSubtotal() == null ? BigDecimal.ZERO : a.treatmentSubtotal();
        BigDecimal total = consult.add(subtotal).subtract(discount);

        billsByNo.put(billNo, new Dtos.BillDTO(
                billNo, appointmentNo, a.patientName(), LocalDateTime.now(),
                consult, subtotal, discount, label, total, "UNPAID",
                List.of(new Dtos.BillLineDTO(1, "Consultation - " + a.dentistName(),
                        consult, 1, consult))));
        billNoByAppointment.put(appointmentNo, billNo);
        return billNo;
    }

    @Override
    public Optional<Dtos.BillDTO> findBillByNo(String billNo) {
        return Optional.ofNullable(billsByNo.get(billNo));
    }

    @Override
    public Optional<Dtos.BillDTO> findBillByAppointment(String appointmentNo) {
        String no = billNoByAppointment.get(appointmentNo);
        return no == null ? Optional.empty() : Optional.ofNullable(billsByNo.get(no));
    }

    @Override
    public List<Map<String, Object>> runReportView(String viewName) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("view", viewName);
        return List.of(row);
    }

    @Override
    public void writeAudit(String performedBy, String action, String entityRef, String detail) {
        auditTrail.add(performedBy + "|" + action + "|" + entityRef);
    }

    /** A concrete DataAccessException, since Spring's own is abstract. */
    public static class StubDataAccessException extends DataAccessException {
        public StubDataAccessException(String message) {
            super(message);
        }
        public StubDataAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
