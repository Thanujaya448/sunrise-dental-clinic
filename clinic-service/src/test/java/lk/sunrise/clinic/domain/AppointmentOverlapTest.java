package lk.sunrise.clinic.domain;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * TDD cycle 1 - written BEFORE Appointment.overlapsWith() exists.
 *
 * Requirement under test : FR-10, ASM-08
 * Rule                   : two appointments clash when their time ranges,
 *                          each widened by the turnaround buffer, intersect
 *                          for the same dentist on the same day. Cancelled
 *                          and no-show appointments free the slot.
 *
 * Test data is DERIVED, not invented (Task C, "derive test data"):
 *   - equivalence partitions : before / overlapping / after / different key
 *   - boundary values        : 11:40 exactly clears a 11:30 end + 10 min buffer,
 *                              11:35 does not
 */
@DisplayName("Appointment clash detection (FR-10, ASM-08)")
class AppointmentOverlapTest {

    private static final LocalDate MON = LocalDate.of(2026, 9, 1);
    private static final LocalDate TUE = LocalDate.of(2026, 9, 2);
    private static final long DR_PERERA   = 1L;
    private static final long DR_FERNANDO = 2L;
    private static final int  BUFFER      = 10;

    /** Dr. Perera, Monday, 10:00-11:30 - the appointment everything is compared against. */
    private Appointment existing() {
        return booked(DR_PERERA, MON, "10:00", "11:30");
    }

    private Appointment booked(long dentistId, LocalDate date, String from, String to) {
        return new Appointment(dentistId, date,
                LocalTime.parse(from), LocalTime.parse(to), AppointmentStatus.BOOKED);
    }

    // -----------------------------------------------------------------
    //  Partition 1 : ranges that genuinely intersect  ->  must clash
    // -----------------------------------------------------------------
    @Nested
    @DisplayName("overlapping ranges are rejected")
    class Overlapping {

        @Test
        @DisplayName("identical times")
        void identicalTimes() {
            assertTrue(booked(DR_PERERA, MON, "10:00", "11:30").overlapsWith(existing(), BUFFER));
        }

        @Test
        @DisplayName("new appointment starts inside the existing one")
        void startsInside() {
            assertTrue(booked(DR_PERERA, MON, "11:00", "11:45").overlapsWith(existing(), BUFFER));
        }

        @Test
        @DisplayName("new appointment ends inside the existing one")
        void endsInside() {
            assertTrue(booked(DR_PERERA, MON, "09:30", "10:30").overlapsWith(existing(), BUFFER));
        }

        @Test
        @DisplayName("new appointment completely contains the existing one")
        void contains() {
            assertTrue(booked(DR_PERERA, MON, "09:00", "12:00").overlapsWith(existing(), BUFFER));
        }
    }

    // -----------------------------------------------------------------
    //  Partition 2 : boundary values around the 10-minute buffer
    // -----------------------------------------------------------------
    @Nested
    @DisplayName("the turnaround buffer is enforced")
    class Buffer {

        @Test
        @DisplayName("11:35 falls inside the buffer after an 11:30 finish")
        void insideBuffer() {
            assertTrue(booked(DR_PERERA, MON, "11:35", "12:05").overlapsWith(existing(), BUFFER));
        }

        @Test
        @DisplayName("11:40 clears the buffer exactly")
        void clearsBufferExactly() {
            assertFalse(booked(DR_PERERA, MON, "11:40", "12:10").overlapsWith(existing(), BUFFER));
        }

        @Test
        @DisplayName("finishing at 09:50 clears the buffer before a 10:00 start")
        void clearsBufferBefore() {
            assertFalse(booked(DR_PERERA, MON, "09:10", "09:50").overlapsWith(existing(), BUFFER));
        }

        @Test
        @DisplayName("with a zero buffer, back-to-back appointments are legal")
        void zeroBuffer() {
            assertFalse(booked(DR_PERERA, MON, "11:30", "12:00").overlapsWith(existing(), 0));
        }
    }

    // -----------------------------------------------------------------
    //  Partition 3 : a differing key means the rule does not apply
    // -----------------------------------------------------------------
    @Nested
    @DisplayName("a different dentist, day or status frees the slot")
    class DifferentKey {

        @Test
        @DisplayName("a different dentist never clashes")
        void differentDentist() {
            assertFalse(booked(DR_FERNANDO, MON, "10:00", "11:30").overlapsWith(existing(), BUFFER));
        }

        @Test
        @DisplayName("a different day never clashes")
        void differentDay() {
            assertFalse(booked(DR_PERERA, TUE, "10:00", "11:30").overlapsWith(existing(), BUFFER));
        }

        @Test
        @DisplayName("a cancelled appointment frees its slot")
        void cancelledFreesSlot() {
            Appointment cancelled = new Appointment(DR_PERERA, MON,
                    LocalTime.parse("10:00"), LocalTime.parse("11:30"), AppointmentStatus.CANCELLED);
            assertFalse(booked(DR_PERERA, MON, "10:00", "11:30").overlapsWith(cancelled, BUFFER));
        }

        @Test
        @DisplayName("a later slot on the same day is free")
        void laterSameDay() {
            assertFalse(booked(DR_PERERA, MON, "14:00", "14:30").overlapsWith(existing(), BUFFER));
        }
    }
}
