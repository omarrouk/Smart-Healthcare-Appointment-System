# Smart Healthcare Appointment System

A university project built step by step as a Spring Boot 3 REST API. There is no frontend. **Current phase: 4 (doctors and patients).** The API supports database-backed authentication, role/ownership checks, admin CRUD for doctors/patients, patient profile updates, and doctor specialty search. Slot management and appointment operations follow in Phase 5.

## Prerequisites

| Tool | Version / purpose |
| --- | --- |
| JDK | Java 17 minimum; Java 21 LTS preferred. The project currently targets the installed Java 17. |
| Spring Boot | 3.5.16, pinned to the required Boot 3 generation |
| Maven | Official wrapper included; no global Maven installation required |
| Docker Desktop | Running with Linux containers and Docker Compose |
| PostgreSQL | 17, provided by Compose |
| MongoDB | 8.0, provided by Compose |
| Postman | API verification; PowerShell works for the health check too |

Spring Boot 3.5 requirements: https://docs.spring.io/spring-boot/3.5/system-requirements.html

## Run locally (PowerShell)

Run commands from this project's root directory. Open Docker Desktop and wait for its engine to be ready.

```powershell
java -version
docker info
```

Create local configuration **only if `.env` does not already exist**:

```powershell
Copy-Item .env.example .env
```

Edit `.env` and set different, nonempty values for `POSTGRES_PASSWORD` and `MONGO_PASSWORD`. Use letters, digits, and hyphens. These are local development credentials; never commit `.env`. If `.env` already exists, keep that file. For the first administrator, follow the bootstrap instructions in the Phase 3 section below.

```powershell
docker compose up -d --wait
.\mvnw.cmd --version
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

The first wrapper/build invocation downloads Maven and dependencies and needs internet access. `verify` runs validation, persistence, bootstrap, and security tests, then packages the project. Start the databases first. Run configuration in the IDE must also use the project root as its working directory so Spring can locate `.env`.

Expected startup message: `Started SmartHealthcareApplication`. In a second terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
docker compose ps
```

In Postman, send **GET `http://localhost:8080/actuator/health`** with **No Auth**. Expect `200 OK` and:

```json
{"status":"UP"}
```

Actuator checks PostgreSQL and MongoDB connections. Only the health endpoint is public and it does not reveal database details. API requests require authentication and their allowed roles/ownership. Unmapped routes are denied by default. GET `http://localhost:8080/` with No Auth returns `401`; an authenticated request to the same unmapped path returns `403`.

Basic Auth uses an enabled account's PostgreSQL email and password. Passwords are stored as BCrypt hashes. Each request is authenticated independently; there is no login token or authenticated session. The former temporary `setup` account has been removed. Use localhost for this setup; Basic Auth requires HTTPS when used remotely.

Stop the foreground application with Ctrl+C. Stop the local databases without deleting their data:

```powershell
docker compose stop
```

## Configuration

Spring reads `.env` as a properties file using `spring.config.import`; Docker Compose reads the same file for container configuration. No dotenv library is needed. OS environment variables override the values loaded by Spring.

| Variables | Defaults / use |
| --- | --- |
| `POSTGRES_HOST`, `POSTGRES_PORT` | `localhost`, `5432` |
| `POSTGRES_DB`, `POSTGRES_USER` | `smart_healthcare`, `healthcare` |
| `POSTGRES_PASSWORD` | Required; no committed default |
| `MONGO_HOST`, `MONGO_PORT` | `localhost`, `27017` |
| `MONGO_DATABASE`, `MONGO_USERNAME` | `smart_healthcare`, `healthcare` |
| `MONGO_PASSWORD` | Required; authenticates against `admin` |
| `BOOTSTRAP_ADMIN_ENABLED` | `false`; explicitly enable once to create the first administrator |
| `BOOTSTRAP_ADMIN_EMAIL`, `BOOTSTRAP_ADMIN_PASSWORD` | Required only when bootstrapping an administrator into a database with no admin account |
| `SERVER_PORT` | `8080` |

