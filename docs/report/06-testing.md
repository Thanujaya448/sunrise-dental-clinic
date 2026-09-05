# 6. Testing

The suite contains **99 automated tests**, all passing, run by Maven and re-run
by GitHub Actions on every push (Figure 25). The exhaustive plan is Appendix A;
this section gives the rationale, the derivation, the traceability and the
result.

## 6.1 Testing at the level that can prove the thing

Each tier is tested by the technique that can prove it, and nothing twice. Pure
rules are tested with no collaborators; the business tier with the repository
replaced by a test double; the rules living in MySQL against a real MySQL 8
server, because the point of a trigger is that it holds *inside the
transaction*; the presentation tier manually, with recorded evidence.

## 6.2 Test-driven development

Two features were written test-first, with red and green as separate commits so
the sequence is visible in the history rather than claimed: `bb1ac3d` →
`94d7a0b` for the clash rule, `ffa86d5` → `8b73b14` for the discount
strategies (Figure 29). Checking out a red commit and running `mvn test`
reproduces the failure; the green commit makes the same tests pass without
altering them.

TDD changed the design, which is its value rather than the tests it leaves
behind (Beck, 2003). Writing `AppointmentOverlapTest` first forced the clash
rule out of the service and onto `Appointment.overlapsWith()`, where it runs
without a database. Writing `DiscountStrategyTest` first made the four rules
separate classes, because a branch of an `if`/`else` chain cannot be tested in
isolation.

## 6.3 Deriving the test data

Test data was derived from the specification by two standard techniques rather
than invented (Myers *et al.*, 2011).

**Equivalence partitioning** divides each input into classes the system should
treat identically and takes one value from each; testing 09:00 and 09:15 proves
nothing new because they fall in the same class. Partitions for the booking
request and for credentials are tabulated in Appendix A.

**Boundary value analysis** then tests each edge at the value itself and at the
adjacent value either side, because every failure the clinic reported happens
at an edge.

| Rule | Just below | On the boundary | Just above |
|---|---|---|---|
| Opening time 08:00 | 07:45 refused | **08:00 accepted** | 08:15 accepted |
| Closing 20:00, 30-min treatment | 19:15 accepted | **19:30 accepted** (ends 20:00) | 19:45 refused |
| 10-min buffer vs a 10:00–11:30 booking | 09:15 accepted | **09:30 refused** | 11:45 accepted |
| Senior discount, age 65 | 64 → none | **65 → 10%** | 66 → 10% |
| Loyalty, fifth visit | 3 previous → none | **4 previous → 5%** | 5 previous → 5% |

## 6.4 A hand-written test double rather than a mocking framework

`spring-boot-starter-test` supplies Mockito, so mocks were available.
`InMemoryClinicRepository` was written by hand instead. A mock verifies that a
**method was called**; the double holds state, so a test asserts on the
**consequence** — the appointment now exists, the status really changed, the
audit trail records it. Fowler (2007) frames this as behaviour versus state
verification: the former couples a test to *how* the code is written, so
refactoring breaks tests that should not care. Meszaros (2007) adds that a
readable fake documents the contract, which fifteen
`when(...).thenReturn(...)` lines do not.

The double deliberately does **not** re-implement the overlap trigger or
`sp_generate_bill`. Re-implementing them in Java would only test the copy;
those rules are proved against a real server instead (Figures 9 and 10).

## 6.5 Traceability — how each requirement is met and proved

| Requirements | Met by | Proved by |
|---|---|---|
| FR-01…05 sign-in, lockout, session, roles | `AuthenticationService`, BCrypt, `staff.locked` | UT-AUTH-01…20; Figs 11, 21 |
| FR-06, 07 register and find a patient | `patient`, `trg_patient_number` | DB-01; UT-APPT-09; MAN-03 |
| FR-08, 09 numbering and booking | `trg_appointment_number`, `book()` | UT-APPT-01, 02; DB-04 |
| FR-10, 12 clash rule and opening hours | `overlapsWith()`, `assertWithinOpeningHours()`, `trg_appointment_no_overlap` | UT-DOM-01…12; UT-APPT-11…27; DB-02, 03; Fig 9 |
| FR-11 alternative slots | `nextFreeSlots()` | UT-APPT-23; Fig 14 |
| FR-13, 14 search and display | `vw_appointment_detail` | UT-APPT-34; Fig 15; MAN-05 |
| FR-15, 16 cancel, complete, no-show | `cancel()`, `markCompleted()`, `trg_appointment_overlap_upd` | UT-APPT-29…33 |
| FR-17…19 billing and receipt | `BillingFacade`, `sp_generate_bill`, `@media print` | UT-BILL-01…15; DB-05, 06; Figs 10, 16, 17 |
| FR-20, 24 notification and audit | `NotificationObserver` (SMTP), `AuditLogObserver` | UT-APPT-03, 04; UT-AUTH-03, 14; Figs 23, 24 |
| FR-21…23 maintenance and reports | `treatment_type`, `staff`, `dentist`, five views | UT-APPT-10; DB-01; Fig 18; MAN-06, 08, 09 |
| FR-25, 26 help and safe exit | help panel, `logout()` | UT-AUTH-15; MAN-10, 11 |
| NFR-01, 05 tiers and concurrency | HTTP boundary; service check **and** trigger | suite runs with no database; UT-APPT-28; DB-02 |
| NFR-02 patterns, all three families | `pattern/` package | UT-BILL-05…11; UT-PAT-01…18 |
| NFR-03, 08 schema and search speed | 3NF schema with indexes | DB-01; Fig 7; MAN-12 |
| NFR-04, 06 validation and data restriction | service validation, `requireRole`, audit by reference | UT-APPT-05…08; UT-AUTH-07, 19; Fig 21 |
| NFR-07, 09 automation and usability | `ci.yml`; browser UI | Figs 27, 28; MAN-13; Figs 11–14 |

Every requirement traces forward to the class or database object that meets it
and to the test or figure that proves it. Per-requirement rows, the full 99-test
plan and the manual test cases are in Appendix A.

## 6.6 What the testing found, and coverage read honestly

Six defects are recorded in Appendix A; two were found by testing rather than
review. A boundary case was first written at 09:35, off the fifteen-minute grid,
so it failed validation *before* reaching the clash rule — the test passed while
testing nothing it claimed. More seriously, `tokenExpires` set the session
window to zero minutes, stamping the expiry at exactly "now"; whether the check
saw that as past depended on the platform clock, so it passed on Linux and in CI
and failed intermittently on Windows. The fix sets a window that has already
elapsed. `Thread.sleep` was rejected: it hides the race behind a delay and slows
every future run. A test whose result depends on clock granularity is worse than
no test, because it teaches a team to ignore red builds.

Line coverage is 47.5% overall and branch coverage 73.8% (Figure 26). The
headline is the least interesting number available, because coverage measures
what was *executed*, not what was *proved*. Two things matter more: branch
coverage where the decisions are — `service` 91.3%, `pattern` 94.4% — and where
the uncovered code sits. It sits in `repository` (5%) and `api` (0%), which is
correct: one is SQL, proved against real MySQL instead; the other delegates
rather than decides, so covering it would test Spring's request mapping.
Raising the headline honestly would mean `@WebMvcTest` slice tests, recorded in
§8 as future work rather than added as padding.
