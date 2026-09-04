# 7. Version control, continuous integration and deployment

*(~380 words — Task D)*

The repository is at `github.com/Thanujaya448/sunrise-dental-clinic`.

## 7.1 Branching and history

Work was done on short-lived `feature/` branches, each merged into `main`
through a pull request rather than committed directly. The history is written to
be readable: commit messages state what changed and why, and the two
test-driven cycles appear as separate red and green commits so the sequence can
be verified rather than taken on trust (§6.2).

## 7.2 Continuous integration

`.github/workflows/ci.yml` runs on every push and every pull request. It has
three jobs, one per tier, and a failure in any of them blocks the merge.

**`unit-tests`** builds on JDK 17, runs the 99 tests with JaCoCo, and uploads
the JUnit reports, the coverage report and the packaged jar as artefacts. A
per-package coverage table is written to the run summary, so a reviewer sees the
figures without downloading anything.

**`database-rules`** is the job worth examining. GitHub starts a real MySQL 8
container, builds the schema from the five SQL scripts, runs the fourteen-row
health check and fails if any row reads `FAIL`. It then asserts the business
rules directly: a clashing appointment must be **rejected**, a booking inside
the ten-minute buffer must be **rejected**, a legal booking must be accepted,
`sp_generate_bill` must produce a total of 28,200.00, and a second bill for the
same appointment must be refused. Two of those steps pass only when MySQL
returns an error — the shell logic inverts the exit status, so a silent
acceptance fails the build.

This is the single strongest piece of evidence in the submission. A machine that
had never seen the database built it from scratch and confirmed that the trigger
enforces the clinic's core rule. It also proves the scripts are complete and
run in order, which no local test can, because a local database accumulates
state.

**`desktop-client`** compiles and packages the Swing client, so the second
client cannot silently break against the shared API contract.

The pipeline earned its place immediately. The flaky session-expiry test
described in §6.5 was green on Linux and in CI and red on Windows; running the
same commit in more than one environment is what exposed it (Humble and Farley,
2010).

## 7.3 Deployment

`mvn verify` produces an executable jar containing an embedded Tomcat, released
on GitHub under tag `v1.0`. It runs with:

```
java -jar clinic-service-0.1.0-SNAPSHOT.jar --spring.datasource.password=...
```

The database password is supplied at runtime rather than committed. The
repository's `application.properties` carries a placeholder, and the CI job uses
a throwaway credential for its disposable container. A committed production
password cannot be un-committed from a public history.
