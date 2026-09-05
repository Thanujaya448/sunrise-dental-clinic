# Sunrise Dental Clinic — Test Plan and Traceability Matrix

**CIS6003 Advanced Programming — WRIT1, Task C**
Author: Thanujaya Hasaranga Perera  |  Registration number: st20374257

---

## 1. Testing strategy

Testing follows the shape of the architecture. Each tier is proved by the
technique that can actually prove it, and nothing is proved twice.

| Level | What it covers | Technique | Where it lives |
|---|---|---|---|
| **Unit — domain** | Rules that are pure arithmetic or pure logic: the clash rule, the discount rules | JUnit 5, no collaborators at all | `domain/AppointmentOverlapTest`, `pattern/DiscountStrategyTest` |
| **Unit — service** | Tier 2 business logic: validation, scheduling, authorisation, discount selection | JUnit 5 with a hand-written **test double** replacing the repository | `service/*Test` |
| **Integration — data tier** | Rules enforced by the database: the overlap trigger, `sp_generate_bill` | SQL scripts run against a real MySQL 8 server | `db/06_verify.sql`, `db/08_healthcheck.sql`, and the `database-rules` job in CI |
| **Manual — presentation** | The browser and desktop clients end to end | Scripted manual test cases with recorded evidence | Section 7 of this document |

### 1.1 Why a hand-written test double rather than Mockito

`spring-boot-starter-test` brings Mockito, so a mock was available. A
hand-written `InMemoryClinicRepository` was chosen instead:

- A mock verifies that a **method was called**. The double holds state, so a
  test asserts on the **consequence** — the appointment now exists, the status
  really changed, the audit trail records it. Behaviour verification couples a
  test to *how* the service is written; state verification couples it only to
  *what* it achieves, so these tests survive refactoring.
- Fifteen `when(...).thenReturn(...)` lines do not document a contract. One
  readable class does.
- The double is itself the proof that the tiers are separated. The entire
  service layer runs in it with **no MySQL, no JDBC driver and no Spring
  context**. If a single business rule had leaked into a SQL string, these
  tests could not exist.

### 1.2 What the service tests deliberately do NOT do

The double does **not** re-implement the overlap trigger or `sp_generate_bill`.
Those are data-tier rules; re-implementing them in Java would only test the
copy. They are proved against a real MySQL 8 server instead — see section 6.

`BillingFacadeTest` therefore asserts that the facade selects the right
**Strategy** and passes the right discount **amount** to the procedure. The
arithmetic that turns that amount into a bill total is asserted in SQL.

### 1.3 Test-driven development

Two features were written test-first, and the red and green steps are separate
commits so the sequence is visible in the Git history rather than merely
claimed:

| Cycle | Feature | Red commit — test written first, fails | Green commit — implementation, test passes |
|---|---|---|---|
| 1 | `Appointment.overlapsWith()` — the clash rule (FR-10, ASM-08) | `bb1ac3d` *test(domain): failing tests for appointment clash rule* | `94d7a0b` *feat(domain): implement clash rule with turnaround buffer* — 12 tests pass |
| 2 | `DiscountStrategy` family — the discount rules (FR-17, ASM-12) | `ffa86d5` *test(pattern): failing tests for discount strategies* | `8b73b14` *feat(pattern): implement discount strategies with BigDecimal rounding* — 18 tests pass |

Checking out the red commit and running `mvn test` reproduces the failure; the
green commit that follows it makes the same tests pass without altering them.
That sequence in the history is the evidence — the tests were not written after
the code to fit it.

---

## 2. How test data was derived

Test data is **derived from the specification**, not invented. Two techniques
were applied to every rule that takes a value.

### 2.1 Equivalence partitioning

Each input is divided into classes that the system should treat identically;
one value is taken from each class. Testing 09:00 and 09:15 proves nothing new,
because they are in the same class.

| Input | Partitions | Representative values |
|---|---|---|
| Booking request completeness | complete · missing patient · missing dentist · missing treatment · missing date/time | one case each |
| Patient number | exists · does not exist | `PAT-2026-000001` · `PAT-2026-999999` |
| Treatment code | active · unknown | `SCA` · `ZZZ` |
| Appointment day | open day · closed day (Sunday) · past date | next Monday · next Sunday · yesterday |
| Requested slot | free · overlapping · inside buffer · adjacent, clear | see 2.2 |
| Appointment status when billing | `COMPLETED` · `BOOKED` · `CANCELLED` · already billed | one case each |
| Patient discount category | none · senior · loyalty · staff family · several at once | age 40 / 65 / 4 visits / staff flag / all three |
| Credentials | correct · wrong password · unknown user · locked · deactivated · blank | one case each |