Compose databases bind to `127.0.0.1` only and keep data in named volumes. Their initialization users are for local development. Do not reuse these accounts as production application users. MongoDB creates the application database on the first write, so an empty database need not appear yet. Flyway creates the relational tables; `ddl-auto: validate` checks the entity mappings without changing the schema.

Java appointment times use `Instant`, PostgreSQL stores them as `TIMESTAMP WITH TIME ZONE`, and Jackson/Hibernate use UTC. API timestamps use ISO 8601 with a `Z` suffix. Clients convert them to local time for display.

## What Spring is doing

- `@SpringBootApplication` marks the entry point, enables configuration based on dependencies, and discovers Spring-managed classes under `com.smarthealthcare`.
- A **starter** is a compatible group of dependencies. Web supplies Spring MVC and embedded Tomcat; JPA supplies Hibernate and relational persistence; MongoDB supplies document persistence; Security protects requests; Validation validates input; AOP supports method interception later.
- Spring's **application context** creates and connects objects called beans. Services, controllers, and security components receive their dependencies through constructors.
- Actuator is the one setup addition: its health endpoint lets us verify the application and both database connections without inventing a business endpoint.
- `spring-boot-starter-test` includes JUnit 5, Mockito, and Spring Boot testing utilities. The current tests cover DTO validation, persistence constraints, administrator initialization, and authenticated HTTP requests. Booking service and concurrency tests will follow with their respective phases.

## Package layout

```text
src/main/java/com/smarthealthcare/
  SmartHealthcareApplication.java
  entity/
    User.java, Role.java, Doctor.java, Patient.java
    DoctorSlot.java, Appointment.java, AppointmentStatus.java
  repository/
    UserRepository.java, DoctorRepository.java, PatientRepository.java
    DoctorSlotRepository.java, AppointmentRepository.java
  dto/
    UserRegistrationRequest.java, CreateDoctorRequest.java, CreatePatientRequest.java
    UserResponse.java, DoctorResponse.java, PatientResponse.java
    DoctorSlotResponse.java, AppointmentResponse.java
    ApiError.java, CsrfTokenResponse.java
    UpdateDoctorRequest.java, UpdatePatientRequest.java, DoctorSummaryResponse.java
  config/
    SecurityConfig.java, AdminInitializer.java
  security/
    DatabaseUserDetailsService.java, CurrentUser.java
    AccessControlService.java, SecurityErrorHandler.java
  controller/
    AuthController.java, DoctorController.java, PatientController.java
  service/
    DoctorService.java, PatientService.java, UserAccountService.java
  exception/
    ResourceNotFoundException.java, GlobalExceptionHandler.java
    ResourceConflictException.java, InvalidRequestException.java
src/main/resources/
  application.yml
  db/migration/V1__create_relational_model.sql
src/test/java/com/smarthealthcare/
  dto/RequestValidationTest.java
  repository/RelationalModelTest.java
  config/AdminInitializerTest.java
  security/DatabaseUserDetailsServiceTest.java
  security/SecurityIntegrationTest.java
  controller/ProfileManagementIntegrationTest.java
  service/UserAccountServiceTest.java
```

The remaining packages (`document`, `aop`, and `chatbot`) will be added when their first class is needed.

## Phase 2: relational model

### 1. Entities and relationships

An `@Entity` is a Java class mapped to a database table. Hibernate uses its annotations to read and save rows. The protected no-argument constructor lets Hibernate instantiate an entity; application code uses the constructor with required fields.

| Entity / table | Relationship and purpose |
| --- | --- |
| `User` / `app_users` | Unique, lowercase email; password hash; `ADMIN`, `DOCTOR`, or `PATIENT` role; enabled flag |
| `Doctor` / `doctors` | One profile per user account, with name and specialty |
| `Patient` / `patients` | One profile per user account, with name and optional phone number |
| `DoctorSlot` / `doctor_slots` | Many slots belong to one doctor; each has a UTC start and end |
| `Appointment` / `appointments` | Many historical appointments can reference a slot; each belongs to one patient |

`@OneToOne` maps a profile to its account; `@ManyToOne` maps several rows to a shared parent. Relationships are unidirectional and lazy to keep the model small and avoid loading related data unnecessarily. There is no cascading deletion: referenced rows cannot be deleted while dependent rows exist. The role enum describes the account; the security layer enforces route and service access.

