# 4. Three-tier distributed architecture

The system runs as three operating-system processes: a client, a Spring Boot
REST service on port 8080 (VMware, 2024), and MySQL 8 on port 3306 (Oracle,
2024), whose twelve tables are in third normal form (Codd, 1970; Date, 2003).
The distinction that matters is between *layers* and *tiers*. Layers inside one
executable are a logical separation a careless import can undo; an HTTP boundary
makes it physical, because the client cannot reach a repository or a SQL
statement even if a developer wanted it to (Fowler, 2002).

## 4.1 Separation proved rather than asserted

Any project can draw three boxes and claim its tiers are separated. Two things
make the claim testable. **Two independent clients share one API**: a browser
client and a Swing client call the same fifteen endpoints, and neither holds a
business rule. Had one leaked into the browser, the Swing client could not exist
without duplicating it, and the two would diverge at the first change. And **the
business tier runs with no database**: the 69 service-layer tests execute
against an in-memory repository, with no MySQL, no JDBC driver and no Spring
context. Had a rule been written into a SQL string, those tests could not run. A
green suite is evidence of the boundary, not only of correctness.

## 4.2 Where each rule lives, and one deliberate duplication

Validation is duplicated intentionally: the client checks required fields so the
receptionist gets an immediate response, and the service re-checks everything
because the client is never trusted (NFR-04). Figure 21 shows a dentist's own
session receiving HTTP 403 from a report endpoint although the tab was never
displayed. The Administrator's screens (Figures 22 and 23) make the point
positively: prices, treatment durations and staff accounts are maintained
through the same API, so code normalisation, the five-to-480-minute bound the
schema also enforces, and BCrypt hashing are written once in
`AdministrationService` and cannot be lost by a client that forgets them.

The clash rule is duplicated more contentiously: `AppointmentService` checks it
*and* `trg_appointment_no_overlap` enforces it inside the transaction. Against:
the rule exists in two languages and could diverge, and data-tier logic is
criticised as harder to test and version. For: between the service's check and
its `INSERT`, another receptionist can commit the same slot, and only a
constraint inside the transaction can close that window (NFR-05). The compromise
makes the divergence risk visible rather than theoretical — the same arithmetic
is asserted in Java and against real MySQL by CI on every push, so if the two
disagree the build fails.

## 4.3 What the architecture costs

Every operation pays a network round trip, the system cannot run offline, and
deployment means three components. Sessions in a `ConcurrentHashMap` are lost on
restart. For one clinic these costs are acceptable, and Fowler's (2002) advice
against distributing objects needlessly is why the boundary is one coarse hop
per user action rather than a chatty interface.
