# Smart Healthcare Appointment System

A university project built step by step as a Spring Boot 3 REST API. There is no frontend. **Current phase: 2 (relational model).** Entities, repositories, validated DTOs, and the initial PostgreSQL migration are implemented. Business endpoints will be added in later phases.

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

Edit `.env` and set different, nonempty values for `POSTGRES_PASSWORD`, `MONGO_PASSWORD`, and `BOOTSTRAP_PASSWORD`. Use letters, digits, and hyphens. These are local development credentials; never commit `.env`. If `.env` already exists, keep that file.

```powershell
docker compose up -d --wait
.\mvnw.cmd --version
.\mvnw.cmd verify
.\mvnw.cmd spring-boot:run
```

The first wrapper/build invocation downloads Maven and dependencies and needs internet access. `verify` runs validation and PostgreSQL persistence tests, then packages the project. Start the databases first. Run configuration in the IDE must also use the project root as its working directory so Spring can locate `.env`.

Expected startup message: `Started SmartHealthcareApplication`. In a second terminal:

```powershell
Invoke-RestMethod http://localhost:8080/actuator/health
docker compose ps
```

In Postman, send **GET `http://localhost:8080/actuator/health`** with **No Auth**. Expect `200 OK` and:

```json
{"status":"UP"}
```

Actuator checks PostgreSQL and MongoDB connections. Only the health endpoint is exposed and it does not reveal database details. Other requests require authentication under Spring Security's default configuration. For example, GET `http://localhost:8080/` with No Auth and `Accept: application/json` returns `401`. A missing route can still return `404` after successful authentication; we have not created business controllers yet.

The temporary Basic Auth credentials come from `BOOTSTRAP_USERNAME` and `BOOTSTRAP_PASSWORD` in your local `.env`. This is Spring's setup user, not an admin/doctor/patient account. Database-backed users, BCrypt, and role/ownership checks are Phase 3 work. Use localhost for this setup; Basic Auth needs HTTPS when used remotely.

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
| `BOOTSTRAP_USERNAME`, `BOOTSTRAP_PASSWORD` | Temporary setup user (`setup`); password required |
| `SERVER_PORT` | `8080` |

Compose databases bind to `127.0.0.1` only and keep data in named volumes. Their initialization users are for local development. Do not reuse these accounts as production application users. MongoDB creates the application database on the first write, so an empty database need not appear yet. Flyway creates the relational tables; `ddl-auto: validate` checks the entity mappings without changing the schema.

Java appointment times use `Instant`, PostgreSQL stores them as `TIMESTAMP WITH TIME ZONE`, and Jackson/Hibernate use UTC. API timestamps use ISO 8601 with a `Z` suffix. Clients convert them to local time for display.

## What Spring is doing

- `@SpringBootApplication` marks the entry point, enables configuration based on dependencies, and discovers Spring-managed classes under `com.smarthealthcare`.
- A **starter** is a compatible group of dependencies. Web supplies Spring MVC and embedded Tomcat; JPA supplies Hibernate and relational persistence; MongoDB supplies document persistence; Security protects requests; Validation validates input; AOP supports method interception later.
- Spring's **application context** creates and connects objects called beans. Later services will receive their dependencies through constructors. No manual dependency wiring is needed in this minimal entry point.
- Actuator is the one setup addition: its health endpoint lets us verify the application and both database connections without inventing a business endpoint.
- `spring-boot-starter-test` includes JUnit 5, Mockito, and Spring Boot testing utilities. Phase 2 uses JUnit and Spring Data JPA tests; service and concurrency tests will follow with their respective phases.

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
src/main/resources/
  application.yml
  db/migration/V1__create_relational_model.sql
src/test/java/com/smarthealthcare/
  dto/RequestValidationTest.java
  repository/RelationalModelTest.java
