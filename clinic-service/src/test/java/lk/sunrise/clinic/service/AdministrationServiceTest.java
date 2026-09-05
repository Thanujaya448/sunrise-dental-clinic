package lk.sunrise.clinic.service;

import lk.sunrise.clinic.domain.TreatmentType;
import lk.sunrise.clinic.exception.ClinicExceptions.NotFoundException;
import lk.sunrise.clinic.exception.ClinicExceptions.ValidationException;
import lk.sunrise.clinic.support.InMemoryClinicRepository;
import lk.sunrise.clinic.support.InMemoryTreatmentTypeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Service-layer tests for AdministrationService.
 *
 * Requirements under test : FR-03 (unlock), FR-21, FR-22, FR-24, NFR-04
 *
 * Equivalence partitions on a treatment: valid | duplicate code | blank name |
 * negative price | duration below 5 | duration above 480.
 * Boundary values on duration, which the schema constrains to 5-480 minutes.
 */
@DisplayName("AdministrationService - maintenance and account recovery (FR-03, FR-21, FR-22)")
class AdministrationServiceTest {

    private InMemoryClinicRepository repository;
    private InMemoryTreatmentTypeRepository treatments;
    private AdministrationService service;

    @BeforeEach
    void setUp() {
        repository = InMemoryClinicRepository.withClinicDefaults()
                .withStaff(10L, "reception1", "$2a$10$hash", "RECEPTIONIST", true, false, 0)
                .withStaff(13L, "locked.user", "$2a$10$hash", "RECEPTIONIST", true, true, 5);
        treatments = new InMemoryTreatmentTypeRepository()
                .seed("SCAL", "Scaling and polishing", "6500.00", 30, true)
                .seed("OLD", "Withdrawn treatment", "1000.00", 15, false);
        service = new AdministrationService(treatments, repository);
    }

    // =================================================================
    @Nested
    @DisplayName("Treatments and prices (FR-21)")
    class Treatments {

        @Test
        @DisplayName("the list includes inactive treatments, so one can be restored")
        void listsInactiveToo() {
            assertEquals(2, service.allTreatments().size(),
                    "an Administrator cannot re-enable what the list hides");
        }

        @Test
        @DisplayName("a new treatment is created and its code is upper-cased")
        void createsTreatment() {
            TreatmentType t = service.createTreatment("wht", "Whitening",
                    new BigDecimal("18000.00"), 45);

            assertEquals("WHT", t.getCode(), "codes are normalised, not taken as typed");
            assertEquals(new BigDecimal("18000.00"), t.getPrice());
            assertTrue(t.isActive());
        }

        @Test
        @DisplayName("a duplicate code is refused")
        void refusesDuplicateCode() {
            assertThrows(ValidationException.class, () -> service.createTreatment(
                    "SCAL", "Another scaling", new BigDecimal("100.00"), 30));
        }

        @Test
        @DisplayName("the standing price can be changed")
        void updatesPrice() {
            TreatmentType t = service.updateTreatment("SCAL", "Scaling and polishing",
                    new BigDecimal("7200.00"), 30, true);

            assertEquals(new BigDecimal("7200.00"), t.getPrice());
            assertEquals(new BigDecimal("7200.00"),
                    treatments.findByCode("SCAL").orElseThrow().getPrice(),
                    "the change must be persisted, not just returned");
        }

        @Test
        @DisplayName("a treatment can be withdrawn without deleting it")
        void deactivatesRatherThanDeletes() {
            service.updateTreatment("SCAL", "Scaling and polishing",
                    new BigDecimal("6500.00"), 30, false);

            assertFalse(treatments.findByCode("SCAL").orElseThrow().isActive());
            assertEquals(2, service.allTreatments().size(),
                    "history must survive: past appointments still reference this row");
        }

        @Test
        @DisplayName("an unknown code is a 404")
        void unknownCode() {
            assertThrows(NotFoundException.class, () -> service.updateTreatment(
                    "ZZZ", "Nothing", new BigDecimal("1.00"), 15, true));
        }

        @Test
        @DisplayName("a negative price is refused")
        void refusesNegativePrice() {
            assertThrows(ValidationException.class, () -> service.createTreatment(
                    "NEG", "Bad", new BigDecimal("-1.00"), 30));
        }

        @Test
        @DisplayName("duration boundaries match the schema: 5 and 480 in, 4 and 481 out")
        void durationBoundaries() {
            assertNotNull(service.createTreatment("MIN", "Shortest", new BigDecimal("100"), 5));
            assertNotNull(service.createTreatment("MAX", "Longest", new BigDecimal("100"), 480));
            assertThrows(ValidationException.class, () -> service.createTreatment(
                    "TOOLO", "Too short", new BigDecimal("100"), 4));
            assertThrows(ValidationException.class, () -> service.createTreatment(
                    "TOOHI", "Too long", new BigDecimal("100"), 481));
        }

