# 1. Introduction

*(~250 words)*

Sunrise Dental Clinic manages appointments, patient records and billing on
paper. The brief lists six functions the clinic believes it needs, but it also
lists four problems the clinic is actually suffering: double bookings, lost
patient records, long waiting times, and billing errors. These four failures,
not the six functions, were treated as the real specification for this system.

The distinction matters, because each failure implies a requirement the six
functions never mention. Double bookings can only occur if an appointment
occupies a period of time rather than an instant, which means every treatment
must carry a duration and the system needs a defined, enforced rule for what
counts as a clash. Lost patient records imply that a patient must be a
persistent entity referenced by each appointment, not a set of fields copied
into every booking. Long waiting times suggest that refusing an unavailable
slot is only half a solution — the system should offer an alternative. Billing
errors point to prices that live in maintained data rather than in code.

This report describes a three-tier distributed appointment and patient
management system built to address those failures: a browser client and a Java
Swing client sharing one REST API, a Spring Boot business tier, and a MySQL 8
data tier in which several business rules are enforced by triggers and a stored
procedure. It sets out the assumptions made, the modelling decisions taken, the
design patterns applied and the alternatives rejected, the testing strategy and
its evidence, and an honest evaluation of what the system does not yet do.

---

# 2. Assumptions and derived requirements

*(~350 words)*

The brief states that "you may make assumptions" and explicitly invites them
regarding access permissions. Thirteen were made. Each is recorded in the
project's requirements register and traced to a line in the brief, one of the
four stated failures, or a marking criterion; the significant ones are set out
below.

**Roles (ASM-01, ASM-02).** The brief says only "authorised staff". Three roles
were assumed — Receptionist, Dentist and Administrator — because a shared
account destroys accountability, and an audit trail that cannot name a person
is of no use when a booking is disputed. Crucially, the role determines which
menu items appear, but authorisation is enforced in the business tier: hiding a
button is not a security control, and the system is tested for this (§6).

**Patient persistence (ASM-03).** The brief requires *new* patients to be
registered, which is only meaningful if returning patients are looked up.
Patient is therefore a first-class entity with a generated number; an
appointment references it. Copying patient details into each appointment would
recreate the lost-records failure the clinic already has.

**Treatment durations (ASM-07).** This is the brief's largest omission. Without
a duration per treatment, "double booking" can only mean two appointments at
the identical minute, which is not the problem the clinic described. Each
treatment type therefore carries a standard duration, and an appointment's end
time is derived from it rather than entered by the receptionist.

**The clash rule (ASM-08).** Two appointments clash when their time ranges,
each widened by a ten-minute turnaround buffer, intersect for the same dentist
on the same date. Cancelled and no-show appointments release the slot.

**Discounts (ASM-12).** Four categories were assumed — none, senior citizen
10%, returning patient 5%, staff and family 15% — with the most generous
applying and no stacking. This was a deliberate choice: it gives the Strategy
pattern genuine work to do rather than a token single implementation.

**Scope (ASM-13).** Tax is not modelled, and the notification feature logs the
message it would send rather than dispatching it. Both are stated here as
scoping decisions rather than left as unexplained gaps.