Availability uses explicit, fixed 30-minute slots starting on an hour or half-hour boundary in UTC. For example, `2030-01-07T10:00:00Z` ends at `10:30:00Z`. Unique `(doctor_id, start_time)` plus database checks for alignment and duration prevent duplicate or overlapping slots for a doctor.

An appointment references `slot_id` instead of repeating `doctor_id`, `start_time`, and `end_time`. Those values come from the slot and remain present in `AppointmentResponse`. Slot identity and times cannot be changed through the entity after creation.

The partial unique index `uq_appointments_occupied_slot` allows at most one `BOOKED` or `COMPLETED` appointment per slot. `CANCELLED` rows remain as history and do not block a replacement booking. PostgreSQL enforces this even when writes come from separate transactions. Booking operations, conflict responses, authorization, and a concurrency test will be implemented in later phases.

### 2. Repositories and DTOs

Each repository extends `JpaRepository<Entity, Long>`, which supplies persistence operations. Spring creates the implementation as a bean. Methods such as `findByEmailIgnoreCase` and `findBySpecialtyIgnoreCase` become queries based on their names. Repository access will be wrapped in authorized service methods when endpoints are introduced.

Request/response DTOs are Java records that define API data independently of the entities. `@NotBlank`, `@Email`, and `@Size` constrain input; `@Valid` also validates the nested account in doctor/patient creation requests. Password validation also checks BCrypt's 72-byte UTF-8 limit. Responses omit password hashes; registration passwords are excluded from serialization and their record's `toString()`. User entities also exclude hashes from JSON as a fallback. There are no hardcoded database accounts or passwords; the optional administrator initializer reads its credentials from local configuration.

### 3. Database migration

