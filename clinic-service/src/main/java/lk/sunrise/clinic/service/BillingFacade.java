package lk.sunrise.clinic.service;

import lk.sunrise.clinic.domain.Patient;
import lk.sunrise.clinic.dto.Dtos;
import lk.sunrise.clinic.exception.ClinicExceptions.*;
import lk.sunrise.clinic.pattern.DiscountPolicy;
import lk.sunrise.clinic.pattern.DiscountStrategy;
import lk.sunrise.clinic.repository.ClinicRepository;
import lk.sunrise.clinic.repository.Sql;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Map;

/**
 * FACADE pattern (structural). FR-17 to FR-19.
 *
 * The controller makes one call. Behind it: resolve the patient, pick a
 * discount strategy, invoke the stored procedure, read the bill back.
 *
 * The split of responsibility is deliberate and is the subject of a paragraph
 * in the report - choosing WHICH discount applies is business policy and
 * belongs here; summing charges and writing bill rows atomically is set work
 * and belongs in sp_generate_bill.
 */
@Service
public class BillingFacade {

    private final ClinicRepository repository;
    private final DiscountPolicy discountPolicy = new DiscountPolicy();

    public BillingFacade(ClinicRepository repository) {
        this.repository = repository;
    }

    public Dtos.BillDTO generateBill(String appointmentNo) {
        Dtos.AppointmentDTO appt = repository.findAppointmentByNo(appointmentNo)
                .orElseThrow(() -> new NotFoundException(
                        "No appointment with number " + appointmentNo));

        if (!"COMPLETED".equals(appt.status())) {
            throw new BillingException(
                    "This appointment has not been completed yet, so it cannot be billed.");
        }
        if (repository.findBillByAppointment(appointmentNo).isPresent()) {
            throw new BillingException("This appointment has already been billed.");
        }

        DiscountStrategy strategy = resolveDiscount(appt.patientNo());
        BigDecimal subtotal = appt.treatmentSubtotal() == null
                ? BigDecimal.ZERO : appt.treatmentSubtotal();
        BigDecimal discount = strategy.calculate(subtotal);

        try {
            String billNo = repository.callGenerateBill(appointmentNo, discount, strategy.getLabel());
            return repository.findBillByNo(billNo)
                    .orElseThrow(() -> new BillingException("Bill could not be read back"));
        } catch (DataAccessException ex) {
            // The stored procedure signalled a business rule. Surface its own
            // message rather than a stack trace the receptionist cannot act on.
            throw new BillingException(rootMessage(ex));
        }
    }

    public Dtos.BillDTO findByNumber(String billNo) {
        return repository.findBillByNo(billNo)
                .orElseThrow(() -> new NotFoundException("No bill with number " + billNo));
    }

    /** Builds a Patient from the discount inputs and asks the policy. */
    private DiscountStrategy resolveDiscount(String patientNo) {
        Map<String, Object> row = repository.patientDiscountInputs(patientNo);
        Patient patient = new Patient(
                null,
                Sql.asLocalDate(row.get("date_of_birth")),
                Sql.asBoolean(row.get("is_staff_family")),
                Sql.asInt(row.get("completed_visits")));
        return discountPolicy.resolve(patient, LocalDate.now());
    }

    private String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null) {
            c = c.getCause();
        }
        return c.getMessage() == null ? "The bill could not be generated" : c.getMessage();
    }
}
