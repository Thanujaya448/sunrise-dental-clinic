# 4. Three-tier distributed architecture

*(~530 words — Task B)*

The system runs as three separate operating-system processes: a client, a
Spring Boot REST service on port 8080, and MySQL 8 on port 3306. The
distinction that matters is between *layers* and *tiers*. Packaging code into
presentation, business and data layers inside one executable is a logical
separation that a careless import can undo. Putting an HTTP boundary between
them makes the separation physical: the client cannot reach a repository or a
SQL statement even if a developer wanted it to, because those classes are not
in its process (Fowler, 2002).

## 4.1 How the separation was proved rather than asserted

Any project can draw three boxes and claim its tiers are separated. Two pieces
of evidence in this build make the claim testable.

**Two independent clients share one API.** A browser client served from the
service's own `static/` directory, and a Java Swing desktop client, both call
the same fifteen endpoints. Neither holds a business rule. Had a rule leaked
into the browser — the clash arithmetic, the discount percentages, the billing
total — the Swing client could not exist without duplicating it, and the two
would drift apart at the first change. The second client was built partly as a
deliberate check on this.

**The whole business tier runs with no database.** The 69 service-layer tests
in §6 execute `AuthenticationService`, `AppointmentService` and `BillingFacade`
against a hand-written in-memory repository, with no MySQL, no JDBC driver and
no Spring context. If a business rule had been written into a SQL string rather
than into Java, those tests could not run at all. A green suite is therefore
direct evidence of the boundary, not just of correctness.

## 4.2 Where each rule lives, and one deliberate duplication

Validation is duplicated intentionally. The client checks required fields and
opening hours so the receptionist gets an immediate response; the service
re-checks everything because the client is never trusted (NFR-04). The service
is authoritative, and `18-forbidden.png` demonstrates the point — a dentist's
own session receives HTTP 403 from a report endpoint even though the tab was
never displayed. Hiding a control is not a control.

The clash rule is duplicated more contentiously. `AppointmentService` checks for
a clash *and* `trg_appointment_no_overlap` enforces it inside the transaction.
The duplication was accepted after weighing two objections. Against it: the
rule now exists in two languages and could diverge, and business logic in the
data tier is widely discouraged because it is harder to test and version. For
it: between the service's check and its `INSERT`, another receptionist on
another machine can commit the same slot, and no amount of application code can
close that window — only a constraint inside the transaction can (NFR-05). The
compromise was to make the divergence risk visible rather than theoretical: the
same arithmetic is asserted in Java by `AppointmentOverlapTest` and against a
real MySQL server by the CI pipeline on every push, so if the two ever disagree,
the build fails.

## 4.3 What the architecture costs

Every operation now pays a network round trip, the system cannot run offline,
and deployment means three components rather than one. Sessions are held in a
`ConcurrentHashMap`, so they are lost on restart and would not survive running
a second instance — a limitation §8 returns to. For a single clinic these costs
are acceptable; Fowler's (2002) advice not to distribute objects unless the
distribution is genuinely needed is the reason the boundary is one coarse hop
per user action rather than a chatty remote interface.
