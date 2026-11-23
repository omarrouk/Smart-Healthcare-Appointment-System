# Testing and verification

## Final results

Verified on 2026-11-23 with Java 17.0.17, the Maven wrapper, PostgreSQL 17, MongoDB 8.0, and Docker Desktop.

| Check | Result |
| --- | --- |
| `.\mvnw.cmd -B -ntp clean verify` | BUILD SUCCESS |
| Maven tests | 228 across 23 classes |
| Failures / errors / skipped | 0 / 0 / 0 |
| Independent chatbot service | 14 passing tests; separate Maven build |
| One organized Postman collection | 194 saved requests, 317 passing assertions |
| Live HTTP requests | 301, including automatic CSRF fetches |
| Groq chatbot workflow with pacing | 29 saved requests, 54 HTTP requests, 52 passing assertions |
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
| AOP/scopes | 12 | Safe conflict details, log injection protection, original result/exception, ordering, request IDs |
| Chatbot | 41 | Role selection, validation, private-input omission, authentication/CSRF |
| Bootstrap/cache configuration | 9 | Safe initial admin creation, cache bounds, lifecycle/isolation |
| Appointments | 48 | Booking rules, UTC date filters, ownership, status transitions, concurrency |
| Profiles/accounts | 26 | CRUD, BCrypt registration, duplicate emails, history protection |
| Authentication/authorization | 31 | Roles, ownership, disabled accounts, generic errors, real CSRF |
| Relational persistence/cache | 16 | Constraints, relationships, L1/L2 behavior, rollback, cache-off reads |
| Clinical documents | 38 | Real Mongo persistence, authorship, history, versions, concurrent HTTP writes |
| DTO validation | 7 | Nested constraints, password byte limit, safe serialization |
| **Total** | **228** | |

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

Run **Smart Healthcare Appointment System** in its saved request order, or run an entire numbered top-level workflow folder. Nested folders depend on their workflow's setup requests.

| Workflow folder | Saved requests | Assertions | Fixture handling |
| --- | ---: | ---: | --- |
| 01 - Health and Security | 11 | 26 | No account fixtures |
| 02 - Doctors and Patients | 23 | 42 | Confirms/removes unused test profiles |
| 03 - Appointments | 49 | 75 | Retains appointment history |
| 04 - Clinical Documents | 59 | 85 | Retains appointment/clinical history |
| 05 - Chatbot | 29 | 52 | Confirms/removes unused test profiles |
| 06 - Doctor Cache | 23 | 37 | Confirms/removes unused test profiles |
| **Total** | **194** | **317** | |

Automatic CSRF fetches account for 107 additional HTTP requests. The collection declares only `healthcareBaseUrl`, `healthcareAdminEmail`, and `healthcareAdminPassword`. Shared credentials remain blank; workflows generate separate namespaced runtime variables. Other projects' collections/environments are not used.

Appointment and clinical workflows intentionally retain synthetic history because the API has no history deletion route. A rerun creates a new tagged fixture set. For workflows with cleanup, complete an interrupted run's remaining confirmation/deletion requests before restarting it. Expected error responses pass when their security or validation assertions succeed.

The consolidated local export passed all 317 assertions against the packaged application. The original workflows remain intact, with nine additional requests covering appointment date filters. A full read-back of the connected main collection verified its request definitions, scripts, descriptions, and folder order against that export. Older exports are retained under the ignored local `postman/archive/` directory as backups.

Collection exports and connection configuration remain local/ignored as requested. The [API reference](API.md) provides independent manual request examples for a fresh clone.

## Reading the logs and cache evidence

The appointment/clinical workflows exercise `healthcare.appointments` and `healthcare.prescriptions`. Match ATTEMPT and outcome lines by request ID. Authentication/CSRF/validation rejected before service invocation does not produce these service-operation entries.

The cache workflow checks warm-read correctness, committed update freshness, permissions, and deletion. Actual hits are proven by Hibernate statistics tests rather than HTTP timing. Tests also verify that disabled user state is not cached and disabling the doctor cache preserves reads.

## Independent chatbot verification

Run `.\mvnw.cmd -f chatbot-service/pom.xml verify` separately. Its fourteen tests use a local provider fixture and verify the requested model name for every role, private service-key enforcement, invalid requests, and `503` on provider failure. The backend adds five remote-client tests. These prove request/response contracts, not actual language-model generation or medical accuracy.

The consolidated Postman regression uses explicit `CHATBOT_MODE=stub` and needs no provider account. Remote assertions check authenticated role, configured model and nonempty output. A separate public API smoke test uses a local provider fixture to verify the independent service and attempts to override role/model from the client. This is protocol verification, not model inference. On 2026-11-23, an additional isolated check simulated OpenAI `429 credit_balance_exhausted` and verified that ADMIN, DOCTOR, and PATIENT requests each received the safe public `503` response without provider diagnostic content.

On 2026-11-23, a live OpenAI request returned `429 credit_balance_exhausted` with a $0.00 trial balance. Authentication was accepted; successful live generation remains unverified. Enable remote mode only when credentials and quota are available, following [OpenAI setup](../chatbot-service/README.md). No Ollama installation or model downloads are required.


## Live Groq verification

Provider configuration uses `CHATBOT_API_KEY` and `CHATBOT_API_URL`; `CHATBOT_SERVICE_KEY` still protects the internal service connection. Automated fixtures use dummy credentials. The configured Groq reasoning models use `CHATBOT_MAX_COMPLETION_TOKENS=1024`; the default remains 256. The service tests verify that the configured completion limit is forwarded to the provider.

On 2026-11-23, the packaged healthcare API and independent chatbot service passed a live test against Groq using synthetic accounts and messages. ADMIN selected `openai/gpt-oss-20b`, DOCTOR selected `openai/gpt-oss-120b`, and PATIENT selected `qwen/qwen3.8-27b`. All three returned nonempty generated replies. Client-supplied role/model fields could not override the authenticated role or configured model. Temporary application processes, databases, and credential files were cleaned up. The independent service suite also passed all 14 tests after adding a descriptive HTTP client header.

These checks demonstrate connectivity and role-based routing, not medical accuracy. Groq model availability and quotas depend on the account. OpenAI's separate zero-credit limitation does not prevent using Groq. See [Groq configuration](../chatbot-service/README.md#groq-configuration). The connected Healthcare environment contains `healthcareChatbotAdminModel`, `healthcareChatbotDoctorModel`, and `healthcareChatbotPatientModel` with the configured Groq model IDs. An optional `healthcareChatbotDelayMs=10000` spaces chatbot requests for free-tier testing; set it to `0` for offline runs. Burst tests observed Groq `429 rate_limit_exceeded`, which correctly becomes public `503` without leaking provider diagnostics.

The optional `healthcareChatbotDelayMs` Postman environment variable spaces chatbot POST requests for free-tier live runs. The Healthcare environment uses `10000` milliseconds; use `0` in offline mode. This runner setting does not change application behavior or treat provider failures as successful replies.

After the provider-setting rename, backend verification passed 228 tests and the independent service passed 14 tests. The complete offline collection passed all 317 assertions. The live Groq chatbot workflow passed all 52 assertions across 54 HTTP requests with a 10-second request delay and a 1024-token completion budget. These are separate runs; the 317-assertion regression used offline mode.
