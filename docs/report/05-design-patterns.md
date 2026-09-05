# 5. Design patterns applied and evaluated

Patterns were selected because a force in this system called for one, not to
satisfy a checklist. Gamma *et al.* (1994) warn that applying a pattern where
the problem does not exist adds indirection without benefit, and §5.5 records
two patterns considered and rejected on exactly that ground.

## 5.1 Strategy and Null Object — the discount rules *(behavioural)*

Four discount categories apply: none, senior 10%, returning patient 5%, staff
and family 15% (ASM-12). Each is encapsulated behind a `DiscountStrategy`
interface with one `calculate` method, and `DiscountPolicy.resolve()` selects
the one that applies.

**The alternative rejected** was a chain of `if`/`else if` inside
`BillingFacade`. It is shorter and needs no interface. It was rejected because
the discount rules vary independently of how a bill is assembled: under the
if/else design every new rule edits a method that is already tested and
working, putting the passing rules at risk each time. With Strategy, adding a
fifth rule is adding a class; the facade, the policy and the four existing
rules are untouched. That is the open/closed principle in a form that can be
demonstrated rather than asserted, and the eighteen tests in
`DiscountStrategyTest` prove each rule in isolation, which an if/else chain
would not permit.

**Null Object** completes it. "No discount applies" is itself a strategy
returning `0.00` rather than `resolve()` returning `null`. Fowler (2002) calls
this Special Case: the absent value is an object that behaves correctly, so no
caller tests for it. `BillingFacade` contains no null check, and no future
caller can forget one.

**What it cost.** Six classes where a dozen lines would have worked, and one
more indirection before a reader sees the arithmetic. For four rules that is a
fair exchange; for a single fixed rule it would be over-engineering and the
pattern would not have been used.

An abstract `PercentageDiscount` sits between the interface and the three
percentage rules. This is **Template Method**: the invariant part — multiply,
round `HALF_UP` to two places, match `DECIMAL(10,2)` in MySQL — is fixed in the
superclass and only the rate and label vary. Without it the three subclasses
would round independently and could drift apart, producing bills that fail to
reconcile by a cent.

## 5.2 Observer — appointment events *(behavioural)*

When an appointment is booked, two things must happen besides the booking: the
patient is emailed a confirmation, and the event reaches the audit trail
(FR-20, FR-24). `AppointmentService` publishes to a list of
`AppointmentObserver` implementations injected by Spring and knows about
neither.

**The alternative rejected** was calling the mailer and the audit writer
directly from `book()`, which couples the clinic's most important method to two
concerns that are not booking and makes every new listener edit it again. Under
Observer a third listener is a new `@Component` and `book()` does not change.
The tests show the decoupling: `AppointmentService` is built with a recording
observer in one test and an empty list in another, and booking succeeds in
both.

`NotificationObserver` sends a real confirmation over SMTP (Figures 23 and 24),
and three decisions in it are worth defending. It is `@Async`, because a slow or
briefly unreachable mail server must not make the receptionist wait for a
booking that has already committed — the log line in Figure 23 is written on a
`task-1` thread, not the request thread. Every failure is caught and logged rather than rethrown:
an undelivered confirmation is a nuisance, but an exception unwinding a
committed appointment would be a defect, so notification is kept out of the
booking transaction by construction. And sending is switched by configuration
rather than by code, so the tests and the CI pipeline exercise the same class
that ships, with delivery off.

**What it cost.** The flow is harder to trace in a debugger, because nothing in
`book()` names who will be called. That is the standard price of indirection.

## 5.3 Facade — billing *(structural)*

Generating a bill means resolving the appointment, checking it is completed and
not already billed, building a `Patient` from its discount inputs, selecting a
strategy, invoking a stored procedure and reading the bill back.
`BillingFacade.generateBill(appointmentNo)` is the only method the controller
calls. **The alternative rejected** was letting the controller orchestrate
those steps: the sequence would then be duplicated in both clients, and the
moment a rule changed one of them would be missed. Because the facade holds it,
both clients share one billing behaviour by construction.

## 5.4 Proxy, Singleton, Repository and Adapter

**Proxy** *(structural)* appears in both clients. `ClinicServiceProxy` and the
browser's `request()` expose the service as though it were local, hiding the
HTTP call, the `Authorization` header and the mapping of status codes to
meaning.

**Singleton** *(creational)* holds the session in each client. It is
justifiably criticised as global state that complicates testing (Martin, 2017),
and that criticism applies here. It was accepted only because the scope is one
client process holding one signed-in user, and threading the token through
every constructor would obscure the design for no practical gain. It is not
used anywhere in the business tier.

**Repository and DAO** separate the service layer from SQL; the measurable
benefit is in §6. One interface was used rather than five, which keeps the
wiring visible at twelve tables; Fowler (2002) would split it per aggregate as
the system grew. **Adapter / DTO** records form the API contract and
deliberately do not mirror the tables, so no client learns a primary key or a
column name and the schema can change without breaking tier 1.

## 5.5 Patterns considered and rejected

**Factory Method with Template Method for reports** was in the original design:
a `ReportFactory` producing `AbstractReport` subclasses. The implemented system
maps a fixed `ReportType` enum to a view name instead. Four read-only queries do
not justify four classes of hierarchy, and the enum makes SQL injection
structurally impossible — a caller cannot supply a view name that is not
already a constant. The pattern would have added machinery and removed a
security property.

**An ORM (JPA/Hibernate)** was rejected for the data tier. Several rules live
in a trigger and a stored procedure, and an ORM's caching and generated SQL
work against database-side logic. Spring JDBC keeps the interaction explicit,
at the cost of hand-written row mapping.
