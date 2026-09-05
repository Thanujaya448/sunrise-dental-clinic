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
Redis or a signed JWT would be the answer beyond one server.

**Suggested slots are the wrong ones.** `nextFreeSlots` scans from opening time,
so a receptionist asking for 14:45 is offered 08:00. It satisfies FR-11
literally and misses its intent. Ranking candidates by distance from the
requested time would fix it.

**Notification is one-directional.** Confirmations are sent; cancellations are
only logged, because the observer contract does not carry the address on that
path. There is no SMS gateway and no tax model.

**Testing has a shaped gap.** Controllers sit at 0% coverage and the repository
at 4%; defensible, but `@WebMvcTest` slice tests would close it. `JavaMailSender`
should also sit behind an interface so the send path can be asserted without a
server, and the search-speed requirement was verified against seeded data only.

**What I would do differently.** Three process failures, caught late. The design
class diagram was drawn as intent and left to drift, naming five classes never
built; it was then published truncated, because PlantUML crops at 4,096 pixels
without saying so. Regenerating diagrams from the code at each merge would have
caught the first, and opening the output the second. And a development database
password reached an early commit. It has been rotated and the repository holds
placeholders only, but secret scanning belongs in the pipeline from the first
commit: a credential cannot be un-committed from a public history, only
invalidated.
