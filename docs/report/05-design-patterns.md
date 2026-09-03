# 5. Design patterns applied and evaluated

*(~1,000 words — Task B)*

Patterns were selected because a specific force in this system called for one,
not to satisfy a checklist. Gamma *et al.* (1994) warn in their own
introduction that applying a pattern where the problem does not exist adds
indirection without benefit, and the last part of this section records two
patterns considered and deliberately rejected on exactly that ground.

## 5.1 Strategy and Null Object — the discount rules *(behavioural)*

The clinic applies four discount categories: none, senior citizen 10%,
returning patient 5%, and staff and family 15% (ASM-12). Each is encapsulated
behind a `DiscountStrategy` interface with a single `calculate` method, and
`DiscountPolicy.resolve()` selects the one that applies.

**The alternative rejected** was the obvious one: a chain of `if`/`else if`
inside `BillingFacade`. It is shorter and needs no interface. It was rejected
because the discount rules vary independently of how a bill is assembled, and
under the if/else design every new rule means editing a method that is already
tested and working — placing the rules that currently pass at risk each time
one is added. With Strategy, adding a fifth rule is adding a class; the facade,
the policy and the four existing rules are untouched. This is the open/closed
principle in a form that can be demonstrated rather than asserted, and the
eighteen tests in `DiscountStrategyTest` prove each rule in isolation, which an
if/else chain would not permit.

**Null Object** completes it. The "no discount applies" case is itself a
strategy returning `0.00`, rather than `resolve()` returning `null`. Fowler
(2002) describes this as Special Case: the absent value is represented by an
object that behaves correctly, so no caller has to test for it. `BillingFacade`
therefore contains no null check, and no future caller can forget one.

**What it cost.** Six classes where a dozen lines would have worked, and a
reader must follow one more indirection to see the arithmetic. For four rules
in a clinic system that is a fair exchange; for a single fixed rule it would be
over-engineering, and the pattern would not have been used.

An abstract `PercentageDiscount` sits between the interface and the three
percentage rules, applying `BigDecimal` rounding `HALF_UP` to two decimal
places in one place. This is **Template Method**: the invariant part of the
algorithm — multiply, round, match `DECIMAL(10,2)` in MySQL — is fixed in the
superclass and only the rate and label vary. Without it, three subclasses would
each round independently and could silently drift apart, producing bills that
fail to reconcile with the stored totals by a cent.

## 5.2 Observer — appointment events *(behavioural)*

When an appointment is booked, two things must happen besides the booking: the
patient should be notified, and the event must reach the audit trail (FR-20,
FR-24). `AppointmentService` publishes to a list of `AppointmentObserver`
implementations injected by Spring and knows nothing about either.

**The alternative rejected** was calling the notifier and the audit writer
directly from `book()`. That couples the clinic's most important business
method to two concerns that are not booking, and each new listener would edit
it again. Under Observer, a third listener is a new `@Component`; `book()` does
not change. The tests demonstrate the decoupling directly: `AppointmentService`
is constructed with a `RecordingObserver` in one test and with an empty list in
another, and booking succeeds in both.

**What it cost.** The flow is harder to trace in a debugger, because nothing in
`book()` names who will be called. That is the standard trade-off of
indirection, and it is real.

**An honesty note.** `NotificationObserver` logs the message it *would* send.
No SMS or email is dispatched. The pattern is fully implemented and the
integration is not, and the report says so rather than implying otherwise.

## 5.3 Facade — billing *(structural)*

Generating a bill means resolving the appointment, checking it is completed and
not already billed, building a `Patient` from its discount inputs, selecting a
strategy, invoking a stored procedure, and reading the bill back.
`BillingFacade.generateBill(appointmentNo)` is the only method the controller
calls.

**The alternative rejected** was letting the controller orchestrate those
steps. It would work, but the sequence would then be duplicated in the browser
client and the Swing client — and the moment a rule changed, one of them would
be missed. Because the facade holds it, both clients share one billing
behaviour by construction.

## 5.4 Proxy, Singleton, Repository and Adapter

**Proxy** *(structural)* appears in both clients. `ClinicServiceProxy` in the
Swing client and `request()` in the browser expose the service as though it
were local, hiding the HTTP call, the `Authorization` header and the mapping of
status codes to meaning. The client code reads as method calls, which is the
point of the pattern.

**Singleton** *(creational)* holds the session in each client — `SessionHolder`
in Swing, one module-level `Session` object in JavaScript. Singleton is
justifiably criticised as global state that complicates testing (Martin, 2017),
and that criticism applies here. It was accepted only because the scope is a
single client process holding a single signed-in user, and the alternative —
threading the token through every constructor — would obscure the design for no
practical gain. It is not used anywhere in the business tier.

**Repository and DAO** separate the service layer from SQL. `ClinicRepository`
is an interface; `JdbcClinicRepository` implements it. The measurable benefit
is in §6: the whole service layer runs in tests against a hand-written
in-memory implementation with no MySQL, no JDBC driver and no Spring context.
One interface was used rather than five. For a system of twelve tables that
keeps the wiring visible; Fowler (2002) would split it per aggregate as the
system grew, and the design should be revisited at that point.

**Adapter / DTO** *(structural)*. The `Dtos` records are the API contract and
deliberately do not mirror the tables — no client ever learns a primary key or
a column name, so the schema can change without breaking tier 1.

## 5.5 Patterns considered and rejected

Two are worth recording. **Factory Method with Template Method for reports**
was in the original design: a `ReportFactory` producing `AbstractReport`
subclasses. The implemented system instead maps a fixed `ReportType` enum to a
view name. This was chosen because four read-only queries do not justify four
classes of hierarchy, and because the enum makes SQL injection structurally
impossible — a caller cannot supply a view name that is not already a constant.
The pattern would have added machinery and removed a security property.

**An ORM (JPA/Hibernate)** was rejected for the data tier. The clinic's rules
live partly in a trigger and a stored procedure; an ORM's caching and
generated SQL work against database-side logic. Spring JDBC keeps the
interaction explicit, at the cost of hand-written row mapping.
