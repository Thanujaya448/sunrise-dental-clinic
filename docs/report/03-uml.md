# 3. Requirements modelling and UML

*(~615 words — Task A)*

Six diagrams were produced: a use case diagram, a domain class diagram, a
design class diagram with a pattern-focused companion, and three sequence
diagrams. All are written in PlantUML and the `.puml` sources are committed
alongside the rendered images, so the diagrams are versioned, diffable and
reviewable in a pull request like any other artefact. A binary drawing exported
from a graphics tool cannot be diffed, and in practice stops being updated.

## 3.1 Use case diagram

Twenty-one use cases across four actors. `Receptionist`, `Dentist` and
`Administrator` inherit from an abstract `Staff User`, so behaviour every
signed-in user shares — signing in, viewing help, exiting safely — is written
once rather than three times. A `Notification Gateway` appears as a secondary
actor because notification is a system the clinic depends on rather than a
person using it.

`<<include>>` marks behaviour the base case **always** performs — booking
always records an audit entry — while `<<extend>>` marks optional behaviour
guarded by a condition: *Suggest Alternative Slot* extends *Book Appointment*
only when the requested slot is unavailable. The arrows point in opposite
directions, a common error, so the diagram carries a legend stating both.

**One modelling decision is worth defending.** Authentication is a
*precondition* of every use case, not an `<<include>>` on each. Drawing
twenty-one include arrows to *Log In* would be technically defensible and
practically useless — it would add no information and obscure the relationships
that do. It is recorded in each use case specification instead.

## 3.2 Domain class diagram

The domain model is an **analysis** model: it describes the clinic's problem
domain, not the Java classes that implement it. This distinction is deliberate
and follows Larman (2004). `DentistSchedule` appears here because a dentist's
working day is a real concept the clinic reasons about, but it has no
corresponding class in the code, where the same behaviour is a private helper
in `AppointmentService`. That is not an inconsistency; a domain model that
mirrored the implementation one-for-one would have added nothing the design
class diagram does not already show.

Multiplicity is stated on every association. Two are load-bearing and were
chosen rather than defaulted:

**Composition** between `Bill` and `BillLine` (filled diamond). A bill line has
no meaning apart from its bill and cannot be moved to another one. The model is
enforced in the schema — `bill_line`'s foreign key is `ON DELETE CASCADE`, so
deleting a bill removes its lines by definition rather than by convention.

**Aggregation** between `DentistSchedule` and `Appointment` (hollow diamond). A
schedule groups appointments, but an appointment exists independently of it and
survives being removed from one. The schema reflects this too: `appointment`'s
foreign key to `patient` is `ON DELETE RESTRICT`, because deleting a patient
must never silently destroy their appointment history.

Recording the UML relationship and its SQL consequence together is what stops
the model being decorative.

## 3.3 Design class diagram and sequence diagrams

The design class diagram shows the three physical tiers and the pattern
participants in each. It was **redrawn from the implemented system** partway
through the project. The first version had been drawn as design intent and the
code then diverged from it — it named four controllers, three repositories, a
mapper and a report factory that were never built. Leaving it would have meant
submitting a diagram of a system that does not exist. The revised version, and
the three sequence diagrams revised with it, name only classes present in the
repository, and §5.5 explains each divergence as a decision rather than an
omission.

The three sequence diagrams cover logging in, booking against a clash, and
generating a bill. Each marks the HTTP boundary explicitly, and each shows the
failure paths beside the success path — the value of the booking diagram is
precisely that it shows what happens when the trigger rejects the insert.