### 2.2 Boundary value analysis

Every defect the clinic reported happens at an edge, never in the middle of a
range. Each boundary is tested at the value itself and at the adjacent value on
each side.

| Rule | Boundary | Just below | On the boundary | Just above |
|---|---|---|---|---|
| Opening time 08:00 | 08:00 | 07:45 → refused | 08:00 → **accepted** | 08:15 → accepted |
| Closing time 20:00 (30-min treatment) | 19:30 start | 19:15 → accepted | 19:30 → **accepted** (ends 20:00) | 19:45 → refused |
| Slot grain 15 min | multiples of 15 | 08:05, 08:10, 08:20 → refused | 08:00, 08:15, 08:30, 08:45 → accepted | — |
| Turnaround buffer 10 min, existing 10:00–11:30 | 09:15–09:45 → accepted (widens to 09:55) | — | 09:30–10:00 → **refused** (widens to 10:10) | 11:45 → accepted |
| Senior citizen age 65 | 64 → no discount | **65 → 10%** | 66 → 10% |
| Loyalty, 5th visit | 3 previous → none | **4 previous → 5%** | 5 previous → 5% |
| Login attempts, max 5 | 1st failure → "4 attempts remaining" | — | — |
| Session, 20 min idle | live token → accepted | 0-minute window → **expired** | — |

### 2.3 Rounding

Money is `BigDecimal` with `RoundingMode.HALF_UP` to two decimal places,
matching `DECIMAL(10,2)` in MySQL, and is asserted at a value that actually
rounds — a subtotal chosen so that the percentage does not divide exactly.
`double` is never used for money anywhere in the system.

---

## 3. Automated test plan — tier 2 and the domain

Every row below is one automated test. Run them with:

```
cd clinic-service
mvn -B verify
```

Measured result: **119 tests run, 0 failures, 0 errors**, JaCoCo report produced
and the service JAR packaged. Screenshot the Maven output and the JaCoCo
summary page into `docs/evidence/`.

### 3.1 Authentication — `AuthenticationServiceTest` (20 tests)

| Test ID | Requirement | Test method | Input / condition | Expected outcome |
|---|---|---|---|---|
| UT-AUTH-01 | FR-01 | `SignIn.signsInSuccessfully` | `reception1` + correct password | Token issued; role `RECEPTIONIST`; expiry in the future |
| UT-AUTH-02 | FR-02 | `SignIn.storedValueIsAHashNotThePassword` | Stored credential inspected | Value starts `$2`, is 60 chars, never contains the password |
| UT-AUTH-03 | FR-24 | `SignIn.writesAuditEntry` | Successful sign-in | A `LOGIN` row is written to the audit trail |
| UT-AUTH-04 | FR-03 | `SignIn.resetsFailureCounter` | Successful sign-in after failures | Failure counter reset for that staff ID |
| UT-AUTH-05 | NFR-04 | `SignIn.refusesBlankInput` | `""`, `"  "`, `null` | `ValidationException` before the database is touched |
| UT-AUTH-06 | FR-03 | `Refusal.wrongPasswordIsCounted` | Correct user, wrong password | `AuthenticationFailedException`; failure recorded once |
| UT-AUTH-07 | NFR-06 | `Refusal.doesNotRevealWhichFieldWasWrong` | Unknown username | Message is exactly the generic "Invalid username or password" — no user enumeration |
| UT-AUTH-08 | FR-03 | `Refusal.countsDownRemainingAttempts` | 1st failure of 5 allowed | Message contains "4 attempts remaining" |
| UT-AUTH-09 | FR-03 | `Refusal.lockedAccountIsRefusedDistinctly` | Locked account, correct password | `AccountLockedException`, message names the lock |
| UT-AUTH-10 | FR-05 | `Refusal.deactivatedAccountIsRefused` | Inactive account | Generic authentication failure |
| UT-AUTH-11 | FR-04 | `Sessions.tokenResolvesToSession` | Fresh token | Resolves to the correct username and staff ID |
| UT-AUTH-12 | FR-04 | `Sessions.rejectsMissingAndUnknownTokens` | `null`, blank, forged UUID | `SessionExpiredException` in all three cases |
| UT-AUTH-13 | FR-04 | `Sessions.tokenExpires` | `SESSION_MINUTES = -1`, i.e. a window that has already elapsed | `SessionExpiredException`, message says "expired" |
| UT-AUTH-14 | FR-04 | `Sessions.logoutInvalidatesToken` | Sign out, then reuse the token | Refused; `LOGOUT` written to the audit trail |
| UT-AUTH-15 | FR-26 | `Sessions.logoutWithoutTokenDoesNotThrow` | `logout(null)` | Returns quietly — a safe exit never crashes |
| UT-AUTH-16 | FR-04 | `Sessions.tokensAreUnique` | Two different sign-ins | Two different tokens |
| UT-AUTH-17 | FR-05 | `RoleChecks.allowsPermittedRole` | Receptionist, receptionist-or-admin action | Allowed |
| UT-AUTH-18 | FR-05 | `RoleChecks.refusesWrongRole` | Dentist, receptionist-only action | `ForbiddenException` |
| UT-AUTH-19 | FR-05 | `RoleChecks.refusesWithoutAnyToken` | No token; forged token | `SessionExpiredException` — hiding a button in the client is not a control |
| UT-AUTH-20 | FR-05 | `RoleChecks.allowsAdministrator` | Administrator, admin-only action | Allowed, role returned |

