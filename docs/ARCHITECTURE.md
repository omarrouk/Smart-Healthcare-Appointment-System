# Architecture and Spring concepts

## Request flow

~~~mermaid
flowchart LR
    Client["Postman / HTTP client"] --> Security["Security filters and validation"]
    Security --> Controllers["Controllers and DTOs"]
    Controllers --> Services["Services and ownership checks"]
    Services --> JPA["JPA repositories"]
    Services --> Mongo["MongoDB repositories"]
    Services --> Chatbot["Chatbot interface"]
    JPA --> PostgreSQL[("PostgreSQL")]
    JPA --> Cache["Doctor entity cache"]
    Mongo --> Documents[("MongoDB")]
    Services -. "Intercepted writes" .-> AOP["Operation loggers"]
~~~

Controllers define routes and validate DTOs. Services enforce rules and map responses. Repositories handle persistence. `CurrentUser`, `AccessControlService`, and `ClinicalAccessService` derive identity/ownership from authenticated accounts.

## Relational model and concurrency

| Entity | Relationships / purpose |
| --- | --- |
| User | Unique normalized email, BCrypt hash, role, enabled |
| Doctor | One-to-one user; name and specialty |
| Patient | One-to-one user; name and optional phone |
| DoctorSlot | Many slots per doctor; immutable 30-minute interval |
| Appointment | One slot and patient; status and creation time |

Relationships are unidirectional and lazy. Appointment derives its doctor/time through its slot. Foreign keys and deletion rules preserve history. Flyway owns migrations; Hibernate validates mappings without changing tables.

JPA writes use `@Transactional`. Account/profile creation/deletion succeed together, and managed updates use dirty checking.

Aligned 30-minute slots and a unique doctor/start-time constraint prevent overlap. The availability check gives early feedback, while PostgreSQL's partial unique index `uq_appointments_occupied_slot` permits only one BOOKED or COMPLETED appointment per slot. Racing inserts cannot both succeed.

Cancellation/completion use a pessimistic row lock. The second status change observes the committed terminal state and fails instead of overwriting it. Booking during cancellation may succeed after commit or receive `409` and retry after refreshing availability.

Cancelled history stays but no longer occupies a slot; completed appointments remain occupied. Any appointment history prevents deleting its doctor, patient, or slot. Java `Instant`, PostgreSQL timestamptz, and UTC serialization provide one time reference.

## MongoDB boundary

Prescription and MedicalRecord use `@Document` and MongoRepository. They embed medicines and keep numeric PostgreSQL IDs as references.

Clinical creation requires a completed doctor-owned appointment. Records may omit its ID only when a completed appointment establishes the relationship. Completion is terminal, so cancellation cannot invalidate that authorization.

`ClinicalAccessService` checks PostgreSQL in a short read-only transaction and returns IDs. The clinical service then writes MongoDB without SQL writes, distributed transactions, or a replica-set requirement.

Mongo auditing supplies creation/update times at millisecond precision. Compound indexes support patient/author history ordering. Client version checks plus MongoDB `@Version` prevent competing copies from overwriting the winning save. Optimistic conflicts become `409`. See [Spring Data MongoDB version checks](https://docs.spring.io/spring-data/mongodb/reference/4.5/mongodb/template-crud-operations.html#mongo-template.optimistic-locking).

References stay fixed. Patients read their own documents; doctors read/update authored documents. Clinical deletion is unavailable.

## Security

HTTP filters restrict methods/routes and require CSRF for unsafe methods. `@EnableMethodSecurity` enables `@PreAuthorize` on services, including ownership expressions.

Basic Auth checks stored accounts on each request. Password hashes are not returned. Registration, clinical, and chatbot diagnostic strings redact content. Bootstrap is opt-in and skips existing admins without resetting them.

Admin account management does not imply clinical access. Profile reads allow an owning patient or doctor with a non-cancelled relationship; clinical creation requires completion. Disabled accounts cannot authenticate.

## IoC, injection, scopes, and lifecycle

Spring creates beans and supplies constructor dependencies. Controllers request services rather than constructing them. ChatbotController depends on a service interface; configuration selects either static guidance or the independent service client.

Services/aspects/cache manager use singleton scope: one per context. RequestLogContext uses request scope. Its proxy lets the singleton aspect resolve one UUID per HTTP request. Attempt/outcome IDs match within that request; non-HTTP calls use `none`. See [Spring scopes](https://docs.spring.io/spring-framework/reference/core/beans/factory-scopes.html).

DoctorCacheConfig initializes a bounded cache in a `@Bean` method. `destroyMethod="close"` supplies shutdown cleanup. Each context owns a provider so another context cannot close its manager. Tests verify initialization, singleton reuse, isolation, and destruction.

## AOP logging

`@Around` advice logs booking/cancellation/completion and prescription creation/update, then preserves the original result or exception.

| Logger | Purpose |
| --- | --- |
| `healthcare.appointments` | Booking/status changes |
| `healthcare.prescriptions` | Creation/updates |
| `healthcare.cache` | Initialization/bounds |

Operation entries contain request ID, method name, outcome, duration, and exception class. Double-booking conflicts additionally contain the public doctor name and UTC slot time/date; control characters in the name are replaced to prevent log injection. Both the availability check and the occupied-slot database constraint produce this safe conflict context. They do not serialize arguments/results or log exception messages, stacks, credentials, or clinical content.

`@Order(0)` puts logging outside transaction advice. Normal HTTP successes follow the service transaction's commit. Calls joining an outer transaction remain subject to its eventual commit/rollback. These are diagnostics rather than a persistent audit ledger.

HTTP authentication, CSRF, and validation failures that stop before the service are not service-operation events. See [Spring AOP advice](https://docs.spring.io/spring-framework/reference/core/aop/ataspectj/advice.html).

## Doctor caching

The first-level cache is the persistence context: repeated lookups return the same managed object. The second-level cache shares state between transactions, which still get separate managed instances. Hibernate statistics tests demonstrate both. See [Hibernate caching](https://docs.hibernate.org/orm/6.6/userguide/html_single/#caching).

Only Doctor uses READ_WRITE caching in the `doctors` region. Hibernate JCache and Caffeine supply a bounded in-process provider. Defaults are 1,000 entries and 300-second expiry after writes; configuration can change limits or disable it.

Committed updates refresh/invalidate state; deletion prevents reading the old entity. READ_WRITE may retain an unreadable lock marker, so physical key presence is not proof a doctor is loadable. Rollback tests verify uncommitted details are not published.

Accounts, slots, appointments, patients, and clinical documents are not cached by this feature. Doctor holds a user reference, not cached credentials/enabled flags. Query caching is disabled; searches/availability still query PostgreSQL. Authentication and service checks still execute.

Direct SQL or another independently writing process does not broadcast invalidations. Disable the local cache for that setup or adopt a shared provider deliberately. First-level caching remains normal Hibernate behavior when second-level caching is off.

## Chatbot boundary

`CHATBOT_MODE=stub` chooses static English guidance and reports `model: "stub"`. It performs no AI inference. `CHATBOT_MODE=remote` forwards the authenticated role and submitted message to the independent chatbot process with a private service key. That process selects a configurable OpenAI model per role and calls the external API. It has no SQL/MongoDB dependencies or clinical-data tools. Model prompts define workflow guidance, not medical advice or a security boundary. Provider failures become `503` without a silent stub fallback. See [service setup and role mapping](../chatbot-service/README.md).
