package lk.sunrise.clinic.service;

import lk.sunrise.clinic.dto.Dtos;
import lk.sunrise.clinic.exception.ClinicExceptions.BillingException;
import lk.sunrise.clinic.exception.ClinicExceptions.NotFoundException;
import lk.sunrise.clinic.support.InMemoryClinicRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Service-layer tests for BillingFacade.
 *
 * Requirements under test : FR-17, FR-18, ASM-05, ASM-10, ASM-12
 *
 * What this class proves, and what it deliberately does not:
 *
 *   PROVED HERE  - the FACADE selects the correct STRATEGY for the patient
 *                  and hands the stored procedure the right discount AMOUNT.
 *                  That choice is business policy and lives in tier 2.
 *
 *   NOT HERE     - that total = consultation + treatments - discount is
 *                  written atomically. That is sp_generate_bill's job and is
 *                  proved against the real MySQL server by 08_healthcheck.sql.
 *                  Re-implementing the procedure in the test double would
 *                  only test the copy, which is why the double does not.
 *
 * Test data is derived by equivalence partitioning on the discount rules:
 *      staff family | senior (65+) | loyalty (5th visit) | none
 * plus boundary values at exactly 65 years old and exactly 4 previous visits.
 */
@DisplayName("BillingFacade - discount selection and billing rules (FR-17, FR-18)")
class BillingFacadeTest {

    private static final String APPT = "APT-2026-000001";
    private static final String PATIENT = "PAT-2026-000001";
    private static final String SUBTOTAL = "10000.00";

    private InMemoryClinicRepository repository;
    private BillingFacade facade;

    @BeforeEach
    void setUp() {
        repository = InMemoryClinicRepository.withClinicDefaults()
                .withPatient(PATIENT, "Nimal Silva")
                .withAppointment(APPT, PATIENT, "COMPLETED", SUBTOTAL);
        facade = new BillingFacade(repository);
    }

    /** Sets the discount inputs for the patient this appointment belongs to. */
    private void patientIs(int ageYears, boolean staffFamily, int completedVisits) {
        repository.withDiscountInputs(PATIENT,
                LocalDate.now().minusYears(ageYears), staffFamily, completedVisits);
    }

    // =================================================================
    @Nested
    @DisplayName("Preconditions (FR-18, ASM-05)")
    class Preconditions {

        @Test
        @DisplayName("an unknown appointment number is a 404")
        void unknownAppointment() {
            patientIs(30, false, 0);
            assertThrows(NotFoundException.class, () -> facade.generateBill("APT-2026-999999"));
        }

        @Test
        @DisplayName("a still-booked appointment cannot be billed")
        void refusesBookedAppointment() {
            repository.withAppointment("APT-2026-000002", PATIENT, "BOOKED", SUBTOTAL);
            patientIs(30, false, 0);

            BillingException ex = assertThrows(BillingException.class,
                    () -> facade.generateBill("APT-2026-000002"));
            assertTrue(ex.getMessage().toLowerCase().contains("completed"),
                    "the message must explain the precondition, not just refuse");
        }

        @Test
        @DisplayName("a cancelled appointment cannot be billed")
        void refusesCancelledAppointment() {
            repository.withAppointment("APT-2026-000003", PATIENT, "CANCELLED", SUBTOTAL);
            patientIs(30, false, 0);
            assertThrows(BillingException.class, () -> facade.generateBill("APT-2026-000003"));
        }

