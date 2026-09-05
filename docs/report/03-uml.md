# 3. Requirements modelling and UML

Six diagrams were produced: a use case diagram, a domain class diagram, a
design class diagram with a pattern-focused companion, and three sequence
diagrams. All are written in PlantUML, with the `.puml` sources committed beside
the rendered images, so diagrams are versioned, diffable and reviewable in a
pull request. A binary drawing exported from a graphics tool cannot be diffed,
and in practice stops being updated.

## 3.1 Use case diagram

Twenty-one use cases across four actors. `Receptionist`, `Dentist` and
`Administrator` inherit from an abstract `Staff User`, so behaviour every
signed-in user shares is modelled once. A `Notification Gateway` appears as a
secondary actor: it is a system the clinic depends on, not a person using it.

Notation follows Fowler (2003). `<<include>>` marks behaviour the base case
**always** performs — booking always records an audit entry — while
`<<extend>>` marks optional behaviour
guarded by a condition: *Suggest Alternative Slot* extends *Book Appointment*
only when the requested slot is unavailable. The arrows point in opposite
directions, a common error, so the diagram carries a legend stating both.

One decision is worth defending. Authentication is a *precondition* of every
use case, not an `<<include>>` on each: twenty-one include arrows to *Log In*
would be defensible and useless, adding no information while obscuring the
relationships that carry some. It is recorded in each specification instead.

## 3.2 Domain class diagram

The domain model is an **analysis** model: it describes the problem domain, not
the Java classes implementing it (Larman, 2004). `DentistSchedule` appears
because a dentist's working day is a concept the clinic reasons about, though
it has no class in the code, where the behaviour is a private helper in
`AppointmentService`. That is not an inconsistency — a domain model mirroring
the implementation would add nothing the design class diagram does not show.

Four relationship kinds are used deliberately, each tied to its consequence in
the schema. **Multiplicity** is stated at both ends of every association.
**Navigability** is shown by the arrowheads and is not decoration: an
`Appointment` holds a reference to its `Patient`, so "whose appointment is
this?" is answered directly while the reverse is a query, and drawing every
association bidirectional would claim references the design does not hold.
**Composition** joins `Bill` and `BillLine` — a line has no meaning apart from
its bill, and `bill_line`'s foreign key is `ON DELETE CASCADE`, so deleting a
bill removes its lines by definition rather than by convention. **Aggregation**
joins `DentistSchedule` and `Appointment` — a schedule groups appointments that
exist independently of it, and `appointment`'s key to `patient` is `ON DELETE
RESTRICT`, because deleting a patient must never silently destroy their
history. Recording the UML relationship and its SQL consequence together is
what stops the model being decorative.

## 3.3 Design class diagram and sequence diagrams

The design class diagram shows the three physical tiers and the pattern
participants in each. It was **redrawn from the implemented system** partway
through the project: the first version was design intent, and the code then
diverged from it, naming four controllers, three repositories, a mapper and a
report factory that were never built. Submitting it would have meant submitting
a diagram of a system that does not exist. The revised version and the three
sequence diagrams revised with it name only classes present in the repository,
and §5.5 explains each divergence as a decision rather than an omission.

The sequence diagrams cover logging in, booking against a clash, and generating
a bill. Each marks the HTTP boundary explicitly and shows the failure paths
beside the success path — the value of the booking diagram is precisely that it
shows what happens when the trigger rejects the insert.

Together the three views answer different questions and check each other: the
use case diagram fixes *what* the system must do, the class diagrams *what it is
made of*, and the sequence diagrams *what happens in what order*. It was the
third that exposed the drift in the second.
