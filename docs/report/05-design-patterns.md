# 5. Design patterns applied and evaluated

Patterns were selected because a force in this system called for one, not to
satisfy a checklist. Gamma *et al.* (1994) warn that applying a pattern where
the problem does not exist adds indirection without benefit; §5.5 records two
rejected on exactly that ground.

## 5.1 Strategy and Null Object — the discount rules *(behavioural)*

Four discount categories apply (ASM-12). Each is encapsulated behind a
`DiscountStrategy` interface with one `calculate` method, and
`DiscountPolicy.resolve()` selects the one that applies.

**The alternative rejected** was a chain of `if`/`else if` inside
`BillingFacade` — shorter, and no interface needed. It was rejected because the
rules vary independently of how a bill is assembled: under if/else every new
rule edits a method that is already tested and working, risking the rules that
pass. With Strategy, adding a fifth rule is adding a class. That is the
open/closed principle demonstrated rather than asserted, and the eighteen tests
in `DiscountStrategyTest` prove each rule in isolation, which an if/else chain
would not permit.

**Null Object** completes it: "no discount applies" is itself a strategy
returning `0.00` rather than `null`. Fowler (2002) calls this Special Case — the
absent value behaves correctly, so no caller tests for it, and no future caller
can forget to.

**What it cost.** Six classes where a dozen lines would have worked, and one
more indirection before the arithmetic is visible. For four rules that is a fair
exchange; for one fixed rule it would be over-engineering.

An abstract `PercentageDiscount` sits between the interface and the three
percentage rules. This is **Template Method**: the invariant part — multiply,
round `HALF_UP` to two places, match `DECIMAL(10,2)` — is fixed in the
superclass and only the rate and label vary. Without it the subclasses would
round independently and drift apart, producing bills that fail to reconcile by
a cent.

## 5.2 Observer — appointment events *(behavioural)*

When an appointment is booked, two things must happen besides the booking: the
patient is emailed a confirmation and the event reaches the audit trail (FR-20,
FR-24). `AppointmentService` publishes to a list of `AppointmentObserver`
implementations injected by Spring and knows about neither.

**The alternative rejected** was calling the mailer and the audit writer
directly from `book()`, coupling the clinic's most important method to two
concerns that are not booking and making every new listener edit it again. Under
Observer a third listener is a new `@Component`. The tests show the decoupling:
`AppointmentService` is built with a recording observer in one test and an empty
list in another, and booking succeeds in both.

`NotificationObserver` sends a real confirmation over SMTP (Figures 23, 24), and
three decisions in it are worth defending. It is `@Async`, because a slow mail
server must not make the receptionist wait for a booking already committed — the
log in Figure 23 is written on a `task-1` thread, not the request thread. Every
failure is caught and logged, never rethrown: an undelivered confirmation is a
nuisance, but an exception unwinding a committed appointment would be a defect,
so notification is kept out of the transaction by construction. And sending is
switched by configuration, not code, so the tests exercise the class that
ships.

**What it cost.** The flow is harder to trace in a debugger, because nothing in
`book()` names who will be called — the standard price of indirection.

## 5.3 Facade — billing *(structural)*

Generating a bill means resolving the appointment, checking it is completed and
unbilled, building a `Patient` from its discount inputs, selecting a strategy,
invoking a stored procedure and reading the bill back.
`BillingFacade.generateBill()` is the only method the controller calls. **The
alternative rejected** was letting the controller orchestrate those steps: the
sequence would then be duplicated in both clients, and the moment a rule changed
one would be missed.

## 5.4 Proxy, Singleton, Repository and Adapter

**Proxy** *(structural)* appears in both clients: `ClinicServiceProxy` and the
browser's `request()` expose the service as though local, hiding the HTTP call,
the `Authorization` header and the meaning of status codes. **Singleton**
*(creational)* holds each client's session; justifiably criticised as global
state that complicates testing (Martin, 2017), it was accepted only because the
scope is one process with one signed-in user, and appears nowhere in the
business tier. **Repository and DAO** separate the service layer from SQL, with
the measurable benefit in §6; one interface was used rather than five, which
Fowler (2002) would split per aggregate as the system grew. **Adapter / DTO**
records form the API contract and do not mirror the tables, so the schema can
change without breaking tier 1.

## 5.5 Patterns considered and rejected

**Factory Method with Template Method for reports** was in the original design:
a `ReportFactory` producing `AbstractReport` subclasses. The implemented system
maps a fixed `ReportType` enum to a view name instead. Four read-only queries do
not justify four classes of hierarchy, and the enum makes SQL injection
structurally impossible — a caller cannot supply a view name that is not already
a constant. The pattern would have added machinery and removed a security
property.

**An ORM (JPA/Hibernate)** was rejected for the data tier: several rules live in
a trigger and a stored procedure, and an ORM's caching and generated SQL work
against database-side logic. Spring JDBC keeps the interaction explicit, at the
cost of hand-written row mapping.
