# 6. Testing

The suite contains **119 automated tests**, all passing, run by Maven and re-run
by CI on every push (Figure 27). The full plan is Appendix A; this section gives
the rationale, derivation, traceability and result.

## 6.1 Testing at the level that can prove the thing

Each tier is tested by the technique that can prove it, and nothing twice. Pure
rules with no collaborators; the business tier with the repository replaced by a
test double; the MySQL rules against a real MySQL 8 server, because a trigger's
whole point is that it holds *inside the transaction*; the presentation tier
manually, with recorded evidence.

## 6.2 Test-driven development

Two features were written test-first, with red and green as separate commits so
the sequence is visible rather than claimed: `bb1ac3d` → `94d7a0b` for the clash
rule, `ffa86d5` → `8b73b14` for the discounts (Figure 31). Checking out a red
commit reproduces the failure; the green commit makes the same tests pass
unaltered.

TDD changed the design, which is its value rather than the tests it leaves
behind (Beck, 2003). Writing `AppointmentOverlapTest` first forced the clash
rule onto `Appointment.overlapsWith()`, where it runs without a database, and
writing `DiscountStrategyTest` first made the four rules separate classes,
because a branch of an `if`/`else` chain cannot be tested alone.

## 6.3 Deriving the test data

Test data was derived rather than invented (Myers *et al.*, 2011).
**Equivalence partitioning** divides each input into classes the system should
treat identically and takes one value from each; 09:00 and 09:15 prove nothing
new because they share a class. **Boundary value analysis** then tests each edge
at the value and at the adjacent value either side, because every failure the
clinic reported is at an edge: opening and closing times, the fifteen-minute
grain, the ten-minute buffer, age 65, the fifth visit. Both are tabulated in
Appendix A.

## 6.4 A hand-written test double rather than a mocking framework

Mockito was available; `InMemoryClinicRepository` was written by hand instead. A
mock verifies a **method was called**; the double holds state, so a test asserts
the **consequence** — the appointment exists, the status changed, the audit trail
records it. Fowler (2007) frames this as behaviour versus state
verification: the former couples a test to *how* the code is written, so
refactoring breaks tests that should not care. Meszaros (2007) adds that a
readable fake documents the contract, which fifteen
`when(...).thenReturn(...)` lines do not. The double deliberately does **not**
re-implement the trigger or `sp_generate_bill`: that would test the copy, and
those rules are proved against a real server instead (Figures 9 and 10).

## 6.5 Traceability — how each requirement is met and proved

| Requirements | Met by | Proved by |
|---|---|---|
| FR-01…05 sign-in, lockout, session, roles | `AuthenticationService`, BCrypt, `staff.locked` | UT-AUTH-01…20; Figs 11, 21 |
| FR-06, 07 register and find a patient | `patient`, `trg_patient_number` | DB-01; UT-APPT-09 |
| FR-08, 09 numbering and booking | `trg_appointment_number`, `book()` | UT-APPT-01, 02; DB-04 |
| FR-10, 12 clash rule and opening hours | `overlapsWith()`, `trg_appointment_no_overlap` | UT-DOM-01…12; UT-APPT-11…27; DB-02, 03; Fig 9 |
| FR-11 alternative slots | `nextFreeSlots()` | UT-APPT-23; Fig 14 |
| FR-13, 14 search and display | `vw_appointment_detail` | UT-APPT-34; Fig 15 |
| FR-15, 16 cancel, complete, no-show | `cancel()`, `trg_appointment_overlap_upd` | UT-APPT-29…33 |
| FR-17…19 billing and receipt | `BillingFacade`, `sp_generate_bill`, print CSS | UT-BILL-01…15; DB-05, 06; Figs 10, 17 |
| FR-20, 24 notification and audit | `NotificationObserver`, `AuditLogObserver` | UT-APPT-03, 04; Figs 25, 26 |
| FR-21, 22 maintain treatments and staff | `AdministrationService`, admin endpoints | UT-ADM-01…20; Figs 22, 23 |
| FR-23 management reports | `ReportService`, five views | DB-01; Fig 18 |
| FR-25, 26 help and safe exit | help panel, `logout()` | UT-AUTH-15; MAN-10, 11 |
| NFR-01, 05 tiers and concurrency | HTTP boundary; check **and** trigger | suite runs with no database; DB-02 |
| NFR-02 patterns, all three families | `pattern/` package | UT-BILL-05…11; UT-PAT-01…18 |
| NFR-03, 08 schema and search speed | 3NF schema, indexes | DB-01; Fig 7; MAN-12 |
| NFR-04, 06 validation and data restriction | `requireRole`, audit by reference | UT-APPT-05…08; Fig 21 |
| NFR-07, 09 automation and usability | `ci.yml`; browser UI | Figs 29, 30; Figs 11–14 |

Per-requirement rows and the full plan are in Appendix A.

## 6.6 What the testing found, and coverage read honestly

Six defects are in Appendix A; two were found by testing rather than review. A
boundary case was first written at 09:35, off the fifteen-minute grid, so it
failed validation *before* reaching the clash rule — passing while testing
nothing it claimed. More seriously, `tokenExpires` set the session window to
zero minutes, stamping the expiry at exactly "now"; whether the check saw that
as past depended on the platform clock, so it passed on Linux and in CI and
failed intermittently on Windows. The fix sets a window already elapsed.
`Thread.sleep` was rejected: it hides the race behind a delay and slows every
run. A test whose result depends on clock granularity is worse than no test,
because it teaches a team to ignore red builds.

Line coverage is 47.5% and branch coverage 73.8% (Figure 28). The headline is
the least interesting number available, because coverage measures what was
*executed*, not what was *proved*. Two things matter more: branch coverage where
the decisions are — `service` 91.3%, `pattern` 94.4% — and where the uncovered
code sits. It sits in `repository` (5%) and `api` (0%), correctly: one is SQL,
proved against real MySQL; the other delegates rather than decides, so covering
it would test Spring's request mapping. Raising the headline honestly means
`@WebMvcTest` slice tests, recorded in §8 as future work rather than padding.