### 3.2 Appointments — `AppointmentServiceTest` (34 tests)

| Test ID | Requirement | Test method | Input / condition | Expected outcome |
|---|---|---|---|---|
| UT-APPT-01 | FR-09, FR-08 | `HappyPath.booksSuccessfully` | Valid request, free slot | Appointment stored, status `BOOKED`, number `APT-YYYY-NNNNNN` |
| UT-APPT-02 | FR-09, ASM-07 | `HappyPath.derivesEndTimeFromTreatmentDuration` | 30-min scaling; 90-min root canal | Ends 08:30 and 15:30 respectively — duration comes from the treatment, not the user |
| UT-APPT-03 | FR-20 | `HappyPath.publishesToObservers` | Valid booking | Observer notified exactly once, with the patient's name |
| UT-APPT-04 | FR-20 | `HappyPath.worksWithNoObservers` | No observers registered | Booking still succeeds — listeners are optional |
| UT-APPT-05 | NFR-04 | `Validation.rejectsMissingPatient` | Blank patient number | `ValidationException` |
| UT-APPT-06 | NFR-04 | `Validation.rejectsMissingDentist` | `null` dentist | `ValidationException` |
| UT-APPT-07 | NFR-04 | `Validation.rejectsMissingTreatment` | `null` treatment code | `ValidationException` |
| UT-APPT-08 | NFR-04 | `Validation.rejectsMissingDateOrTime` | Missing date; missing time | `ValidationException` in both cases |
| UT-APPT-09 | FR-07 | `Validation.rejectsUnknownPatient` | `PAT-2026-999999` | `NotFoundException` — a 404, not a 400 |
| UT-APPT-10 | FR-21 | `Validation.rejectsUnknownTreatmentCode` | Code `ZZZ` | `NotFoundException` |
| UT-APPT-11 | FR-12 | `OpeningHours.openingBoundary` | 08:00; 07:45 | Accepted; refused |
| UT-APPT-12 | FR-12 | `OpeningHours.closingBoundary` | 19:30; 19:45 (30-min treatment) | Accepted; refused |
| UT-APPT-13 | FR-12, ASM-06 | `OpeningHours.rejectsClosedDay` | Next Sunday | `ValidationException` naming the closed day |
| UT-APPT-14 | FR-12 | `OpeningHours.rejectsPastDate` | Yesterday | `ValidationException` |
| UT-APPT-15…21 | FR-12, ASM-06 | `OpeningHours.enforcesSlotGranularity` (7 parameterised cases) | 08:00 / 08:15 / 08:30 / 08:45 accepted; 08:05 / 08:10 / 08:20 refused | Only multiples of 15 minutes are accepted |
| UT-APPT-22 | FR-10 | `ClashDetection.refusesOverlap` | 10:30 against an existing 10:00–11:30 | `SlotUnavailableException` |
| UT-APPT-23 | FR-11, ASM-09 | `ClashDetection.offersAlternatives` | Same clash | Exception carries at most 3 free slots, none inside the busy period plus buffer |
| UT-APPT-24 | FR-10, ASM-08 | `ClashDetection.bufferBeforeExistingAppointment` | 09:30 (widens to 10:10); 09:15 (widens to 09:55) | Refused; accepted |
| UT-APPT-25 | FR-10, ASM-08 | `ClashDetection.bufferAfterExistingAppointment` | 11:45 after an 11:30 end | Accepted — clears the 10-minute turnaround |
| UT-APPT-26 | FR-10 | `ClashDetection.differentDentistDoesNotClash` | Same time, Dr. Fernando | Accepted — the rule is per dentist |
| UT-APPT-27 | FR-10 | `ClashDetection.differentDayDoesNotClash` | Same time, next day | Accepted |
| UT-APPT-28 | NFR-05 | `ClashDetection.secondIdenticalBookingIsRefused` | Same slot booked twice in a row | Second attempt refused — the first booking occupies the slot |
| UT-APPT-29 | FR-15 | `StatusTransitions.cancelsWithReason` | Cancel a booked appointment with a reason | Status `CANCELLED`; observers notified |
| UT-APPT-30 | FR-15, FR-24 | `StatusTransitions.refusesCancellationWithoutReason` | Blank reason | `ValidationException`; appointment left untouched |
| UT-APPT-31 | FR-15 | `StatusTransitions.refusesDoubleCancellation` | Cancel twice | Second attempt refused |
| UT-APPT-32 | FR-16, ASM-05 | `StatusTransitions.refusesCompletingACancelledAppointment` | Complete a cancelled appointment | `ValidationException` |
| UT-APPT-33 | FR-16 | `StatusTransitions.completesBookedAppointment` | Complete a booked appointment | Status `COMPLETED` — the precondition for billing |
| UT-APPT-34 | FR-13 | `StatusTransitions.unknownAppointmentNumberIsNotFound` | `APT-2026-999999` | `NotFoundException` |

