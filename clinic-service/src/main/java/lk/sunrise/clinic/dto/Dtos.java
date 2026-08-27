package lk.sunrise.clinic.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * ADAPTER / DTO pattern (structural).
 *
 * These records are the contract the REST API exposes. They deliberately do
 * not mirror the database tables: the client never learns a primary key or a
 * column name, so the schema can change without breaking tier 1.
 *
 * Java records give immutability and a Jackson-friendly shape in one line
 * each.
 */
public final class Dtos {

    private Dtos() { }

    // ---- authentication ---------------------------------------------
    public record LoginRequest(String username, String password) { }

    public record SessionDTO(String token, String fullName, String role,
                             LocalDateTime expiresAt) { }

    // ---- patients ---------------------------------------------------
    public record PatientDTO(String patientNo, String fullName, String address,
                             String contactNumber, String email,
                             LocalDate dateOfBirth, int completedVisits) { }

    public record RegisterPatientRequest(String fullName, String address,
                                         String contactNumber, String email,
                                         LocalDate dateOfBirth,
                                         Boolean staffFamily) { }

    // ---- appointments -----------------------------------------------
    public record BookingRequest(String patientNo, Long dentistId,
                                 String treatmentCode, LocalDate appointmentDate,
                                 LocalTime startTime, String notes) { }

    public record AppointmentDTO(String appointmentNo, String patientNo,
                                 String patientName, String contactNumber,
                                 String dentistName, String specialisation,
                                 LocalDate appointmentDate, LocalTime startTime,
                                 LocalTime endTime, String status,
                                 String treatments, BigDecimal treatmentSubtotal,
                                 BigDecimal consultationFee, String notes) { }

    /** Returned with HTTP 409 when a slot clashes - carries the alternatives. */
    public record SlotConflictDTO(String message, List<LocalTime> suggestedSlots) { }

    // ---- billing -----------------------------------------------------
    public record BillLineDTO(int lineNo, String description,
                              BigDecimal unitPrice, int quantity,
                              BigDecimal lineTotal) { }

    public record BillDTO(String billNo, String appointmentNo, String patientName,
                          LocalDateTime issuedOn, BigDecimal consultationFee,
                          BigDecimal treatmentSubtotal, BigDecimal discountAmount,
                          String discountLabel, BigDecimal totalPayable,
                          String paymentStatus, List<BillLineDTO> lines) { }

    // ---- reference data ----------------------------------------------
    public record DentistDTO(Long dentistId, String fullName, String specialisation,
                             BigDecimal consultationFee) { }

    // ---- errors -------------------------------------------------------
    public record ErrorDTO(String message) { }
}
