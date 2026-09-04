# 6. Testing

*(~790 words — Task C)*

The suite contains **99 automated tests**, all passing, run by Maven and
re-run by GitHub Actions on every push. The full test plan, derived test data
and traceability matrix are in `docs/test-plan.md`; this section explains the
decisions behind them.

## 6.1 Testing at the level that can prove the thing

Each tier is tested by the technique that can prove it, and nothing is proved
twice. Pure rules are tested with no collaborators; the business tier with the
repository replaced by a test double; the rules living in MySQL against a real
MySQL 8 server, because the point of a trigger is that it holds *inside the
transaction*, which no Java test can demonstrate; the presentation tier
manually, with recorded evidence.

## 6.2 Test-driven development

Two features were written test-first, and the red and green steps are separate
commits so the sequence is visible in the Git history rather than merely
claimed: `bb1ac3d` → `94d7a0b` for the clash rule, and `ffa86d5` → `8b73b14`
for the discount strategies. Checking out a red commit and running `mvn test`
reproduces the failure; the green commit that follows makes the same tests pass
without altering them.

TDD changed the design, which is its actual value rather than the tests it
leaves behind (Beck, 2003). Writing `AppointmentOverlapTest` first forced the
clash rule out of the service and onto `Appointment.overlapsWith()`, where it
could be exercised without a database. Writing `DiscountStrategyTest` first
made the four rules separate classes, because testing a branch of an `if`/`else`
chain in isolation is not possible.

## 6.3 Deriving the test data

Test data was derived from the specification by two standard techniques rather
than invented (Myers *et al.*, 2011).

**Equivalence partitioning** divides each input into classes the system should
treat identically and takes one value from each; testing 09:00 and 09:15 proves
nothing new, because they fall in the same class. The partitions for the booking
request and for credentials are tabulated in the test plan.

**Boundary value analysis** then tested each edge at the value itself and at the
adjacent value on each side, because every failure the clinic reported happens
at an edge. Opening at 08:00 is accepted and 07:45 refused; a thirty-minute
treatment may start at 19:30 but not 19:45; a patient aged 65 receives the
senior discount and one aged 64 does not; a fifth visit earns loyalty and a
fourth does not. Against an existing 10:00–11:30 booking with a ten-minute
buffer, 09:30 is refused and the adjacent slot 09:15 is accepted.

## 6.4 A hand-written test double rather than a mocking framework

`spring-boot-starter-test` supplies Mockito, so mocks were available.
`InMemoryClinicRepository` was written by hand instead.

A mock verifies that a **method was called**; the double holds state, so a test
asserts on the **consequence** — the appointment now exists, the status really
changed, the audit trail records it. Fowler (2007) frames this as behaviour
versus state verification: the former couples a test to *how* the code is
written, so refactoring breaks tests that should not care; the latter couples
only to what the code achieves. Meszaros (2007) adds that a readable fake
documents the contract, which fifteen `when(...).thenReturn(...)` lines do not.

The double deliberately does **not** re-implement the overlap trigger or
`sp_generate_bill`. Re-implementing them in Java would only test the copy. Those
rules are proved against a real server instead.

## 6.5 What the testing actually found

Six defects are recorded in the test plan. Two are worth naming here because
they were found by testing rather than by review.

A boundary case was originally written at 09:35, which is not on the
fifteen-minute grid, so it failed validation *before* ever reaching the clash
rule — the test passed while testing nothing it claimed to. It now uses 09:30
and 09:15, the adjacent legal slots.

More significantly, `tokenExpires` set the session window to zero minutes,
stamping the expiry at exactly "now". Whether the subsequent check saw that as
past depended on the platform clock's resolution: it passed consistently on
Linux and in CI, and failed intermittently on Windows. The fix sets a window
that has already elapsed, removing the race. A `Thread.sleep` was rejected
because it would hide the race behind a delay and slow every future run. A test
whose result depends on clock granularity is worse than no test, because it
teaches a team to ignore red builds.

## 6.6 Coverage, read honestly

Line coverage is 47.5% overall and branch coverage 73.8%. The headline figure is
the least interesting number available, because coverage measures what was
*executed*, not what was *proved*. Two things matter more: branch coverage where
the decisions are — `service` at 91.3%, `pattern` at 94.4% — and where the
uncovered code sits. It sits in `repository` (5%) and `api` (0%), which is
correct: one is SQL, proved against real MySQL instead; the other delegates
rather than decides, so covering it would test Spring's request mapping. Raising
the headline honestly would mean `@WebMvcTest` slice tests, recorded in §8 as
future work rather than added as padding.