### 3.3 Billing — `BillingFacadeTest` (15 tests)

| Test ID | Requirement | Test method | Input / condition | Expected outcome |
|---|---|---|---|---|
| UT-BILL-01 | FR-17 | `Preconditions.unknownAppointment` | Unknown appointment number | `NotFoundException` |
| UT-BILL-02 | FR-18, ASM-05 | `Preconditions.refusesBookedAppointment` | Status `BOOKED` | `BillingException` naming the precondition |
| UT-BILL-03 | FR-18, ASM-05 | `Preconditions.refusesCancelledAppointment` | Status `CANCELLED` | `BillingException` |
| UT-BILL-04 | FR-18 | `Preconditions.refusesSecondBill` | Bill the same appointment twice | Second attempt refused, message says "already" |
| UT-BILL-05 | ASM-12 | `DiscountSelection.ordinaryPatientGetsNoDiscount` | Age 40, no flags, 0 visits | Amount `0.00`, label "No discount" — the **Null Object**, never `null` |
| UT-BILL-06 | ASM-12 | `DiscountSelection.seniorGetsTenPercent` | Age exactly 65, subtotal 10,000 | `1000.00`, label contains "Senior" |
| UT-BILL-07 | ASM-12 | `DiscountSelection.sixtyFourIsNotSenior` | Age 64 | `0.00` — the boundary is exact |
| UT-BILL-08 | ASM-12 | `DiscountSelection.fifthVisitEarnsLoyalty` | 4 previous visits | `500.00`, label "Returning patient 5%" |
| UT-BILL-09 | ASM-12 | `DiscountSelection.fourthVisitEarnsNothing` | 3 previous visits | `0.00` |
| UT-BILL-10 | ASM-12 | `DiscountSelection.staffFamilyGetsFifteenPercent` | Staff family flag | `1500.00` |
| UT-BILL-11 | ASM-12 | `DiscountSelection.mostGenerousRuleWins` | Senior **and** loyal **and** staff family | `1500.00` only — rules do not stack |
| UT-BILL-12 | FR-17, FR-19 | `BillContents.returnsStoredBill` | Bill a completed appointment | Bill read back from the data tier; number `BIL-YYYY-NNNNNN`; status `UNPAID` |
| UT-BILL-13 | FR-19 | `BillContents.unknownBillNumber` | Unknown bill number | `NotFoundException` |
| UT-BILL-14 | NFR-04 | `DatabaseRuleSurfaced.translatesDataAccessException` | Stored procedure signals `SQLSTATE 45000` | The procedure's own business message reaches the user, not a stack trace |
| UT-BILL-15 | NFR-04 | `DatabaseRuleSurfaced.handlesMessagelessFailure` | Failure with no message | Falls back to "The bill could not be generated" |

### 3.4 Administration — `AdministrationServiceTest` (20 tests)

