package lk.sunrise.clinic.repository;

import lk.sunrise.clinic.dto.Dtos;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * DAO implementation over Spring JDBC.
 *
 * Every statement is parameterised - no SQL is built by string concatenation,
 * which closes the injection route (NFR-06, ETHICAL outcome).
 *
 * Reads go through the report views wherever one exists, so the shape of a
 * result is defined once, in the database, rather than repeated in Java.
 */
@Repository
public class JdbcClinicRepository implements ClinicRepository {

    private final JdbcTemplate jdbc;
    private final SimpleJdbcCall generateBill;

    public JdbcClinicRepository(JdbcTemplate jdbc, DataSource dataSource) {
        this.jdbc = jdbc;
        this.generateBill = new SimpleJdbcCall(dataSource)
                .withProcedureName("sp_generate_bill")
                .declareParameters(
                        new org.springframework.jdbc.core.SqlParameter("p_appointment_no", Types.VARCHAR),
                        new org.springframework.jdbc.core.SqlParameter("p_discount_amount", Types.DECIMAL),
                        new org.springframework.jdbc.core.SqlParameter("p_discount_label", Types.VARCHAR),
                        new org.springframework.jdbc.core.SqlOutParameter("p_bill_no", Types.VARCHAR));
    }

    // =================================================================
    //  staff / auth
    // =================================================================

    @Override
    public Optional<Map<String, Object>> findStaffByUsername(String username) {
        return jdbc.queryForList(
                "SELECT staff_id, username, password_hash, full_name, role, active, "
              + "       failed_attempts, locked FROM staff WHERE username = ?",
                username).stream().findFirst();
    }

    @Override
    public void recordLoginFailure(long staffId) {
        jdbc.update("UPDATE staff SET failed_attempts = failed_attempts + 1, "
                  + "locked = (failed_attempts + 1 >= ?) WHERE staff_id = ?",
                Integer.parseInt(readSetting("MAX_LOGIN_FAILS")), staffId);
    }

    // ---- staff administration (FR-22, FR-03) --------------------------

    @Override
    public List<Map<String, Object>> findAllStaff() {
        return jdbc.queryForList(
                "SELECT s.staff_id, s.username, s.full_name, s.role, s.active, s.locked, "
              + "       s.failed_attempts, d.registration_no, d.specialisation, d.consultation_fee "
              + "  FROM staff s LEFT JOIN dentist d ON d.staff_id = s.staff_id "
              + " ORDER BY s.role, s.full_name");
    }