        @Test
        @DisplayName("a blank name or code is refused before anything is written")
        void refusesBlankFields() {
            assertThrows(ValidationException.class, () -> service.createTreatment(
                    "  ", "Nameless code", new BigDecimal("100"), 30));
            assertThrows(ValidationException.class, () -> service.createTreatment(
                    "NEW", "  ", new BigDecimal("100"), 30));
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Staff and dentists (FR-22)")
    class Staff {

        @Test
        @DisplayName("a receptionist is created with a hashed password")
        void createsReceptionist() {
            service.createStaff("newrecep", "Str0ngPass!", "New Receptionist",
                    "RECEPTIONIST", null, null, null);

            var row = repository.findStaffByUsername("newrecep").orElseThrow();
            String stored = (String) row.get("password_hash");
            assertTrue(stored.startsWith("$2"), "FR-02: the password is hashed, never stored raw");
            assertFalse(stored.contains("Str0ngPass!"));
        }

        @Test
        @DisplayName("creating a dentist writes the dentist row as well")
        void createsDentistRowToo() {
            service.createStaff("dr.new", "Str0ngPass!", "Dr. New", "DENTIST",
                    "SLMC-99999", "Orthodontics", new BigDecimal("2500.00"));

            assertNotNull(repository.findStaffByUsername("dr.new").orElse(null));
            assertEquals(1, repository.findActiveDentists().size(),
                    "a dentist is a staff member with a fee; the two rows are meaningless apart");
        }

        @Test
        @DisplayName("a dentist without a registration number or fee is refused")
        void dentistNeedsRegistrationAndFee() {
            assertThrows(ValidationException.class, () -> service.createStaff(
                    "dr.bad", "Str0ngPass!", "Dr. Bad", "DENTIST", "  ", "General", new BigDecimal("100")));
            assertThrows(ValidationException.class, () -> service.createStaff(
                    "dr.bad2", "Str0ngPass!", "Dr. Bad", "DENTIST", "SLMC-1", "General", null));
        }

        @Test
        @DisplayName("a duplicate username is refused")
        void refusesDuplicateUsername() {
            assertThrows(ValidationException.class, () -> service.createStaff(
                    "reception1", "Str0ngPass!", "Clash", "RECEPTIONIST", null, null, null));
        }

        @Test
        @DisplayName("a short password is refused (NFR-01)")
        void refusesShortPassword() {
            assertThrows(ValidationException.class, () -> service.createStaff(
                    "shorty", "abc", "Short Password", "RECEPTIONIST", null, null, null));
        }

        @Test
        @DisplayName("an unknown role is refused")
        void refusesUnknownRole() {
            assertThrows(ValidationException.class, () -> service.createStaff(
                    "someone", "Str0ngPass!", "Someone", "SUPERUSER", null, null, null));
        }

        @Test
        @DisplayName("creating staff is written to the audit trail (FR-24)")
        void writesAudit() {
            service.createStaff("audited", "Str0ngPass!", "Audited", "RECEPTIONIST", null, null, null);
            assertTrue(repository.auditTrail.stream().anyMatch(e -> e.contains("STAFF_CREATED")));
        }

        @Test
        @DisplayName("an account can be deactivated rather than deleted")
        void deactivates() {
            service.setActive("reception1", false);
            assertFalse((Boolean) repository.findStaffByUsername("reception1")
                    .orElseThrow().get("active"));
        }
    }

    // =================================================================
    @Nested
    @DisplayName("Account recovery - the other half of FR-03")
    class Unlock {

        @Test
        @DisplayName("the Administrator can unlock a locked account")
        void unlocksAccount() {
            service.unlockAccount("locked.user");

            var row = repository.findStaffByUsername("locked.user").orElseThrow();
            assertFalse((Boolean) row.get("locked"),
                    "FR-03 tells the user to ask the Administrator; this is the Administrator's answer");
            assertEquals(0, ((Number) row.get("failed_attempts")).intValue(),
                    "clearing the lock without clearing the counter would re-lock on the next mistake");
        }

        @Test
        @DisplayName("unlocking is written to the audit trail (FR-24)")
        void unlockIsAudited() {
            service.unlockAccount("locked.user");
            assertTrue(repository.auditTrail.stream().anyMatch(e -> e.contains("ACCOUNT_UNLOCKED")),
                    "who lifted a security control, and when, must be recoverable");
        }

        @Test
        @DisplayName("unlocking an account that does not exist is a 404")
        void unknownAccount() {
            assertThrows(NotFoundException.class, () -> service.unlockAccount("nobody"));
        }
    }
}