| Test ID | Requirement | Coverage |
|---|---|---|
| UT-ADM-01…10 | FR-21 | Treatments: list includes withdrawn ones; create with code normalisation; duplicate code refused; standing price updated and persisted; withdraw without delete; unknown code 404; negative price refused; duration boundaries 5 and 480 accepted, 4 and 481 refused; blank code or name refused |
| UT-ADM-11…18 | FR-22, FR-02, FR-24 | Staff: receptionist created with a BCrypt hash; creating a dentist writes the dentist row too; dentist without registration number or fee refused; duplicate username refused; password under 8 characters refused; unknown role refused; creation audited; account deactivated rather than deleted |
| UT-ADM-19…21 | FR-03, FR-24 | Unlock: the lock **and** the counter are cleared; the unlock is audited; unlocking an unknown account is a 404 |

### 3.5 Domain rules — `AppointmentOverlapTest` (12 tests) and `DiscountStrategyTest` (18 tests)

Both were written **test-first** (section 1.3) and run with no collaborators at
all — not even a test double.

| Test ID | Requirement | Coverage |
|---|---|---|
| UT-DOM-01…04 | FR-10, ASM-08 | Overlapping ranges: identical times, contains, starts inside, ends inside |
| UT-DOM-05…08 | ASM-08 | Buffer: clears exactly, inside the buffer, clears before, zero buffer |
| UT-DOM-09…12 | FR-10 | Different key: other dentist, other day, cancelled frees the slot, later the same day |
| UT-PAT-01…07 | ASM-12 | Discount arithmetic: senior, loyalty, staff, none, rounding, zero subtotal, `null` subtotal |
| UT-PAT-08…13 | ASM-12 | Boundaries: age 64 / 65 / 66; 3 / 4 / 5 previous visits (parameterised) |
| UT-PAT-14…18 | ASM-12 | Precedence: staff beats senior, senior beats loyalty, all three, none, `null` patient |

---

## 4. Traceability matrix

Every requirement in the register traces forward to a use case, a class and the
tests that prove it. Blank test cells are requirements proved by manual test or
by the data tier, not by JUnit — stated rather than hidden.

| Req | Description | Use case | Class(es) under test | Tests |
|---|---|---|---|---|
| FR-01 | Staff log in | UC-01 | `AuthenticationService` | UT-AUTH-01 |
| FR-02 | Passwords stored hashed | UC-01 | `AuthenticationService` (BCrypt) | UT-AUTH-02, DB-08 |
| FR-03 | Lock out after 5 failures | UC-01 | `AuthenticationService` | UT-AUTH-04, 06, 08, 09 |
| FR-04 | Session with idle timeout | UC-02 | `AuthenticationService` | UT-AUTH-11…16 |
| FR-05 | Role-restricted functions | UC-03 | `AuthenticationService`, `ClinicController` | UT-AUTH-10, 17…20 |
| FR-06 | Register a new patient | UC-05 | `JdbcClinicRepository`, `patient` table | DB-01, MAN-03 |
| FR-07 | Look up an existing patient | UC-06 | `AppointmentService` | UT-APPT-09, MAN-04 |
| FR-08 | Generated appointment number | UC-07 | `trg_appointment_number` | UT-APPT-01, DB-04 |
| FR-09 | Book an appointment | UC-07 | `AppointmentService` | UT-APPT-01, 02 |
| FR-10 | Reject a dentist overlap | UC-07 | `Appointment`, `AppointmentService`, `trg_appointment_no_overlap` | UT-DOM-01…12, UT-APPT-22, 24…27, DB-02 |
| FR-11 | Suggest the next 3 free slots | UC-07 | `AppointmentService.nextFreeSlots` | UT-APPT-23 |
| FR-12 | Reject out-of-hours and past dates | UC-07 | `AppointmentService` | UT-APPT-11…21 |
| FR-13 | Search by appointment number | UC-10 | `AppointmentService`, `vw_appointment_detail` | UT-APPT-34, MAN-05 |
| FR-14 | Display full appointment detail | UC-10 | `vw_appointment_detail` | MAN-05 |
| FR-15 | Cancel / reschedule | UC-11 | `AppointmentService`, `trg_appointment_overlap_upd` | UT-APPT-29…31 |
| FR-16 | Mark completed / no-show | UC-13 | `AppointmentService` | UT-APPT-32, 33 |
| FR-17 | Calculate a bill | UC-14 | `BillingFacade`, `sp_generate_bill` | UT-BILL-01, 12, DB-05 |
| FR-18 | Bill only completed appointments | UC-14 | `BillingFacade`, `sp_generate_bill` | UT-BILL-02…04, DB-06 |
| FR-19 | Print a receipt | UC-15 | `app.css` `@media print`, `BillingFrame` | UT-BILL-12, 13, MAN-07 |
| FR-20 | Appointment notifications | UC-08 | `NotificationObserver` (**stub — logs what it would send**) | UT-APPT-03, 04 |
| FR-21 | Maintain treatments and prices | UC-16 | `TreatmentTypeRepository`, `treatment_type` | UT-APPT-10, MAN-08 |
| FR-22 | Maintain staff and dentists | UC-17 | `staff`, `dentist` tables | DB-01, MAN-09 |
| FR-23 | Management reports | UC-18 | `ReportService`, 5 views | DB-01, MAN-06 |
| FR-24 | Audit log | UC-19 | `AuditLogObserver`, `audit_entry` | UT-AUTH-03, 14, UT-APPT-30 |
| FR-25 | Help screen | UC-20 | Help panel / `HelpFrame` | MAN-10 |
| FR-26 | Safe exit | UC-21 | `AuthenticationService.logout` | UT-AUTH-15, MAN-11 |
| NFR-01 | Three physical tiers | — | Browser / Swing → Spring Boot :8080 → MySQL :3306 | The whole service suite runs with no database at all — see 1.1 |
| NFR-02 | Patterns from all three families, evaluated | — | `pattern/` package | UT-BILL-05…11, UT-APPT-03, 04, UT-PAT-01…18 |
| NFR-03 | 3NF schema with referential integrity | — | `01_schema.sql` | DB-01 |
| NFR-04 | Client and server validation, server authoritative | — | `AppointmentService`, `GlobalExceptionHandler` | UT-APPT-05…08, UT-BILL-14, 15, UT-AUTH-05 |
| NFR-05 | Concurrent bookings cannot both succeed | — | Service check **and** `trg_appointment_no_overlap` | UT-APPT-28, DB-02, DB-03 |
| NFR-06 | Role-restricted patient data, no PII in logs | — | `AuthenticationService`, `AuditLogObserver` | UT-AUTH-07, 19 |
| NFR-07 | Automated tests on every push | — | `.github/workflows/ci.yml` | The CI run for this commit |
| NFR-08 | Search under 2 s at 10,000 records | — | Indexes in `01_schema.sql` | MAN-12 |
| NFR-09 | Operable by non-technical staff | — | Browser UI | MAN-13 |

