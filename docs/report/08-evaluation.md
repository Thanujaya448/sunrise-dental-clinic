# 8. Critical evaluation, limitations and future work

*(~365 words)*

All four failures the clinic described are addressed: double bookings are
prevented at three points, the last inside the database transaction; patient
records persist and are referenced rather than copied; a refused booking offers
alternatives; and the bill total is computed once, in one transaction, and read
back rather than recalculated. What follows is what the system does not do.

**A promise the system cannot keep.** After five failed sign-ins an account
locks and the message says to ask the Administrator to unlock it. There is no
unlock function — no endpoint, no screen. `resetLoginFailures` clears the
counter on a successful sign-in but never clears the lock, so the only route
back is a manual `UPDATE`. This was found while testing and is the most serious
defect remaining: a system that tells a user to do something impossible is worse
than one that simply refuses. It is a repository method, a role-guarded
endpoint and a button.

**Sessions do not survive anything.** Held in a `ConcurrentHashMap`, they are
lost on restart and a second instance would not recognise the first's tokens.
Acceptable for one clinic on one server; Redis or a signed JWT if it were not.

**Suggested slots are the wrong ones.** `nextFreeSlots` scans from opening time
and returns the day's earliest free slots, so a receptionist asking for 14:45 is
offered 08:00. It satisfies FR-11 literally and misses its intent, which was to
reduce waiting. Ranking candidates by distance from the requested time would fix
it.

**Two features are stubs, and are labelled as such.** `NotificationObserver`
logs the message it would send; nothing is dispatched. No tax is modelled.

**Testing has a shaped gap.** Controllers sit at 0% coverage and the repository
at 5%; defensible, but `@WebMvcTest` slice tests would close it honestly. The
sub-two-second search requirement was verified against seeded data only, not at
10,000 records.

**What I would do differently.** The design class diagram was drawn as intent
and then left to drift while the code changed, and it named five classes that
were never built. Regenerating diagrams from the implementation at each merge,
rather than once at the end, would have caught that in a pull request instead of
a week before submission.
