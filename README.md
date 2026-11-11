# Smart Healthcare Appointment System

A Spring Boot 3 REST API for managing doctors, patients, appointments, prescriptions, and medical records. This university project demonstrates Spring Security, JPA/Hibernate, MongoDB, AOP, and dependency injection. It has no frontend.

## Features

- Basic Auth, BCrypt passwords, and ADMIN/DOCTOR/PATIENT roles with resource ownership.
- Admin doctor/patient management, specialty search, and patient profile updates.
- Fixed 30-minute slots with database protection against concurrent bookings.
- Cancellation, completion, and preserved appointment history.
- MongoDB clinical documents with author/patient access and optimistic version checks.
- Local chatbot guidance, AOP operation logs, request-scoped IDs, and doctor caching.

The chatbot uses English keyword rules. It does not call an AI provider or retrieve clinical content.

## Stack

| Component | Configuration |
| --- | --- |
| Java | Target 17; JDK 17 or 21 |
| Spring Boot | 3.5.16 |
| Build | Maven wrapper |
| Relational data | JPA, Hibernate, PostgreSQL 17, Flyway |
| Documents | Spring Data MongoDB, MongoDB 8.0 |
| Security | Spring Security, HTTP Basic, BCrypt, CSRF |
| Cache | Hibernate JCache with Caffeine |
| Tests | JUnit 5, Mockito, MockMvc, real databases, Postman/Newman |

The Spring Boot parent manages dependency versions.

## Run locally

Use PowerShell from the project root. Install a JDK and start Docker Desktop with Linux containers.

~~~powershell
java -version
docker info
if (!(Test-Path .env)) { Copy-Item .env.example .env }
~~~

Set nonempty `POSTGRES_PASSWORD` and `MONGO_PASSWORD` in local `.env`. Use different values with letters, digits, and hyphens so Compose and Spring parse them consistently. Preserve an existing configuration and its database credentials.

For a database without an administrator, also set:

| Variable | First-start value |
| --- | --- |
| `BOOTSTRAP_ADMIN_ENABLED` | `true` |
| `BOOTSTRAP_ADMIN_EMAIL` | Your chosen admin email |
| `BOOTSTRAP_ADMIN_PASSWORD` | Your chosen password: 8–72 characters and at most 72 UTF-8 bytes |

~~~powershell
docker compose up -d --wait
.\mvnw.cmd clean verify
.\mvnw.cmd spring-boot:run
~~~

The first build downloads Maven/dependencies. Both databases must be healthy. Tests disable admin bootstrapping and create their own fixtures.

After the first successful app startup, set `BOOTSTRAP_ADMIN_ENABLED=false` and restart. Bootstrapping skips an existing administrator; it does not reset passwords or promote another account.

Check from another terminal:

~~~powershell
Invoke-RestMethod http://localhost:8080/actuator/health
~~~

Expect `{"status":"UP"}`. GET `http://localhost:8080/api/auth/me` in Postman with Basic Auth using the administrator email/password. Expect `200` and `role: ADMIN`.

To run the packaged application instead:

~~~powershell
java -jar .\target\smart-healthcare-0.0.1-SNAPSHOT.jar
~~~

Run from the project root so `.env` is found. Stop the old app before starting another on its port. Ctrl+C stops the app; this stops databases while preserving their data:

~~~powershell
docker compose stop
~~~

## Configuration

Spring imports `.env` as a properties file; Compose reads the same file. OS environment variables override imported values.

| Variables | Default / purpose |
| --- | --- |
| `POSTGRES_HOST`, `POSTGRES_PORT` | `localhost`, `5432` |
| `POSTGRES_DB`, `POSTGRES_USER` | `smart_healthcare`, `healthcare` |
| `POSTGRES_PASSWORD` | Required |
| `MONGO_HOST`, `MONGO_PORT` | `localhost`, `27017` |
| `MONGO_DATABASE`, `MONGO_USERNAME` | `smart_healthcare`, `healthcare` |
| `MONGO_PASSWORD` | Required; authenticates against `admin` |
| `BOOTSTRAP_ADMIN_ENABLED` | `false`; opt in for initial admin creation |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Initial admin credentials |
| `SERVER_PORT` | `8080` |
| `DOCTOR_CACHE_ENABLED` | `true` |
| `DOCTOR_CACHE_MAX_ENTRIES` | `1000`; positive capacity |
| `DOCTOR_CACHE_TTL_SECONDS` | `300`; positive expiry after a cache write |