---

## 5. Coverage

JaCoCo runs on every build and every CI push.

```
cd clinic-service
mvn -B verify
```

Report: `clinic-service/target/site/jacoco/index.html`. In CI it is also
uploaded as the **jacoco-coverage-report** artifact, and a per-package summary
is printed to the workflow run's summary page.

**How to read these figures honestly.** Coverage measures what was *executed*,
not what was *proved*. A single test that calls every method and asserts nothing
scores 100%. So the headline 47.5% is the least interesting number on this page,
and quoting it alone — in either direction — would misrepresent the suite.

Two things matter more:

1. **Branch coverage on the packages that hold decisions.** A branch is an
   `if`, a boundary, a rule that can go either way. `service` is at **91.3%**
   and `pattern` at **94.4%** — nearly every decision the clinic's rules can
   make is exercised by a test.
2. **Where the uncovered lines are.** They are concentrated in exactly the
   places that *should* be uncovered by unit tests.

Measured run, `mvn -B verify`. **Note on which metric is quoted:** the table
below reports **line** coverage, taken from `jacoco.csv`. The first `Cov.`
column on JaCoCo's own HTML page is **instruction** coverage, which is a
slightly different number for the same code — `pattern`, for example, is 65% by
instruction and 69% by line. Both are given here so the figures in this document
and the figures in the screenshot can be reconciled.

| Package | Line cov. | Instruction cov. | Branch cov. | Reading |
|---|---:|---:|---:|---|
| `lk.sunrise.clinic.service` | **87.2%** | 87% | **91.3%** | The business logic. This is the figure that matters, and it is the highest in the project. |
| `lk.sunrise.clinic.pattern` | 69.0% | 65% | **94.4%** | Every discount *decision* is covered. The uncovered lines are `NotificationObserver` and `AuditLogObserver` bodies — logging, not logic. |
| `lk.sunrise.clinic.exception` | 100% | 100% | n/a | Every domain exception is thrown by a test — no dead exception types. |
| `lk.sunrise.clinic.domain` | 45.2% | 55% | 77.3% | `Appointment.overlapsWith()` is fully covered; the shortfall is unused accessors on `Patient` and `TreatmentType`. Line coverage penalises getters; branch coverage shows the rule itself is proved. |
| `lk.sunrise.clinic.dto` | 63.6% | 79% | n/a | Java records — generated accessors. |
| `lk.sunrise.clinic.repository` | 6.7% | 5% | 20.0% | **Deliberately low.** This layer is SQL. Executing it in a unit test would prove only that JDBC works; it is proved instead against a real MySQL 8 server in section 6. |
| `lk.sunrise.clinic.api` | 0.0% | 0% | 0.0% | **Deliberately zero.** Controllers delegate and do not decide. Covering them would test Spring's request mapping, not the clinic's rules; they are proved by the manual end-to-end tests in section 7. |
| **Overall** | **47.5%** | 47% | **73.8%** | Dominated by the two layers that are intentionally untested here. |

