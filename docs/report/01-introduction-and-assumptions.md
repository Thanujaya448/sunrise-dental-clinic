# 1. Introduction

Sunrise Dental Clinic manages appointments, patient records and billing on
paper. The brief lists six functions the clinic believes it needs, but it also
lists four problems it is suffering: double bookings, lost patient records, long
waiting times, and billing errors. Those four failures, not the six functions,
were treated as the specification.

Each failure implies a requirement the six functions never mention. Double
bookings can only occur if an appointment occupies a period rather than an
instant, so treatments need durations and the clash rule needs a definition and
an enforcement point. Lost records imply a patient is a persistent entity
referenced by each appointment. Long waiting times suggest refusing a slot is
half a solution. Billing errors point to prices held in data, not code.

This report describes the three-tier system built to address them — two clients
over one REST API, a Spring Boot business tier, and a MySQL 8 data tier
enforcing rules through triggers and a stored procedure — with the alternatives
rejected at each decision, the testing evidence, and an honest evaluation.

# 2. Assumptions and derived requirements

The brief invites assumptions on design and access permissions. Thirteen were
made, each traced to a line in the brief, one of the four failures, or a marking
criterion. The significant ones follow.

**Roles (ASM-01, ASM-02).** The brief says only "authorised staff". Three were
assumed — Receptionist, Dentist, Administrator — because a shared account
destroys accountability and an audit trail that cannot name a person is useless
when a booking is disputed. The role picks the menu, but authorisation is
enforced in the business tier: hiding a button is not a control.

**Patient persistence (ASM-03).** The brief requires *new* patients to be
registered, which is only meaningful if returning ones are looked up. Patient is
a first-class entity with a generated number; copying details into every
appointment would recreate the lost-records failure.

**Treatment durations (ASM-07).** The brief's largest omission. Without a
duration per treatment, "double booking" means only the identical minute, which
is not the problem described. End times are derived, not typed.

**The clash rule (ASM-08).** Two appointments clash when their ranges, each
widened by a ten-minute turnaround buffer, intersect for the same dentist on the
same date. Cancelled and no-show appointments release the slot.

**Discounts (ASM-12).** Four categories — none, senior 10%, returning patient
5%, staff and family 15% — most generous applying, no stacking. Deliberate: it
gives the Strategy pattern real work rather than a token implementation.

**Scope (ASM-13).** No tax is modelled, stated as a scoping decision rather than
left as an unexplained gap.
