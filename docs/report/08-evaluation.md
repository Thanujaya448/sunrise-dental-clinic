# 8. Critical evaluation, limitations and future work

All four failures are addressed: double bookings are prevented at three points,
the last inside the database transaction; records persist and are referenced
rather than copied; a refused booking offers alternatives; and the bill total is
computed once and read back rather than recalculated. What follows is what the
system does not do.

**A promise the system nearly failed to keep.** After five failed sign-ins an
account locks and the message says to ask the Administrator. Testing found that
no unlock function existed: `resetLoginFailures` clears the counter on a
successful sign-in but never the lock, so the only route back was a manual
`UPDATE`. A system that tells a user to do something impossible is worse than
one that simply refuses, so `AdministrationService.unlockAccount()` was added,
clearing both the lock and the counter and writing to the audit trail. The
lesson is that FR-03 was written as one rule when it was two, and only exercising
the message revealed the second half.

**Sessions do not survive anything.** Held in a `ConcurrentHashMap`, they are
lost on restart and a second instance would not recognise the first's tokens.
Redis or a signed JWT if the scope grew beyond one server.

**Suggested slots are the wrong ones.** `nextFreeSlots` scans from opening time,
so a receptionist asking for 14:45 is offered 08:00. It satisfies FR-11
literally and misses its intent. Ranking candidates by distance from the
requested time would fix it.

**Notification is one-directional.** Confirmations are sent; cancellations are
only logged, because the observer contract does not carry the address on that
path. No SMS gateway, no tax model.

**Testing has a shaped gap.** Controllers sit at 0% coverage and the repository
at 5%; defensible, but `@WebMvcTest` slice tests would close it honestly, and
the search-speed requirement was verified against seeded data only.

**What I would do differently.** Two process failures, caught late. The design
class diagram was drawn as intent and left to drift, naming five classes never
built; regenerating diagrams from the implementation at each merge would have
caught it in a pull request. And a development database password reached an
early commit before the runtime configuration in §7.3 was adopted. The
repository now holds only placeholders and the password has been rotated, but
the lesson is that secret scanning belongs in the pipeline from the first
commit: a credential cannot be un-committed from a public history, only
invalidated.
