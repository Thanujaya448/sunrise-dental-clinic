# 3. Requirements modelling and UML

Six diagrams were produced, all in PlantUML with the `.puml` sources committed
beside the images, so they are versioned, diffable and reviewable in a pull
request. A binary drawing cannot be diffed, and in practice stops being
updated.

## 3.1 Use case diagram

Twenty-one use cases across four actors. `Receptionist`, `Dentist` and
`Administrator` inherit from an abstract `Staff User`, so shared behaviour is
modelled once. A `Notification Gateway` is a secondary actor — a system the
clinic depends on, not a person.

Notation follows Fowler (2003). `<<include>>` marks behaviour the base case
**always** performs; `<<extend>>` marks optional behaviour guarded by a
condition — *Suggest Alternative Slot* extends *Book Appointment* only when the
slot is unavailable. The arrows point opposite ways, a common error, so the
diagram carries a legend.

One decision is worth defending: authentication is a *precondition* of every use
case, not an `<<include>>` on each. Twenty-one arrows to *Log In* would be
defensible and useless, adding no information while obscuring the relationships
that carry some. It is recorded in each specification instead.

## 3.2 Domain class diagram

The domain model is an **analysis** model: it describes the problem domain, not
the classes implementing it (Larman, 2004). `DentistSchedule` appears because a
dentist's working day is a concept the clinic reasons about, though the code
holds it as a private helper. A domain model mirroring the implementation would
add nothing the design class diagram does not show.

Four relationship kinds are used deliberately, each tied to its consequence in
the schema. **Multiplicity** is stated at both ends. **Navigability** is shown
by the arrowheads and is not decoration: an `Appointment` references its
`Patient`, so "whose appointment is this?" is answered directly while the
reverse is a query; drawing every association bidirectional would claim
references the design does not hold. **Composition** joins `Bill` and `BillLine`
— a line has no meaning apart from its bill, and its key is `ON DELETE CASCADE`.
**Aggregation** joins `DentistSchedule` and `Appointment` — appointments exist
independently, and `appointment`'s key to `patient` is `ON DELETE RESTRICT`,
because deleting a patient must never destroy their history. Recording the UML
relationship and its SQL consequence together is what stops the model being
decorative.

## 3.3 Design class diagram and sequence diagrams

The design class diagram shows the three tiers and the pattern participants in
each. It was **redrawn from the implemented system**: the first version was
design intent, and the code diverged from it, naming four controllers, three
repositories, a mapper and a report factory that were never built. Submitting it
would have meant submitting a diagram of a system that does not exist. The
revised version and the sequence diagrams revised with it name only classes
present in the repository, and §5.5 explains each divergence as a decision.

The sequence diagrams cover logging in, booking against a clash, and generating
a bill. Each marks the HTTP boundary and shows the failure paths beside the
success path — the booking diagram's value is that it shows what happens when
the trigger rejects the insert. The three views check each other, and it was the
third that exposed the drift in the second.