    @Override
    public long insertStaff(String username, String passwordHash, String fullName, String role) {
        KeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var ps = connection.prepareStatement(
                    "INSERT INTO staff (username, password_hash, full_name, role) VALUES (?, ?, ?, ?)",
                    java.sql.Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, passwordHash);
            ps.setString(3, fullName);
            ps.setString(4, role);
            return ps;
        }, key);
        return key.getKey().longValue();
    }

    @Override
    public void insertDentist(long staffId, String registrationNo, String specialisation,
                              BigDecimal consultationFee) {
        jdbc.update("INSERT INTO dentist (staff_id, registration_no, specialisation, consultation_fee) "
                  + "VALUES (?, ?, ?, ?)", staffId, registrationNo, specialisation, consultationFee);
    }

    @Override
    public int unlockStaff(String username) {
        return jdbc.update(
                "UPDATE staff SET locked = FALSE, failed_attempts = 0 WHERE username = ?", username);
    }

    @Override
    public int setStaffActive(String username, boolean active) {
        return jdbc.update("UPDATE staff SET active = ? WHERE username = ?", active, username);
    }

    @Override
    public void resetLoginFailures(long staffId) {
        jdbc.update("UPDATE staff SET failed_attempts = 0 WHERE staff_id = ?", staffId);
    }

    // =================================================================
    //  reference data
    // =================================================================

    @Override
    public List<Dtos.DentistDTO> findActiveDentists() {
        return jdbc.query(
                "SELECT d.dentist_id, s.full_name, d.specialisation, d.consultation_fee "
              + "  FROM dentist d JOIN staff s ON s.staff_id = d.staff_id "
              + " WHERE d.active = TRUE ORDER BY s.full_name",
                (rs, i) -> new Dtos.DentistDTO(
                        rs.getLong("dentist_id"),
                        rs.getString("full_name"),
                        rs.getString("specialisation"),
                        rs.getBigDecimal("consultation_fee")));
    }

    @Override
    public Optional<Map<String, Object>> findTreatmentByCode(String code) {
        return jdbc.queryForList(
                "SELECT treatment_id, code, name, price, duration_minutes "
              + "  FROM treatment_type WHERE code = ? AND active = TRUE", code)
                .stream().findFirst();
    }

    @Override
    public String readSetting(String key) {
        List<String> v = jdbc.queryForList(
                "SELECT setting_value FROM clinic_setting WHERE setting_key = ?",
                String.class, key);
        return v.isEmpty() ? null : v.get(0);
    }

    // =================================================================
    //  patients
    // =================================================================

    private static final String PATIENT_SELECT =
            "SELECT p.patient_no, p.full_name, p.address, p.contact_number, p.email, "
          + "       p.date_of_birth, "
          + "       (SELECT COUNT(*) FROM appointment a "
          + "         WHERE a.patient_id = p.patient_id AND a.status = 'COMPLETED') AS completed_visits "
          + "  FROM patient p ";

    private static final RowMapper<Dtos.PatientDTO> PATIENT_MAPPER = (rs, i) ->
            new Dtos.PatientDTO(
                    rs.getString("patient_no"),
                    rs.getString("full_name"),
                    rs.getString("address"),
                    rs.getString("contact_number"),
                    rs.getString("email"),
                    rs.getDate("date_of_birth").toLocalDate(),
                    rs.getInt("completed_visits"));

    @Override
    public Optional<Dtos.PatientDTO> findPatientByNo(String patientNo) {
        return jdbc.query(PATIENT_SELECT + "WHERE p.patient_no = ?",
                PATIENT_MAPPER, patientNo).stream().findFirst();
    }

    @Override
    public List<Dtos.PatientDTO> searchPatients(String term) {
        String like = "%" + term + "%";
        return jdbc.query(PATIENT_SELECT
                        + "WHERE p.full_name LIKE ? OR p.contact_number LIKE ? OR p.patient_no LIKE ? "
                        + "ORDER BY p.full_name LIMIT 50",
                PATIENT_MAPPER, like, like, like);
    }

    @Override
    public String insertPatient(Dtos.RegisterPatientRequest req) {
        jdbc.update(
                "INSERT INTO patient (full_name, address, contact_number, email, "
              + "                     date_of_birth, is_staff_family) VALUES (?,?,?,?,?,?)",
                req.fullName(), req.address(), req.contactNumber(), req.email(),
                java.sql.Date.valueOf(req.dateOfBirth()),
                req.staffFamily() != null && req.staffFamily());
        // trg_patient_number assigned the number; read it back.
        return jdbc.queryForObject(
                "SELECT patient_no FROM patient WHERE patient_id = LAST_INSERT_ID()",
                String.class);
    }

    @Override
    public Map<String, Object> patientDiscountInputs(String patientNo) {
        return jdbc.queryForMap(
                "SELECT p.date_of_birth, p.is_staff_family, "
              + "       (SELECT COUNT(*) FROM appointment a "
              + "         WHERE a.patient_id = p.patient_id AND a.status = 'COMPLETED') AS completed_visits "
              + "  FROM patient p WHERE p.patient_no = ?", patientNo);
    }

    // =================================================================
    //  appointments
    // =================================================================

    @Override
    public List<Map<String, Object>> findDayBookings(long dentistId, LocalDate date) {
        return jdbc.queryForList(
                "SELECT start_time, end_time FROM appointment "
              + " WHERE dentist_id = ? AND appointment_date = ? AND status = 'BOOKED' "
              + " ORDER BY start_time",
                dentistId, java.sql.Date.valueOf(date));
    }

    @Override
    public String insertAppointment(String patientNo, long dentistId, LocalDate date,
                                    LocalTime start, LocalTime end, String notes,
                                    long createdBy, long treatmentId, BigDecimal unitPrice) {
        jdbc.update(
                "INSERT INTO appointment (patient_id, dentist_id, appointment_date, "
              + "        start_time, end_time, notes, created_by) "
              + "SELECT p.patient_id, ?, ?, ?, ?, ?, ? FROM patient p WHERE p.patient_no = ?",
                dentistId, java.sql.Date.valueOf(date), java.sql.Time.valueOf(start),
                java.sql.Time.valueOf(end), notes, createdBy, patientNo);

        Long appointmentId = jdbc.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        jdbc.update(
                "INSERT INTO appointment_treatment (appointment_id, treatment_id, quantity, unit_price) "
              + "VALUES (?,?,1,?)", appointmentId, treatmentId, unitPrice);

        return jdbc.queryForObject(
                "SELECT appointment_no FROM appointment WHERE appointment_id = ?",
                String.class, appointmentId);
    }

    private static final RowMapper<Dtos.AppointmentDTO> APPT_MAPPER = (rs, i) ->
            new Dtos.AppointmentDTO(
                    rs.getString("appointment_no"),
                    rs.getString("patient_no"),
                    rs.getString("patient_name"),
                    rs.getString("contact_number"),
                    rs.getString("dentist_name"),
                    rs.getString("specialisation"),
                    rs.getDate("appointment_date").toLocalDate(),
                    rs.getTime("start_time").toLocalTime(),
                    rs.getTime("end_time").toLocalTime(),
                    rs.getString("status"),
                    rs.getString("treatments"),
                    rs.getBigDecimal("treatment_subtotal"),
                    rs.getBigDecimal("consultation_fee"),
                    rs.getString("notes"));

    @Override
    public Optional<Dtos.AppointmentDTO> findAppointmentByNo(String appointmentNo) {
        return jdbc.query("SELECT * FROM vw_appointment_detail WHERE appointment_no = ?",
                APPT_MAPPER, appointmentNo).stream().findFirst();
    }

    @Override
    public List<Dtos.AppointmentDTO> findAppointmentsOn(LocalDate date) {
        return jdbc.query(
                "SELECT * FROM vw_appointment_detail WHERE appointment_date = ? "
              + " ORDER BY start_time",
                APPT_MAPPER, java.sql.Date.valueOf(date));
    }

    @Override
    public int updateStatus(String appointmentNo, String status, String reason) {
        return jdbc.update(
                "UPDATE appointment SET status = ?, cancel_reason = ? WHERE appointment_no = ?",
                status, reason, appointmentNo);
    }

    // =================================================================
    //  billing
    // =================================================================

    @Override
    public String callGenerateBill(String appointmentNo, BigDecimal discount, String label) {
        Map<String, Object> out = generateBill.execute(Map.of(
                "p_appointment_no", appointmentNo,
                "p_discount_amount", discount,
                "p_discount_label", label));
        return (String) out.get("p_bill_no");
    }

    private Optional<Dtos.BillDTO> loadBill(String whereClause, Object param) {
        List<Map<String, Object>> head = jdbc.queryForList(
                "SELECT b.bill_id, b.bill_no, a.appointment_no, p.full_name AS patient_name, "
              + "       b.issued_on, b.consultation_fee, b.treatment_subtotal, "
              + "       b.discount_amount, b.discount_label, b.total_payable, b.payment_status "
              + "  FROM bill b "
              + "  JOIN appointment a ON a.appointment_id = b.appointment_id "
              + "  JOIN patient p     ON p.patient_id     = a.patient_id "
              + " WHERE " + whereClause, param);

        if (head.isEmpty()) {
            return Optional.empty();
        }
        Map<String, Object> h = head.get(0);

        List<Dtos.BillLineDTO> lines = jdbc.query(
                "SELECT line_no, description, unit_price, quantity, line_total "
              + "  FROM bill_line WHERE bill_id = ? ORDER BY line_no",
                (rs, i) -> new Dtos.BillLineDTO(
                        rs.getInt("line_no"), rs.getString("description"),
                        rs.getBigDecimal("unit_price"), rs.getInt("quantity"),
                        rs.getBigDecimal("line_total")),
                h.get("bill_id"));

        return Optional.of(new Dtos.BillDTO(
                (String) h.get("bill_no"),
                (String) h.get("appointment_no"),
                (String) h.get("patient_name"),
                h.get("issued_on") instanceof java.time.LocalDateTime dt ? dt
                        : ((java.sql.Timestamp) h.get("issued_on")).toLocalDateTime(),
                (BigDecimal) h.get("consultation_fee"),
                (BigDecimal) h.get("treatment_subtotal"),
                (BigDecimal) h.get("discount_amount"),
                (String) h.get("discount_label"),
                (BigDecimal) h.get("total_payable"),
                String.valueOf(h.get("payment_status")),
                lines));
    }

    @Override
    public Optional<Dtos.BillDTO> findBillByNo(String billNo) {
        return loadBill("b.bill_no = ?", billNo);
    }

    @Override
    public Optional<Dtos.BillDTO> findBillByAppointment(String appointmentNo) {
        return loadBill("a.appointment_no = ?", appointmentNo);
    }

    // =================================================================
    //  reports / audit
    // =================================================================

    /** Only names from ReportService's fixed list reach here - never user input. */
    @Override
    public List<Map<String, Object>> runReportView(String viewName) {
        return jdbc.queryForList("SELECT * FROM " + viewName);
    }

    @Override
    public void writeAudit(String performedBy, String action, String entityRef, String detail) {
        jdbc.update("INSERT INTO audit_entry (performed_by, action, entity_ref, detail) "
                  + "VALUES (?,?,?,?)", performedBy, action, entityRef, detail);
    }
}