Stating the gap and defending it is worth more than raising the headline figure
by adding tests that assert nothing. If the number needed to rise, the honest
way would be `@WebMvcTest` slice tests over the controllers — noted in the
report as future work rather than added as padding.

---

## 6. Data-tier tests

These prove the rules that live in MySQL. They cannot be proved in Java,
because the point of them is that they hold *inside the transaction*.

Run locally:

```
mysql -u root -p < db/08_healthcheck.sql
mysql -u root -p --force < db/06_verify.sql
```

`--force` is required for `06_verify.sql`: several checks are **supposed** to
error, and that error is the pass condition. In CI the same assertions run as
explicit shell steps in the `database-rules` job, which fails the build if a
rejection does not happen.

| Test ID | Requirement | What is done | Expected outcome |
|---|---|---|---|
| DB-01 | NFR-03, FR-06, FR-22, FR-23 | `08_healthcheck.sql` | 14 rows, every one `PASS`: 12 tables, 5 views, 4 triggers, 2 routines, 9 foreign keys, seed data present |
| DB-02 | FR-10, NFR-05 | Insert 14:15–14:45 against an existing 14:00–14:30 | `SQLSTATE 45000` — rejected by `trg_appointment_no_overlap` |
| DB-03 | ASM-08, NFR-05 | Insert 14:35–15:05 — no overlap, but inside the 10-minute buffer | `SQLSTATE 45000` — rejected |
| DB-04 | FR-08, FR-09 | Insert 14:45–15:15 — clears the buffer | Accepted; `appointment_no` generated as `APT-YYYY-NNNNNN` |
| DB-05 | FR-17, ASM-10, ASM-12 | `CALL sp_generate_bill` for the senior citizen: 3,000 fee + 28,000 treatments − 2,800 discount | `total_payable = 28200.00`, bill lines written in the same transaction |
| DB-06 | FR-18 | Call `sp_generate_bill` again for the same appointment | Rejected — an appointment may be billed once only |
| DB-07 | ASM-05 | Call `sp_generate_bill` for a `BOOKED` appointment | Rejected — only completed appointments are billable |
| DB-08 | FR-02 | Inspect `staff.password_hash` | All 5 rows are BCrypt hashes (`$2…`), no plain text anywhere |

---

## 7. Manual test cases — presentation tier

Automated tests cannot prove that a receptionist can use the screen. These are
run by hand against the running system, and the evidence is a screenshot of
**your own** system taken at the moment of the test.

> **Evidence must be real.** Every screenshot in the report has to come from
> your own running application against your own MySQL server. A fabricated or
> mocked-up screenshot is Fabrication of data under the assessment
> regulations. Where a feature is a stub — the notifier logs what it *would*
> send rather than sending it — say so in the caption.

| Test ID | Requirement | Steps | Expected result |
|---|---|---|---|
| MAN-01 | FR-01, NFR-09 | Open `http://localhost:8080`, sign in as a receptionist | Dashboard opens showing only the receptionist's tabs |
| MAN-02 | FR-05 | Sign in as a dentist | Billing and admin tabs are absent; calling the endpoint directly still returns 403 |
| MAN-03 | FR-06 | Register a new patient | `PAT-YYYY-NNNNNN` issued and shown |
| MAN-04 | FR-07 | Search for that patient by name | Found; details displayed |
| MAN-05 | FR-13, FR-14 | Search an appointment by number | Patient, dentist, treatments, times and status all shown |
| MAN-06 | FR-23 | Open each of the five reports | Each returns rows and is readable |
| MAN-07 | FR-19 | Generate a bill, then print (Ctrl+P) | Print preview shows only the receipt — navigation and buttons are stripped |
| MAN-08 | FR-21 | Change a treatment price as Administrator | New price applies to the next booking; existing appointments keep their snapshot price |
| MAN-09 | FR-22 | Add a dentist as Administrator | Appears in the booking screen's dentist list |
| MAN-10 | FR-25 | Open Help | Explains each function in plain language |
| MAN-11 | FR-26 | Sign out, then press Back | Returns to the sign-in screen; the session cannot be resumed |
| MAN-12 | NFR-08 | Search with the seeded data | Result returns in well under 2 seconds |
| MAN-13 | NFR-09, FR-11 | Attempt a clashing booking | The refusal names the conflict **and** offers three alternative times |
| MAN-14 | — | Toggle light / dark / system appearance | All screens remain legible; the choice survives a page reload |

