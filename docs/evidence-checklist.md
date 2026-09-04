# Evidence checklist — screenshots for the WRIT1 report

Tick these off as you take them. Save each one into `docs/evidence/` with the
filename given, so the report can reference it directly.

## Ground rule — read this first

**Every screenshot must come from your own system, running on your own
machine, at the moment you take it.** Nothing mocked up, nothing edited, no
image found online, no output retyped by hand. Fabricating evidence is a more
serious offence under the assessment regulations than a feature that does not
work, and it is the one mistake that cannot be argued down.

Two things in this build are **stubs**, and the report must say so plainly
rather than implying otherwise:

- `NotificationObserver` **logs what it would send**. It does not send an SMS
  or an email. Caption its screenshot as *"the notifier logging the message it
  would send; delivery is out of scope"*.
- The Swing desktop client is a **second client proving tier separation**, not
  a fully finished product. Say that.

Windows: `Win + Shift + S` for a region snip. For a full window, `Alt + PrtScn`.

---

## A. Codebase — 2 shots

| # | Filename | What to capture | How |
|---|---|---|---|
| 1 | `01-project-structure.png` | NetBeans Projects panel, both projects expanded far enough to show the `lk.sunrise.clinic` packages: `api`, `service`, `pattern`, `repository`, `domain`, `dto`, `exception` | NetBeans, expand the tree, snip |
| 2 | `02-test-tree.png` | The `Test Packages` branch expanded, showing all five test classes | Same panel |

## B. Database — 4 shots

| # | Filename | What to capture | How |
|---|---|---|---|
| 3 | `03-schema-tree.png` | MySQL Workbench navigator with `sunrise_clinic` expanded: Tables (12), Views (5), Stored Procedures, Functions | Workbench → Schemas panel |
| 4 | `04-healthcheck.png` | All 14 rows of the health check, every one reading `PASS` | `mysql -u root -p --table < db\08_healthcheck.sql` |
| 5 | `05-trigger-rejects.png` | The **error** from `06_verify.sql` check 1 — `Error Code: 1644` / `Overlapping appointment: dentist …` | `mysql -u root -p --force < db\06_verify.sql`, or run check 1 alone in Workbench |
| 6 | `06-bill-total.png` | The bill row from check 4: consultation 3,000 + treatments 28,000 − discount 2,800 = **28,200.00** | Same script, check 4 output |

> Shot 5 is important and counter-intuitive: **the error IS the pass**. Caption
> it *"the data tier refusing a double booking — the exception is the expected
> result"*, or a marker may read it as a defect.

## C. The running system — 10 shots

Start the service first (`mvn spring-boot:run` or the NetBeans Run button),
then open `http://localhost:8080`.

| # | Filename | What to capture |
|---|---|---|
| 7 | `07-service-startup.png` | Console showing `Sunrise Clinic service started on port 8080` and `MySQL connected — 10 active treatment types loaded` |
| 8 | `08-login.png` | The sign-in screen (light mode) |
| 9 | `09-dashboard-receptionist.png` | Signed in as `rmenaka` — the sidebar showing the receptionist's tabs |
| 10 | `10-booking-form.png` | The booking form filled in, before submitting |
| 11 | `11-clash-refused.png` | **The most important screenshot in the report.** A booking refused for a clash, showing the message *and* the three suggested alternative times |
| 12 | `12-search-detail.png` | An appointment found by number, with patient, dentist, treatments, times and status |
| 13 | `13-receipt.png` | A generated bill on screen, with the discount line visible |
| 14 | `14-print-preview.png` | `Ctrl + P` on that bill — the preview showing **only** the receipt, with navigation and buttons stripped out |
| 15 | `15-reports.png` | One of the five reports returning rows |
| 16 | `16-dark-mode.png` | Any screen with dark mode on |

To produce shot 11: book any slot, then try to book the **same dentist at the
same time** again. The service refuses and offers alternatives.

## D. Role restriction — 2 shots

| # | Filename | What to capture |
|---|---|---|
| 17 | `17-dentist-view.png` | Signed in as `dperera` — noticeably fewer tabs than the receptionist had |
| 18 | `18-forbidden.png` | A `403` proving the check is on the **server**, not just hidden in the UI |

For shot 18, with the dentist signed in, open the browser console (`F12`) and run:

```js
await (await fetch('/api/reports/DAILY_REVENUE', {
  headers: { Authorization: 'Bearer ' + JSON.parse(sessionStorage.getItem('clinic.session')).token }
})).status
```

It should return `403`. Screenshot the console. This single shot is what
separates "I hid the button" from "I enforced authorisation", and it is worth
real marks against the security criterion.

## E. Second client — 1 shot

| # | Filename | What to capture |
|---|---|---|
| 19 | `19-swing-client.png` | The Swing desktop client's main menu, running against the same service |

## F. Testing — 2 shots

| # | Filename | What to capture |
|---|---|---|
| 20 | `20-mvn-test.png` | Terminal showing `Tests run: 99, Failures: 0, Errors: 0` and `BUILD SUCCESS` |
| 21 | `21-jacoco.png` | `clinic-service\target\site\jacoco\index.html` open in a browser, showing the package table with the coverage bars |

## G. Version control and CI — 4 shots

| # | Filename | What to capture | Where |
|---|---|---|---|
| 22 | `22-actions-green.png` | The Actions tab showing all four runs green | github.com → Actions |
| 23 | `23-database-job.png` | Open CI run #4 → the **`Tier 3 — MySQL schema and business rules`** job, expanded so the step *"the overlap trigger must REJECT a clashing booking"* is visible with its green tick | Click into the run |
| 24 | `24-tdd-commits.png` | `git log --oneline` with the red→green pairs visible: `bb1ac3d` → `94d7a0b`, `ffa86d5` → `8b73b14` | Terminal |
| 25 | `25-pull-requests.png` | The closed pull-requests list showing all 8 merged | github.com → Pull requests → Closed |

Shot 23 is your best single piece of Task D evidence: it shows a machine that
had never seen your database building the schema from scratch and confirming
your trigger enforces the business rule.

---

## One last thing to do on GitHub

Tag a release so there is a "deployed version" to point at:

```powershell
git checkout main
git pull
git tag -a v1.0 -m "CIS6003 WRIT1 submission — Sunrise Dental Clinic"
git push origin v1.0
```

Then on GitHub: **Releases → Draft a new release → choose tag `v1.0`**, attach
`clinic-service-0.1.0-SNAPSHOT.jar`, and write two lines on how to run it.
Screenshot that page as `26-release.png`.