Flyway runs `db/migration/V1__create_relational_model.sql` automatically before Hibernate validates the schema. It tracks applied migrations in `flyway_schema_history`, so subsequent startups do not recreate the tables. `flyway-core` and `flyway-database-postgresql` are the only new dependencies; Spring Boot manages their versions. See [Spring Boot's migration documentation](https://docs.spring.io/spring-boot/3.5/how-to/data-initialization.html#howto.data-initialization.migration-tool.flyway).

Keep an applied migration unchanged. Future schema changes belong in a new versioned SQL file, such as `V2__describe_the_change.sql`. Flyway does not seed business data or change MongoDB.

### 4. Verify Phase 2

Stop any earlier app instance with Ctrl+C, then run:

```powershell
docker compose up -d --wait
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

The tests cover nested DTO validation, password serialization, persisted relationships and repository queries, unique emails, slot boundaries/duration, duplicate bookings, rebooking after cancellation, and completed-slot protection. Persistence tests use the local PostgreSQL database configured in `.env`; their inserted rows and changes roll back after each test. Flyway schema migrations persist, and identity counters may advance. Use this local development configuration when running them.

Some tests deliberately violate database constraints, so PostgreSQL constraint errors can appear in the logs even when the assertions pass. Check the final Maven summary for zero failures/errors and `BUILD SUCCESS`.

Startup should report a successful migration on the first run, or that the schema is up to date on subsequent runs. In Postman, GET `http://localhost:8080/actuator/health` with No Auth must still return `200` and `{"status":"UP"}`. GET `/` with No Auth and `Accept: application/json` must still return `401`. There are no new HTTP endpoints in Phase 2.

To inspect the schema using the default local database/user names:

```powershell
docker compose exec postgres psql -U healthcare -d smart_healthcare -c '\dt'
docker compose exec postgres psql -U healthcare -d smart_healthcare -c 'SELECT version, description, success FROM flyway_schema_history;'
```

Expect `app_users`, `doctors`, `patients`, `doctor_slots`, `appointments`, and `flyway_schema_history`. Tests roll back their fixture rows. An administrator created during application startup remains in `app_users`.

## Phase 3: authentication and authorization

### 1. Create the first administrator

In your ignored `.env`, set:

```properties
BOOTSTRAP_ADMIN_ENABLED=true
BOOTSTRAP_ADMIN_EMAIL=your-admin-email@example.com
BOOTSTRAP_ADMIN_PASSWORD=your-unique-local-password
```

Use your own password: 8–72 characters and at most 72 UTF-8 bytes. The values above are placeholders. Start the app with `.\mvnw.cmd spring-boot:run`. `AdminInitializer` validates the configuration and creates an enabled `ADMIN` with a BCrypt hash, only if no admin account exists. It never resets an existing admin password, promotes another account, or creates doctor/patient demo accounts. Invalid bootstrap configuration stops startup rather than creating a weak or incorrectly assigned account.

After verifying the administrator with `/api/auth/me`, set `BOOTSTRAP_ADMIN_ENABLED=false` in `.env`. Keep the email/password privately for local login; changing these bootstrap values later does not change the stored account. The old `BOOTSTRAP_USERNAME` and `BOOTSTRAP_PASSWORD` settings are no longer used.

### 2. How authentication works

`SecurityFilterChain` runs before controllers. HTTP Basic extracts credentials from the `Authorization` header, `DatabaseUserDetailsService` loads the account by email, and `DaoAuthenticationProvider` uses the `PasswordEncoder` bean to check the BCrypt hash. Email lookup is case-insensitive. Disabled accounts, unknown emails, and wrong passwords receive the same generic `401` response. See [Spring Security's authentication provider documentation](https://docs.spring.io/spring-security/reference/6.5/servlet/authentication/passwords/dao-authentication-provider.html).

Spring supplies `Authentication` through `SecurityContextHolder` for the current request. `CurrentUser` resolves that identity from the database. The client does not choose a role or provide a trusted user ID. Sessions and form login are disabled; send Basic credentials on every protected request.

### 3. Roles and ownership

| Method and path | Allowed access |
| --- | --- |
| GET `/actuator/health` | Public; aggregate health only |
| GET `/api/auth/me` | Any authenticated, enabled account; returns its own ID/email/role |
| GET `/api/auth/csrf` | Any authenticated account; returns a CSRF header name and token |
| GET `/api/doctors/me` | `DOCTOR`; its own profile |
| GET `/api/patients/me` | `PATIENT`; its own profile |
| GET `/api/patients/{patientId}` | `ADMIN`, the owning `PATIENT`, or a `DOCTOR` with a `BOOKED`/`COMPLETED` appointment for that patient |

`@EnableMethodSecurity` activates `@PreAuthorize` checks on services. The patient lookup calls `AccessControlService` before returning a profile, so knowing another patient's ID does not grant access. A cancelled appointment alone does not establish a doctor/patient relationship. Completed appointments preserve access to the patient profile for follow-up care. Admin profile access does not imply access to medical records; those permissions will be implemented with MongoDB endpoints in Phase 6.

There is no public registration or role-change endpoint. Admin creation of doctor/patient accounts and CRUD/search are described in Phase 4 below. An account with a clinical role but no corresponding profile receives `404` from its `/me` profile endpoint.

### 4. CSRF protection and errors

CSRF protection remains enabled because browsers can automatically resend Basic Auth credentials. `SessionCreationPolicy.STATELESS` does not make Basic Auth immune to CSRF. Tokens are stored in an HttpOnly `XSRF-TOKEN` cookie instead of an HTTP session. See [Spring Security's CSRF documentation](https://docs.spring.io/spring-security/reference/6.5/servlet/exploits/csrf.html).

For POST/PUT/PATCH/DELETE operations, first send an authenticated GET `/api/auth/csrf`. Keep the returned cookie and send the JSON `token` value in the header named by `headerName` (`X-XSRF-TOKEN`). Postman keeps the cookie in its cookie jar. Use the token returned in JSON, which can differ from the cookie value because Spring masks it. These operations also require their role/ownership checks; a CSRF token grants no additional permissions.

Security-filter and controller errors share this format:

```json
{
  "timestamp": "2030-01-07T10:00:00Z",
  "status": 403,
  "error": "Forbidden",
  "message": "Access denied",
  "path": "/api/patients/123",
  "fieldErrors": {}
}
```

The timestamp and path above are illustrative. `401` means authentication is missing or invalid; `403` means access is denied or a CSRF token is missing/invalid; `404` means an authorized lookup found no profile; `400` covers invalid request parameters or validation failures. Rejected values and password hashes are not included in errors.

### 5. Verify Phase 3

```powershell
docker compose up -d --wait
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

In Postman, select Basic Auth, enter the configured administrator email as the username and its password, then send GET `http://localhost:8080/api/auth/me`. Expect `200` with `role: ADMIN`, `enabled: true`, and no password/hash fields. Removing authentication or entering a wrong password must return `401`. With the same admin credentials, GET `/api/doctors/me` and `/api/patients/me` must return `403` because those endpoints represent the caller's clinical profile.

The security tests use real BCrypt-backed accounts and MockMvc requests against the full Spring context. They cover all three roles, disabled accounts, request-by-request authentication, profile ownership, related/unrelated doctors, cancelled/completed relationships, consistent errors, and the real CSRF cookie/token flow. Their accounts, profiles, slots, and appointments roll back after each test. The bootstrap initializer is disabled in the full-context test and tested separately with Mockito. No new dependencies or schema migrations were needed for Phase 3.

## Phase 4: doctors and patients

### Endpoints

All write requests require Basic Auth, the CSRF cookie, and the matching `X-XSRF-TOKEN` header. JSON bodies use `Content-Type: application/json`.

| Method and path | Access | Result |
| --- | --- | --- |
| POST `/api/doctors` | ADMIN | `201` with a doctor profile and `Location` header |
| GET `/api/doctors` | ADMIN, PATIENT | List of doctor summaries |
| GET `/api/doctors?specialty=Cardiology` | ADMIN, PATIENT | Case-insensitive exact specialty matches |
| GET `/api/doctors/{doctorId}` | ADMIN | Full doctor profile and safe account details |
| PUT `/api/doctors/{doctorId}` | ADMIN | Update name/specialty; `200` |
| DELETE `/api/doctors/{doctorId}` | ADMIN | Delete an unused doctor and its login; `204` |
| POST `/api/patients` | ADMIN | `201` with a patient profile and `Location` header |
| GET `/api/patients` | ADMIN | List of patient profiles |
| GET `/api/patients/{patientId}` | Existing Phase 3 ownership rules | One patient profile |
| PUT `/api/patients/{patientId}` | ADMIN | Update name/phone; `200` |
| PUT `/api/patients/me` | PATIENT | Update only the caller's name/phone; `200` |
| DELETE `/api/patients/{patientId}` | ADMIN | Delete an unused patient and its login; `204` |

The existing doctor/patient GET `/me` endpoints are unchanged. A doctor can read its own profile but cannot administer accounts. Patients cannot list other patients or use administrative write routes, including PUT by ID. Their update route is PUT `/api/patients/me`.

### Request bodies

Create a doctor as ADMIN:

```json
{
  "account": {
    "email": "doctor@example.com",
    "password": "replace-with-your-own-password"
  },
  "name": "Doctor Name",
  "specialty": "Cardiology"
}
```

Create a patient as ADMIN:

```json
{
  "account": {
    "email": "patient@example.com",
    "password": "replace-with-your-own-password"
  },
  "name": "Patient Name",
  "phone": "+970123456789"
}
```

Choose your own unique local passwords; the examples are placeholders. The server assigns `DOCTOR` or `PATIENT` from the endpoint. Request bodies cannot select an administrator role. Duplicate emails, including case variations across roles, return `409`; the database unique constraint also protects concurrent registrations. Passwords are BCrypt-encoded before saving and never returned.

Update a doctor with `{"name":"Updated Doctor","specialty":"Neurology"}`. Update a patient with `{"name":"Updated Patient","phone":"12345"}`. Names and specialties are trimmed. Phone is optional; null or blank clears it. These updates do not change the email, password, enabled flag, or role. Empty names/specialties and oversized values return `400` with field errors. Missing profiles return `404` for authorized callers.

Doctor search returns only `id`, `name`, and `specialty`, without account IDs, login emails, or password hashes. Omit the specialty parameter to list all doctors. A blank or overlong specialty is rejected with `400`; an unknown specialty returns an empty list. Available-slot management remains in Phase 5.

### Transactions and deletion rules

`@Transactional` groups account/profile creation and deletion into one database operation: failures roll back the group. Updates load a managed entity and change its fields inside a write transaction; Hibernate detects and saves those changes. See [Spring Data JPA transactionality](https://docs.spring.io/spring-data/jpa/reference/jpa/transactions.html). Patient lists use `@EntityGraph(attributePaths = "user")` to fetch the needed account data with the profiles instead of issuing a separate account query for each row.

An account/profile with any appointment history cannot be deleted. This includes `BOOKED`, `CANCELLED`, and `COMPLETED` appointments; the API returns `409` and preserves the account, profile, and history. An unused doctor's unbooked slots are removed as part of its deletion. Deleting an unused patient or doctor also removes its login, so later Basic Auth attempts return `401`. Database foreign keys remain a final safeguard against concurrent conflicting writes. There are no cascading deletes of appointment or medical history.

### Verify Phase 4

Stop the old application before running the updated version:

```powershell
docker compose up -d --wait
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

In Postman, authenticate as ADMIN, GET `/api/auth/csrf`, then POST a doctor/patient using the example body, returned token, and cookie. Expect `201`. Sign in with the new clinical account and read its `/me` profile. As PATIENT, search doctors and PUT your own profile. Administrative creation/updates/deletion must return `403` for clinical users, even with a valid CSRF token.

The tests cover registration and BCrypt login, server-assigned roles, nested validation, duplicate emails, search privacy, persisted updates, self-update restrictions, deletion/account removal, history protection across all appointment statuses, and service-level permissions. They use the actual CSRF endpoint rather than replacing the filter's cookie repository. Their fixture data rolls back. Phase 4 adds no dependencies or schema migrations.

## Common setup errors

- **Docker cannot connect to its engine:** start Docker Desktop, select Linux containers, and wait until `docker info` succeeds.
- **Port already allocated:** change `POSTGRES_PORT`, `MONGO_PORT`, or `SERVER_PORT` in `.env`, then rerun Compose and restart the app. There is no need to stop an unrelated service.
- **Java/release error:** `java -version` and `JAVA_HOME` must point to a JDK 17 or 21 (not a Java 8 JRE). Maven prints the Java runtime it uses with `.\mvnw.cmd --version`.
- **Missing placeholder or database authentication failure:** run from the project root, check `.env`, and ensure both database passwords are nonempty. Existing OS environment variables may override Spring settings.
- **Database password changed after first startup:** initialization credentials apply only when a database volume is first created. Restore the original local password or deliberately change the database user's password. Do not delete volumes to fix this without deciding whether their data can be lost.
- **Maven download fails:** check internet/proxy access to `repo.maven.apache.org`; retry the same command after access is fixed.
- **Health returns `503 DOWN`:** check `docker compose ps` and local application logs. Both databases must be healthy and credentials must match. Avoid sharing logs containing credentials.
- **Tests cannot connect to PostgreSQL:** start Compose, check that PostgreSQL is healthy, and verify the connection values in `.env`.
- **Flyway checksum mismatch:** restore the original applied migration and put subsequent schema changes in a new versioned migration. Do not delete database volumes or edit Flyway history to bypass the error.
- **Hibernate reports a missing table/column:** check the earlier Flyway startup messages and ensure migrations are enabled. Keep `ddl-auto: validate`; changing it to `update` hides the schema mismatch.
- **Basic Auth returns `401`:** use the PostgreSQL account email, not the old `setup` username. Check that the admin was initialized and is enabled. Bootstrap configuration does not reset an existing account's password.
- **A profile request returns `403`:** check the caller's role and patient ownership/appointment relationship. Admin credentials do not grant a doctor's or patient's personal `/me` profile.
- **An unsafe request reports a CSRF error:** fetch `/api/auth/csrf` using the same host, keep its cookie, and send the returned token in `X-XSRF-TOKEN`. A valid token still cannot enable an unimplemented route.

Confirm Phase 4 works before starting Phase 5 (slot management, booking, cancellation, and completion).