```

The remaining packages (`controller`, `service`, `document`, `security`, `config`, `exception`, `aop`, and `chatbot`) will be added when their first class is needed.

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

`@OneToOne` maps a profile to its account; `@ManyToOne` maps several rows to a shared parent. Relationships are unidirectional and lazy to keep the model small and avoid loading related data unnecessarily. There is no cascading deletion: referenced rows cannot be deleted while dependent rows exist. Role-to-profile checks will be enforced by services in the security/CRUD phases; a role enum alone does not authorize requests.

Availability uses explicit, fixed 30-minute slots starting on an hour or half-hour boundary in UTC. For example, `2030-01-07T10:00:00Z` ends at `10:30:00Z`. Unique `(doctor_id, start_time)` plus database checks for alignment and duration prevent duplicate or overlapping slots for a doctor.

An appointment references `slot_id` instead of repeating `doctor_id`, `start_time`, and `end_time`. Those values come from the slot and remain present in `AppointmentResponse`. Slot identity and times cannot be changed through the entity after creation.

The partial unique index `uq_appointments_occupied_slot` allows at most one `BOOKED` or `COMPLETED` appointment per slot. `CANCELLED` rows remain as history and do not block a replacement booking. PostgreSQL enforces this even when writes come from separate transactions. Booking operations, conflict responses, authorization, and a concurrency test will be implemented in later phases.

### 2. Repositories and DTOs

Each repository extends `JpaRepository<Entity, Long>`, which supplies persistence operations. Spring creates the implementation as a bean. Methods such as `findByEmailIgnoreCase` and `findBySpecialtyIgnoreCase` become queries based on their names. Repository access will be wrapped in authorized service methods when endpoints are introduced.

Request/response DTOs are Java records that define API data independently of the entities. `@NotBlank`, `@Email`, and `@Size` constrain input; `@Valid` also validates the nested account in doctor/patient creation requests. Validation is tested now and will be invoked by controllers when those are added. Responses omit password hashes; registration passwords are excluded from serialization and their record's `toString()`. User entities also exclude hashes from JSON as a fallback. There are no default accounts or seeded passwords in PostgreSQL.

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

Expect `app_users`, `doctors`, `patients`, `doctor_slots`, `appointments`, and `flyway_schema_history`. Business tables remain empty after rolled-back tests.

## Common setup errors

- **Docker cannot connect to its engine:** start Docker Desktop, select Linux containers, and wait until `docker info` succeeds.
- **Port already allocated:** change `POSTGRES_PORT`, `MONGO_PORT`, or `SERVER_PORT` in `.env`, then rerun Compose and restart the app. There is no need to stop an unrelated service.
- **Java/release error:** `java -version` and `JAVA_HOME` must point to a JDK 17 or 21 (not a Java 8 JRE). Maven prints the Java runtime it uses with `.\mvnw.cmd --version`.
- **Missing placeholder or authentication failure:** run from the project root, check `.env`, and ensure all three passwords are nonempty. Existing OS environment variables may override Spring settings.
- **Database password changed after first startup:** initialization credentials apply only when a database volume is first created. Restore the original local password or deliberately change the database user's password. Do not delete volumes to fix this without deciding whether their data can be lost.
- **Maven download fails:** check internet/proxy access to `repo.maven.apache.org`; retry the same command after access is fixed.
- **Health returns `503 DOWN`:** check `docker compose ps` and local application logs. Both databases must be healthy and credentials must match. Avoid sharing logs containing credentials.
- **Tests cannot connect to PostgreSQL:** start Compose, check that PostgreSQL is healthy, and verify the connection values in `.env`.
- **Flyway checksum mismatch:** restore the original applied migration and put subsequent schema changes in a new versioned migration. Do not delete database volumes or edit Flyway history to bypass the error.
- **Hibernate reports a missing table/column:** check the earlier Flyway startup messages and ensure migrations are enabled. Keep `ddl-auto: validate`; changing it to `update` hides the schema mismatch.

Confirm Phase 2 works before starting Phase 3 (database-backed authentication and authorization).