        @Test
        @DisplayName("an appointment can be billed once only (FR-18, ASM-05)")
        void refusesSecondBill() {
            patientIs(30, false, 0);
            assertNotNull(facade.generateBill(APPT), "the first bill must succeed");

            BillingException ex = assertThrows(BillingException.class,
                    () -> facade.generateBill(APPT));
            assertTrue(ex.getMessage().toLowerCase().contains("already"),
                    "the receptionist must be told the bill already exists");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Strategy selection - the right rule for the right patient (ASM-12)")
    class DiscountSelection {

        @Test
        @DisplayName("an ordinary patient gets no discount - the Null Object, never null")
        void ordinaryPatientGetsNoDiscount() {
            patientIs(40, false, 0);
            facade.generateBill(APPT);

            assertEquals(new BigDecimal("0.00"), repository.lastDiscountPassedToProcedure,
                    "NoDiscount must return zero, so no caller ever tests for null");
            assertEquals("No discount", repository.lastDiscountLabelPassedToProcedure);
        }

        @Test
        @DisplayName("a patient of 65 gets the senior citizen 10%")
        void seniorGetsTenPercent() {
            patientIs(65, false, 0);
            facade.generateBill(APPT);

            assertEquals(new BigDecimal("1000.00"), repository.lastDiscountPassedToProcedure,
                    "10% of a 10,000 treatment subtotal");
            assertTrue(repository.lastDiscountLabelPassedToProcedure.toLowerCase().contains("senior"));
        }

        @Test
        @DisplayName("a patient of 64 does not - the boundary is exact")
        void sixtyFourIsNotSenior() {
            patientIs(64, false, 0);
            facade.generateBill(APPT);
            assertEquals(new BigDecimal("0.00"), repository.lastDiscountPassedToProcedure);
        }

        @Test
        @DisplayName("a fifth visit earns the loyalty 5%")
        void fifthVisitEarnsLoyalty() {
            patientIs(40, false, 4);   // four PREVIOUS visits, so this is the fifth
            facade.generateBill(APPT);

            assertEquals(new BigDecimal("500.00"), repository.lastDiscountPassedToProcedure);
            assertEquals("Returning patient 5%", repository.lastDiscountLabelPassedToProcedure,
                    "the label is printed on the bill, so it is part of the contract");
        }

        @Test
        @DisplayName("a fourth visit does not - the boundary is exact")
        void fourthVisitEarnsNothing() {
            patientIs(40, false, 3);
            facade.generateBill(APPT);
            assertEquals(new BigDecimal("0.00"), repository.lastDiscountPassedToProcedure);
        }

        @Test
        @DisplayName("staff family gets 15%")
        void staffFamilyGetsFifteenPercent() {
            patientIs(40, true, 0);
            facade.generateBill(APPT);
            assertEquals(new BigDecimal("1500.00"), repository.lastDiscountPassedToProcedure);
        }

        @Test
        @DisplayName("when several rules apply, the most generous one wins and only one applies")
        void mostGenerousRuleWins() {
            patientIs(70, true, 10);   // senior AND loyal AND staff family
            facade.generateBill(APPT);

            assertEquals(new BigDecimal("1500.00"), repository.lastDiscountPassedToProcedure,
                    "15% staff family, not 10 + 5 + 15 stacked");
        }
    }

    // =================================================================
    @Nested
    @DisplayName("The bill that comes back (FR-17, FR-19)")
    class BillContents {

        @Test
        @DisplayName("the facade returns the stored bill, read back from the database")
        void returnsStoredBill() {
            patientIs(65, false, 0);
            Dtos.BillDTO bill = facade.generateBill(APPT);

            assertNotNull(bill);
            assertTrue(bill.billNo().startsWith("BIL-"),
                    "the bill number is generated by the data tier, format BIL-YYYY-NNNNNN");
            assertEquals(APPT, bill.appointmentNo());
            assertEquals("UNPAID", bill.paymentStatus());
        }

        @Test
        @DisplayName("looking up a bill that does not exist is a 404")
        void unknownBillNumber() {
            assertThrows(NotFoundException.class, () -> facade.findByNumber("BIL-2026-999999"));
        }
    }

    // =================================================================
    @Nested
    @DisplayName("When the stored procedure refuses (NFR-04)")
    class DatabaseRuleSurfaced {

        @Test
        @DisplayName("the procedure's own message reaches the user, not a stack trace")
        void translatesDataAccessException() {
            patientIs(30, false, 0);
            repository.nextBillFailure = new InMemoryClinicRepository.StubDataAccessException(
                    "StatementCallback; SQL [{call sp_generate_bill(?,?,?,?)}]",
                    new RuntimeException("Discount cannot exceed the treatment subtotal"));

            BillingException ex = assertThrows(BillingException.class,
                    () -> facade.generateBill(APPT));

            assertEquals("Discount cannot exceed the treatment subtotal", ex.getMessage(),
                    "the root cause carries the business message the procedure signalled");
        }

        @Test
        @DisplayName("a failure with no message still produces something readable")
        void handlesMessagelessFailure() {
            patientIs(30, false, 0);
            repository.nextBillFailure =
                    new InMemoryClinicRepository.StubDataAccessException(null);

            BillingException ex = assertThrows(BillingException.class,
                    () -> facade.generateBill(APPT));
            assertEquals("The bill could not be generated", ex.getMessage());
        }
    }
}
