# 4. Three-tier distributed architecture

The system runs as three operating-system processes: a client, a Spring Boot
REST service on port 8080 (VMware, 2024), and MySQL 8 on port 3306 (Oracle,
2024), whose twelve tables are in third normal form (Codd, 1970; Date, 2003). The distinction that
matters is between *layers* and *tiers*. Packaging code into presentation,
business and data layers inside one executable is a logical separation a
careless import can undo. An HTTP boundary makes it physical: the client cannot
reach a repository or a SQL statement even if a developer wanted it to, because
those classes are not in its process (Fowler, 2002).

## 4.1 Separation proved rather than asserted

Any project can draw three boxes and claim its tiers are separated. Two things
here make the claim testable.

**Two independent clients share one API.** A browser client served from the
service's own `static/` directory and a Java Swing desktop client call the same
fifteen endpoints, and neither holds a business rule. Had one leaked into the
browser — the clash arithmetic, the discount percentages, the billing total —
the Swing client could not exist without duplicating it, and the two would
diverge at the first change. The second client was built partly as a check.

**The business tier runs with no database.** The 69 service-layer tests in §6
execute `AuthenticationService`, `AppointmentService` and `BillingFacade`
against a hand-written in-memory repository, with no MySQL, no JDBC driver and
no Spring context. Had a rule been written into a SQL string rather than into
Java, those tests could not run. A green suite is evidence of the boundary, not
only of correctness.

## 4.2 Where each rule lives, and one deliberate duplication

Validation is duplicated intentionally. The client checks required fields and
opening hours so the receptionist gets an immediate response; the service
re-checks everything because the client is never trusted (NFR-04). The service
is authoritative, and Figure 21 shows a dentist's own session receiving HTTP
403 from a report endpoint although the tab was never displayed.

The clash rule is duplicated more contentiously. `AppointmentService` checks
for a clash *and* `trg_appointment_no_overlap` enforces it inside the
transaction. Two objections were weighed. Against: the rule exists in two
languages and could diverge, and business logic in the data tier is criticised
as harder to test and version. For: between the service's check and its
`INSERT`, another receptionist on another machine can commit the same slot, and
no application code can close that window — only a constraint inside the
transaction can (NFR-05). The compromise makes the divergence risk visible
rather than theoretical: the same arithmetic is asserted in Java by
`AppointmentOverlapTest` and against a real MySQL server by the CI pipeline on
every push, so if the two disagree the build fails.

## 4.3 What the architecture costs

Every operation pays a network round trip, the system cannot run offline, and
deployment means three components rather than one. Sessions held in a
`ConcurrentHashMap` are lost on restart and would not survive a second instance
— a limitation §8 returns to. For a single clinic these costs are acceptable,
and Fowler's (2002) advice against distributing objects needlessly is why the
boundary is one coarse hop per user action rather than a chatty interface.
