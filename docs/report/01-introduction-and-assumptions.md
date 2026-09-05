# 1. Introduction

Sunrise Dental Clinic manages appointments, patient records and billing on
paper. The brief lists six functions the clinic believes it needs, but it also
lists four problems it is actually suffering: double bookings, lost patient
records, long waiting times, and billing errors. Those four failures, not the
six functions, were treated as the specification.

The distinction matters, because each failure implies a requirement the six
functions never mention. Double bookings can only occur if an appointment
occupies a period rather than an instant, so every treatment must carry a
duration and the system needs a defined, enforced rule for what counts as a
clash. Lost records imply that a patient is a persistent entity referenced by
each appointment, not fields copied into every booking. Long waiting times
suggest that refusing an unavailable slot is half a solution — the system
should offer an alternative. Billing errors point to prices held in maintained
data rather than in code.

This report describes a three-tier distributed system built to address them: a
browser client and a Java Swing client over one REST API, a Spring Boot business
tier, and a MySQL 8 data tier in which several rules are enforced by triggers
and a stored procedure. It sets out the assumptions, the design decisions with
the alternatives rejected, the testing strategy and its evidence, and an honest
evaluation of what the system does not do.

# 2. Assumptions and derived requirements

The brief invites assumptions on system design and access permissions.
Thirteen were made, each traced to a line in the brief, one of the four stated
failures, or a marking criterion. The significant ones follow.

**Roles (ASM-01, ASM-02).** The brief says only "authorised staff". Three roles
were assumed — Receptionist, Dentist and Administrator — because a shared
account destroys accountability, and an audit trail that cannot name a person is
useless when a booking is disputed. The role determines the menu, but
authorisation is enforced in the business tier: hiding a button is not a
control.

**Patient persistence (ASM-03).** The brief requires *new* patients to be
registered, which is only meaningful if returning ones are looked up. Patient
is therefore a first-class entity with a generated number, referenced by each
appointment. Copying details into every appointment would recreate the
lost-records failure.

**Treatment durations (ASM-07).** The brief's largest omission. Without a
duration per treatment, "double booking" can only mean the identical minute,
which is not the problem described. Each treatment type carries a standard
duration, and an appointment's end time is derived from it rather than typed.

**The clash rule (ASM-08).** Two appointments clash when their ranges, each
widened by a ten-minute turnaround buffer, intersect for the same dentist on
the same date. Cancelled and no-show appointments release the slot.

**Discounts (ASM-12).** Four categories — none, senior 10%, returning patient
5%, staff and family 15% — with the most generous applying and no stacking.
This was deliberate: it gives the Strategy pattern real work rather than a
token single implementation.

**Scope (ASM-13).** No tax is modelled. Stated here as a scoping decision
rather than left as an unexplained gap.