Database containers bind to `127.0.0.1` and use named volumes. Their initialization users are for local development. Initialization passwords apply when a volume is first created; editing `.env` does not change an existing database password.

Flyway owns PostgreSQL migrations; Hibernate uses `ddl-auto: validate`. Keep applied migrations unchanged. MongoDB creates clinical collections and history indexes at startup.

Basic Auth requires HTTPS beyond localhost. Credentials, local prompts, Postman files, connection configuration, logs, and build artifacts stay excluded from Git.

## Using the API

Protected requests use Basic Auth. There is no login token or authenticated session. Before POST, PUT, PATCH, or DELETE:

1. GET `/api/auth/csrf` using the same account and host.
2. Retain the returned cookie in Postman.
3. Add the token under its returned `headerName`, normally `X-XSRF-TOKEN`.
4. Send JSON with `Content-Type: application/json`.

Refresh the token for each write. A valid CSRF token does not grant authentication or permission.

| Role | Main workflow |
| --- | --- |
| ADMIN | Manage doctors/patients; create/delete unused slots |
| PATIENT | Search, book/cancel own appointments, update own profile, read own history |
| DOCTOR | View/complete own appointments; create clinical documents and update authored documents |

Clinical creation requires a completed appointment. Admin account management does not grant clinical-document access. Appointment history prevents deleting its doctor, patient, or slot.

See the [API reference](docs/API.md) for all endpoints, bodies, ownership rules, validation, and status codes.

## Architecture and verification

- [Architecture](docs/ARCHITECTURE.md): relationships, transactions, concurrency, DI, scopes, lifecycle, AOP, and caching.
- [Testing](docs/TESTING.md): coverage, fixtures, Postman order, and final results.
- [Demo guide](docs/DEMO.md): preparation, presentation outline, and expected responses.

~~~powershell
docker compose up -d --wait
.\mvnw.cmd clean verify
~~~

Final regression: **209 Maven tests**, with no failures or skipped tests. The five local Postman collections contain **299 assertions**. The testing guide records the tested environment and live checks.

Doctor caching is local to one process. Hibernate updates/deletes invalidate state; authentication and availability queries are not cached. Disable second-level caching for independently writing instances or out-of-band SQL changes.

## Troubleshooting

| Symptom | Check |
| --- | --- |
| Docker unavailable | Start Docker Desktop and wait for `docker info`. |
| Port occupied | Change the relevant port in `.env`, restart, and update the client URL. |
| `ECONNREFUSED` | Confirm the app is running and the Healthcare base URL uses its port. |
| Java/release error | Check `JAVA_HOME`, `java -version`, and `.\mvnw.cmd --version`. |
| Database authentication failure | Check existing volume credentials and `.env`; do not delete volumes as a password fix. |
| Maven download failure | Restore access to Maven Central and rerun. |
| Flyway checksum mismatch | Restore the applied migration; use another migration for changes. |
| MongoDB startup/index error | Check availability, credentials, and index-creation permission. |
| `401` | Use an enabled stored account's email/password; bootstrap settings do not reset it. |
| CSRF `403` | Fetch a fresh token on the same host and retain its cookie. |
| `Access denied` `403` | Check role and ownership. |
| Booking `409` | Refresh availability; the slot is occupied, expired, or unavailable. |
| Clinical-write `409` | Complete the owned appointment first, or reload a stale version. |
| Delete `409` | History is preserved; delete only unused profiles/slots. |
| Cache startup error | Use positive capacity/TTL and restart after changes. |

## GitHub handoff

Review and run these commands yourself after verification:

~~~powershell
git status --short
git diff --check
git add README.md pom.xml .env.example src docs
git diff --cached --stat
git diff --cached
git commit -m "Complete operation logging, regression tests, and project documentation"
git push -u origin main
~~~

If `origin` has not been configured, add it once:

~~~powershell
git remote add origin https://github.com/omarrouk/Smart-Healthcare-Appointment-System.git
~~~

Inspect the staged diff before committing. The example environment has blank credentials; keep real credentials and local tooling files excluded.
