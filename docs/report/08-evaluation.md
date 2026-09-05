# 8. Critical evaluation, limitations and future work

All four failures the clinic described are addressed: double bookings are
prevented at three points, the last inside the database transaction; patient
records persist and are referenced rather than copied; a refused booking offers
alternatives; and the bill total is computed once, in one transaction, and read
back rather than recalculated. What follows is what the system does not do.

**A promise the system cannot keep.** After five failed sign-ins an account
locks and the message says to ask the Administrator to unlock it. There is no
unlock function — no endpoint, no screen. `resetLoginFailures` clears the
counter on a successful sign-in but never the lock, so the only route back is a
manual `UPDATE`. Found while testing, this is the most serious defect
remaining: a system that tells a user to do something impossible is worse than
one that simply refuses. The fix is a repository method, a role-guarded
endpoint and a button.

**Sessions do not survive anything.** Held in a `ConcurrentHashMap`, they are
lost on restart and a second instance would not recognise the first's tokens.
Acceptable for one clinic on one server; Redis or a signed JWT if it were not.

**Suggested slots are the wrong ones.** `nextFreeSlots` scans from opening time
and returns the day's earliest free slots, so a receptionist asking for 14:45 is
offered 08:00. It satisfies FR-11 literally and misses its intent, which was to
reduce waiting. Ranking candidates by distance from the requested time would
fix it.

**Notification is one-directional and email-only.** Confirmations are sent;
cancellations are logged, because the observer contract does not carry the
patient's address on that path. No SMS gateway is integrated, and no tax is
modelled.

**Testing has a shaped gap.** Controllers sit at 0% coverage and the repository
at 5%; defensible, but `@WebMvcTest` slice tests would close it honestly. The
sub-two-second search requirement was verified against seeded data only, not at
10,000 records.

**What I would do differently.** Two process failures, both caught late. The
design class diagram was drawn as intent and left to drift while the code
changed, naming five classes that were never built; regenerating diagrams from
the implementation at each merge would have caught that in a pull request rather
than a week before submission. And a development database password reached an
early commit before the runtime-configuration approach described in §7.3 was
adopted. The repository now holds only placeholders and that password has been
rotated, but the real lesson is that secret scanning belongs in the pipeline
from the first commit, not the last: a credential cannot be un-committed from a
public history, only invalidated.