---

## 8. Defects found and fixed during testing

Recording the defects testing actually caught is stronger evidence that the
tests do something than any coverage figure.

| # | Found by | Defect | Fix |
|---|---|---|---|
| 1 | `05_views.sql` on MySQL 8 | Three views failed `ONLY_FULL_GROUP_BY`, which MySQL 8 enables by default and MariaDB does not | Every non-aggregated column added to `GROUP BY`; all scripts re-run under MySQL 8's exact default `sql_mode` |
| 2 | `01_schema.sql` on MySQL 8 | `last_value` became a reserved word in MySQL 8 (window functions) | Renamed to `last_issued`; all 72 identifiers checked against the MySQL 8 reserved-word list |
| 3 | Browser rendering | The sign-in screen rendered on top of the whole application: `.login-shell { display: grid }` overrode the `hidden` attribute | `[hidden] { display: none !important; }` — caught only by opening the page, never by reading the code |
| 4 | Browser rendering, dark mode | Toast messages were unreadable: `background: var(--ink)` inverts to near-white behind white text | Dedicated `--toast-bg` / `--toast-fg` tokens defined per theme |
| 5 | `AppointmentServiceTest` | The buffer boundary case was first written at 09:35, which is not on the 15-minute grain, so it failed validation before ever reaching the clash rule — the test was not testing what it claimed | Boundary moved to the adjacent legal slots, 09:30 (refused) and 09:15 (accepted) |
| 6 | `AuthenticationServiceTest` on Windows | **A flaky test.** `tokenExpires` set the session window to zero minutes, stamping the expiry at exactly "now". Whether the subsequent check saw that as past depended on the platform clock's resolution: it passed consistently on Linux, but on Windows, where `LocalDateTime.now()` can return the same value on two consecutive calls, it failed intermittently. The suite was green on one operating system and red on another for the same commit | The window is now set to a value that has *already* elapsed (`-1`), so the token is stamped a minute in the past and the race cannot occur. A `Thread.sleep` was rejected: it would have hidden the race behind a delay rather than removing it, and slow tests are the first thing a team stops running |

> Add any defect your own testing turns up. A defect you found and fixed is
> worth more marks than a suite that never went red.


## A.9 Use case specifications

Section 3.1 states that authentication is a *precondition* of every use case
rather than an `<<include>>` on each. These are the specifications in which it
is recorded. Three are given in full; the remaining eighteen follow the same
shape and are in the repository.

**UC-01 Log In** · Actor: Staff User · Precondition: the user holds an active,
unlocked account · Main flow: the user supplies a username and password; the
system compares the password against the stored BCrypt hash, clears the failure
counter, issues a token with a twenty-minute idle expiry and writes a LOGIN
audit entry · Alternate: an unknown username or wrong password returns the same
generic message and increments the counter; five failures lock the account ·
Postcondition: a live session exists, or the attempt is recorded ·
Requirements: FR-01, FR-02, FR-03, FR-04, FR-24.

**UC-07 Book Appointment** · Actor: Receptionist · Preconditions: signed in as
Receptionist or Administrator; the patient exists; the treatment is active ·
Main flow: the receptionist selects patient, dentist, treatment, date and start
time; the system derives the end time from the treatment's duration, checks the
opening hours and the clash rule, inserts the appointment and publishes the
event to its observers · Alternate: a clash returns HTTP 409 with the next three
free slots (UC-09); the database trigger rejects any booking that wins a race
between the check and the insert · Postcondition: the appointment exists with a
generated number, the patient has a confirmation email and an audit row exists ·
Requirements: FR-08 to FR-12, FR-20, FR-24, NFR-05.

**UC-16 Maintain Treatments** · Actor: Administrator · Precondition: signed in
as Administrator · Main flow: the administrator lists treatments including
withdrawn ones, and creates one or edits its name, price, duration and
availability; the system validates the code, price and duration before writing ·
Alternate: a duplicate code or an out-of-range duration is refused with a
message naming the rule · Postcondition: the standing price is changed;
appointments already booked keep the price snapshot taken at booking time ·
Requirements: FR-21, NFR-04.
