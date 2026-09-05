# 7. Version control, continuous integration and deployment

The repository is public at `github.com/Thanujaya448/sunrise-dental-clinic`.

## 7.1 Branching and history

Work was done on short-lived `feature/` branches, each merged into `main`
through a pull request rather than committed directly (Figure 32). Commit
messages state what changed and why, and the two test-driven cycles appear as
separate red and green commits so the sequence can be verified rather than taken
on trust (Figure 31).

## 7.2 Continuous integration

`.github/workflows/ci.yml` runs on every push and pull request. It has three
jobs, one per tier, and a failure in any blocks the merge (Figure 29).

**`unit-tests`** builds on JDK 17, runs the 119 tests with JaCoCo and uploads the
reports, coverage and jar as artefacts, writing a coverage table to the run
summary.

**`database-rules`** is the job worth examining (Figure 30). GitHub starts a
real MySQL 8 container, builds the schema from the five scripts, runs the
fourteen-row health check and fails if any row reads `FAIL`. It then asserts the
business rules: a clashing appointment **rejected**, a booking inside the buffer
**rejected**, a legal booking accepted, `sp_generate_bill` totalling 28,200.00,
and a second bill refused. Two of those pass only when MySQL returns an error —
the shell inverts the exit status, so a silent acceptance fails the build. A
machine that had never seen the database built it from scratch, which proves the
scripts are complete and correctly ordered in a way no local run can: a local
database accumulates state.

**`desktop-client`** packages the Swing client, so the second client cannot
silently break against the shared contract.

The pipeline earned its place immediately: the flaky test in §6.6 was green on
Linux and red on Windows, and running the same commit in more than one
environment is what exposed it (Humble and Farley, 2010).

## 7.3 Deployment

`mvn verify` produces an executable jar with an embedded Tomcat, released under
tag `v1.0` (Figure 33). The database password and mail credentials are supplied
as command-line arguments at runtime; the repository holds only placeholders and
CI uses a throwaway credential for its disposable container.
