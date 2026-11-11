# Testing and verification

## Final results

Verified on 2026-11-11 with Java 17.0.17, the Maven wrapper, PostgreSQL 17, MongoDB 8.0, and Docker Desktop.

| Check | Result |
| --- | --- |
| `.\mvnw.cmd -B -ntp clean verify` | BUILD SUCCESS |
| Maven tests | 209 across 22 classes |
| Failures / errors / skipped | 0 / 0 / 0 |
| Five Postman collections | 299 passing assertions |
| Live HTTP requests | 292, including automatic CSRF fetches |
| Dedicated operation-log check | 46 entries verified for expected outcomes and absence of clinical content |
| Packaged application | Started successfully against temporary PostgreSQL/MongoDB |
| Temporary live-test resources | Application, containers, and credential files cleaned up |

The live check ran the packaged JAR on a temporary port and used temporary database containers. It did not reset the development volumes or stop another application instance. Results describe this local configuration.

## Run the suite

From the project root:

~~~powershell
docker compose up -d --wait
.\mvnw.cmd clean verify
~~~

The application does not need to be running on port 8080 for Maven tests. MockMvc runs the full Spring configuration in-process. Both configured databases must be available because full-context tests initialize JPA and MongoDB indexes.

To run selected integration checks:

~~~powershell
.\mvnw.cmd "-Dtest=AppointmentConcurrencyTest,ClinicalDocumentConcurrencyTest" test
.\mvnw.cmd "-Dtest=DoctorCacheIntegrationTest,DoctorCacheDisabledTest" test
~~~

Surefire writes reports under `target/surefire-reports`. Some tests intentionally violate constraints, so expected database error messages can appear even on a successful run. Judge the final test summary and exit code.

## Coverage map

| Area | Tests | Main assertions |
| --- | ---: | --- |
| AOP/scopes | 11 | Metadata-only events, original result/exception, ordering, request IDs |
| Chatbot | 36 | Role selection, validation, private-input omission, authentication/CSRF |
| Bootstrap/cache configuration | 9 | Safe initial admin creation, cache bounds, lifecycle/isolation |
| Appointments | 35 | Booking rules, ownership, status transitions, concurrency |
| Profiles/accounts | 26 | CRUD, BCrypt registration, duplicate emails, history protection |
| Authentication/authorization | 31 | Roles, ownership, disabled accounts, generic errors, real CSRF |
| Relational persistence/cache | 16 | Constraints, relationships, L1/L2 behavior, rollback, cache-off reads |
| Clinical documents | 38 | Real Mongo persistence, authorship, history, versions, concurrent HTTP writes |
| DTO validation | 7 | Nested constraints, password byte limit, safe serialization |
| **Total** | **209** | |

## Concurrent cases

1. Two patients book one slot concurrently: exactly one `201` and one `409`; one active row remains.
2. Cancellation races completion: exactly one `200` and one `409`; one terminal status survives.
3. Cancellation races rebooking: cancellation succeeds; booking either succeeds after release or returns `409` and can retry afterward. Cancelled history and one new active booking remain.
4. Two prescription PUTs use one version: one `200` and one `409`; the winner's content/version is preserved.
5. The same race is tested for medical records.

Workers use independent request transactions and bounded waits. A database uniqueness/index test and direct Mongo version-copy test supplement the HTTP races.

## Fixtures and transaction boundaries

Use development databases. Ordinary JPA integration fixtures roll back after each test. Concurrency, AOP, and cross-transaction cache tests commit unique fixtures so independent requests/transactions can see them, then explicitly remove only those owned rows.

Mongo writes are independent of JPA rollback. Clinical tests explicitly remove their own documents by fixture patient IDs before removing SQL references. They do not clear whole collections/databases or delete development volumes. Flyway metadata, indexes, and identity counters may persist or advance.

Full-context tests disable bootstrap. Test passwords/hashes are synthetic, and no production credentials are embedded.

## Postman workflows

Select **Smart Healthcare - Local** and set private local values for `healthcareAdminEmail` and `healthcareAdminPassword`. `healthcareBaseUrl` defaults to `http://localhost:8080`. These names avoid generic variables belonging to other projects.

Run each collection in its saved request order:

| Collection | Saved requests | Assertions | Fixture handling |
| --- | ---: | ---: | --- |
| Smart Healthcare Appointment System | 34 | 68 | Confirms/removes unused test profiles |
| Smart Healthcare - Phase 5 Appointments | 40 | 57 | Retains appointment history |
| Smart Healthcare - Phase 6 Clinical Documents | 59 | 85 | Retains appointment/clinical history |
| Smart Healthcare - Phase 7 Chatbot | 29 | 52 | Confirms/removes unused test profiles |
| Smart Healthcare - Phase 8 Doctor Cache | 23 | 37 | Confirms/removes unused test profiles |
| **Total** | **185** | **299** | |

Automatic CSRF fetches account for 107 additional HTTP requests. Shared credential/test values remain blank; each collection has its own namespaced runtime variables. Other projects' collections/environments are not used.

Appointment and clinical collections intentionally retain synthetic history because the API has no history deletion route. A rerun creates a new tagged fixture set. For the cleanup collections, complete an interrupted run's remaining confirmation/deletion requests before restarting it.

The final workspace review verified all five connected collections against the tested local versions.

Collection exports and connection configuration remain local/ignored as requested. The [API reference](API.md) provides independent manual request examples for a fresh clone.

## Reading the logs and cache evidence

The appointment/clinical collections exercise `healthcare.appointments` and `healthcare.prescriptions`. Match ATTEMPT and outcome lines by request ID. Authentication/CSRF/validation rejected before service invocation does not produce these service-operation entries.

The cache collection checks warm-read correctness, committed update freshness, permissions, and deletion. Actual hits are proven by Hibernate statistics tests rather than HTTP timing. Tests also verify that disabled user state is not cached and disabling the doctor cache preserves reads.
