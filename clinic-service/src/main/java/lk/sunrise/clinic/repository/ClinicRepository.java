package lk.sunrise.clinic.repository;

import lk.sunrise.clinic.dto.Dtos;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * REPOSITORY pattern. The service tier depends only on this interface, so a
 * stub can replace the database entirely in unit tests.
 *
 * One interface rather than five keeps the wiring simple for a system of this
 * size; the report notes that a larger system would split it per aggregate.
 */
public interface ClinicRepository {

    // ---- staff / auth -------------------------------------------------
    Optional<Map<String, Object>> findStaffByUsername(String username);
    void recordLoginFailure(long staffId);
    void resetLoginFailures(long staffId);

    // ---- staff administration (FR-22, FR-03) --------------------------
    List<Map<String, Object>> findAllStaff();
    long insertStaff(String username, String passwordHash, String fullName, String role);
    void insertDentist(long staffId, String registrationNo, String specialisation,
                       BigDecimal consultationFee);
    /** FR-03. Clears the lock AND the counter - the Administrator's answer to a lockout. */
    int unlockStaff(String username);
    int setStaffActive(String username, boolean active);

    // ---- reference ----------------------------------------------------
    List<Dtos.DentistDTO> findActiveDentists();
    Optional<Map<String, Object>> findTreatmentByCode(String code);
    String readSetting(String key);

    // ---- patients ------------------------------------------------------
    Optional<Dtos.PatientDTO> findPatientByNo(String patientNo);
    List<Dtos.PatientDTO> searchPatients(String term);
    String insertPatient(Dtos.RegisterPatientRequest req);
    Map<String, Object> patientDiscountInputs(String patientNo);

    // ---- appointments ---------------------------------------------------
    List<Map<String, Object>> findDayBookings(long dentistId, LocalDate date);
    String insertAppointment(String patientNo, long dentistId, LocalDate date,
                             LocalTime start, LocalTime end, String notes,
                             long createdBy, long treatmentId, BigDecimal unitPrice);
    Optional<Dtos.AppointmentDTO> findAppointmentByNo(String appointmentNo);
    List<Dtos.AppointmentDTO> findAppointmentsOn(LocalDate date);
    int updateStatus(String appointmentNo, String status, String reason);

    // ---- billing ---------------------------------------------------------
    String callGenerateBill(String appointmentNo, BigDecimal discount, String label);
    Optional<Dtos.BillDTO> findBillByNo(String billNo);
    Optional<Dtos.BillDTO> findBillByAppointment(String appointmentNo);

    // ---- reports / audit --------------------------------------------------
    List<Map<String, Object>> runReportView(String viewName);
    void writeAudit(String performedBy, String action, String entityRef, String detail);
}
