# Demo and presentation guide

Allow approximately seven minutes. Use synthetic accounts and clinical content.

## Prepare

- Start both databases and the updated app; confirm health returns `200` and `UP`.
- Keep private admin credentials ready in Postman.
- Prepare a doctor, two patients, a future UTC-aligned slot, and a completed appointment for clinical examples.
- Keep the terminal visible for appointment/prescription operation loggers.
- Keep credentials, authorization headers, cookies/tokens, and private environment values out of shared screenshots.

The Healthcare collection's six numbered folders automate the workflows. Appointment/clinical runs retain history intentionally. Demo cleanup does not require deleting volumes or clinical history.

## Presentation outline

| Time | Show | Explain |
| --- | --- | --- |
| 0:00–0:45 | Purpose and architecture | REST API, PostgreSQL scheduling, MongoDB documents |
| 0:45–1:30 | Admin authentication and registration | BCrypt, server-assigned roles, DTO validation, DI |
| 1:30–2:45 | Patient searches and books | Identity from authentication; one active booking per slot |
| 2:45–3:30 | Second booking `409`, cancellation/rebooking | Database race protection and preserved history |
| 3:30–4:45 | Doctor completion and clinical creation | Completed ownership and numeric database references |
| 4:45–5:30 | Patient history and stale update `409` | Ownership and optimistic version checks |
| 5:30–6:15 | Chatbot and operation logs | Local role guidance, request scope, safe metadata |
| 6:15–7:00 | Doctor cache workflow and test results | Fresh reads, lifecycle cleanup, regression evidence |

## Suggested requests

For each write, fetch fresh CSRF with the same account.

1. ADMIN creates doctor, patients, and a future slot.
2. PATIENT searches by specialty and views availability.
3. PATIENT books with a slot ID: `201`, BOOKED.
4. Another patient books the same slot: `409`.
5. The owner cancels: CANCELLED. The other patient can now book.
6. DOCTOR lists appointments and completes the new booking.
7. DOCTOR creates a prescription and medical record: `201`.
8. The owning PATIENT reads both histories. Another patient cannot read a document by ID.
9. DOCTOR updates with the latest version: `200`. Reusing the old version returns `409`.
10. PATIENT asks `How do I book an appointment?` in the chatbot.
11. With a separate unused doctor: warm GET, update, fresh GET, delete, then `404`.

The used clinical doctor/patients cannot be deleted because history is protected. Use a separate unused doctor for the cache deletion example.

## Explain the evidence

- 209 Maven tests include real PostgreSQL races and competing MongoDB HTTP updates.
- Two patients racing for one slot produce one `201` and one `409`.
- Competing clinical updates produce one `200` and one `409` and preserve the winner.
- Hibernate statistics prove cache hits; faster HTTP timing alone does not.
- One organized Postman collection covers 185 saved requests and 299 assertions against a packaged application.
- The chatbot is a local stub. Operation logs are diagnostics, not a persistent audit ledger.

## Questions to prepare for

| Question | Answer |
| --- | --- |
| Why two databases? | PostgreSQL enforces scheduling relationships; MongoDB stores documents with embedded medicines. |
| How is double booking prevented? | A partial unique index protects occupied slots even if availability checks race. |
| Why no distributed transaction? | Clinical creation validates stable completed references, then writes one MongoDB document. |
| How are lost document updates prevented? | Client versions plus atomic MongoDB version matching. |
| Roles versus ownership? | Role permits the workflow; ownership limits the particular resource. |
| Why a chatbot interface? | The controller stays independent of a future provider. |
| What do scopes demonstrate? | Singleton services/cache and one log context per request. |
| Outside scope? | Frontend, real AI, uploads, full audit retention, and deployment hardening. |
