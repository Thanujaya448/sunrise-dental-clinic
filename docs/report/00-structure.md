# WRIT1 report — structure and word budget

Target: **4,000 words** (body text only; diagram captions, tables, code
listings, the reference list and appendices are not normally counted, but keep
the body honest at ~4,000).

Format required by the brief: A4 · margins 1.5″ left, 1″ elsewhere · page
numbers bottom-right · 1.5 line spacing · Times New Roman · headings 14pt bold ·
body 12pt · Harvard referencing.

| § | Section | Words | Task / marks it targets | Why it earns them |
|---|---|---:|---|---|
| 1 | Introduction and analysis of the scenario | 250 | Framing | Shows the four stated failures were read as the real specification, not the six numbered functions |
| 2 | Assumptions and derived requirements | 350 | All tasks | The brief explicitly invites assumptions. 13 of them, each traced to a line in the brief |
| 3 | Task A — Requirements modelling and UML | 700 | **A (20)** | Use case, domain class, design class, three sequence diagrams; the modelling *decisions*, not a description of the pictures |
| 4 | Task B — Three-tier architecture | 500 | **B (40)** | Why three *physical* tiers, the HTTP boundary, two clients over one API, Spring JDBC over JPA |
| 5 | Task B — Design patterns applied and evaluated | 1,000 | **B (40)** | Patterns from all three GoF families, each with the alternative that was rejected and why |
| 6 | Task C — Testing and TDD | 700 | **C (20)** | Strategy, derived test data, the test-double decision, TDD evidence, coverage read honestly |
| 7 | Task D — Version control, CI and documentation | 400 | **D (20)** | Branching, PRs, the three-job pipeline, the release |
| 8 | Critical evaluation, limitations and future work | 250 | **70–100 band** | The band asks you to *evaluate*. Naming your own weaknesses is what distinguishes a first |
| — | References | — | All | Harvard, ~12 sources |
| — | Appendices | — | All | Screenshots, SQL listings, test plan table, traceability matrix |

## The rubric line that decides the grade

The 70–100 band does not ask for *more* features. It asks for **justified,
evaluated decisions**. Every section below therefore follows the same shape:

> **Decision → the alternative that was rejected → why → what it cost.**

A paragraph that only describes what was built scores in the 50s. The same
paragraph with the rejected alternative and its trade-off scores in the 70s.

## Drafting order

1. §1, §2 — framing (done)
2. §5 — patterns (the biggest block of marks, 1,000 words)
3. §4 — architecture
4. §3 — UML
5. §6 — testing
6. §7 — CI
7. §8 — evaluation (written last, once everything else is on the page)
