# Sunrise Dental Clinic — Appointment & Patient Management System

CIS6003 Advanced Programming · WRIT1 · Cardiff Metropolitan University / ICBT

**Author:** Thanujaya Hasaranga Perera  ·  **Registration number:** st20374257

A three-tier distributed system replacing the paper diary at a busy dental
clinic. The scenario names four failures of the manual process — double
bookings, lost patient records, long waiting times and billing errors — and
every design decision in this repository traces back to one of them.

## Architecture

| Tier | Technology | Responsibility |
|------|-----------|----------------|
| 1 — Presentation | Java Swing desktop client | Six screens: login, book, search, billing, reports, help |
| 2 — Business | Spring Boot REST service, port 8080 | Validation, scheduling rules, billing, reports |
| 3 — Data | MySQL 8, port 3306 | Normalised schema, trigger, stored function, stored procedure, report views |

Tiers 1 and 2 are separate operating-system processes communicating over
HTTP/JSON. That boundary is what makes the system **distributed** rather
than a layered monolith.

## Repository layout

```
db/              MySQL scripts — schema, triggers, routines, seed data, views
docs/uml/        Use case, class and sequence diagrams (PlantUML source + PNG)
clinic-service/  Tier 2 — Spring Boot REST service (Maven)
```

## Running it

```bash
# 1. Database
cd db
mysql -u root -p < 01_schema.sql
mysql -u root -p < 02_triggers.sql
mysql -u root -p < 03_functions_procedures.sql
mysql -u root -p < 04_seed_data.sql
mysql -u root -p < 05_views.sql
mysql -u root -p < 08_healthcheck.sql     # all 14 rows must say PASS

# 2. Service
cd ../clinic-service
mvn spring-boot:run                        # http://localhost:8080/api/treatments
```

Set your MySQL password in `clinic-service/src/main/resources/application.properties`
before running. In production this would come from an environment variable,
never from a file in version control.

## Testing

```bash
mvn test                                   # JUnit 5, JaCoCo report in target/site/jacoco
mysql -u root -p --force < db/06_verify.sql
```

`06_verify.sql` needs `--force` because three of its six checks are *supposed*
to fail — they prove the database rejects clashing bookings and invalid bills.

## Design patterns applied

Creational — Singleton, Factory Method, Builder
Structural — Facade, Adapter/DTO, Proxy
Behavioural — Strategy, Observer, Template Method
Architectural — MVC, Repository, DAO, layered three-tier, dependency injection

## Development approach

Test-driven. Core business rules were written as failing tests first; the
commit history shows each `test(...)` commit immediately followed by the
`feat(...)` commit that makes it pass. Work reaches `main` only through pull
requests from feature branches.
