# API reference

Base URL: `http://localhost:8080`. PostgreSQL IDs are numbers; MongoDB document IDs are strings. Timestamps use ISO 8601 UTC. Use synthetic clinical content for development.

## Authentication and errors

Protected routes require Basic Auth with an enabled account's email/password. Writes also require a fresh CSRF cookie/token from GET `/api/auth/csrf` using the same account and host. Retain the cookie and send the token under its returned `headerName`, normally `X-XSRF-TOKEN`. JSON bodies use `Content-Type: application/json`.

| Method | Path | Access | Result |
| --- | --- | --- | --- |
| GET | `/actuator/health` | Public | `200` aggregate health |
| GET | `/api/auth/me` | Authenticated | `200` account identity/role |
| GET | `/api/auth/csrf` | Authenticated | `200` header name and token |

There is no login body, JWT, or authenticated session. Unsupported paths are denied by default. Errors have a common shape without rejected values, stack traces, SQL details, or password hashes:

~~~json
{
  "timestamp":"2026-11-11T12:00:00Z",
  "status":400,
  "error":"Bad Request",
  "message":"Validation failed",
  "path":"/api/appointments",
  "fieldErrors":{"slotId":"must be greater than 0"}
}
~~~

| Status | Meaning |
| --- | --- |
| `400` | Invalid body/parameter, validation failure, or slot alignment |
| `401` | Missing/invalid credentials or disabled account |
| `403` | Missing/invalid CSRF or denied role/ownership |
| `404` | Authorized lookup of a missing resource |
| `409` | Duplicate/occupied resource, invalid transition, stale version, or protected history |
| `500` | Unexpected failure with a generic message |

A write rejected by CSRF can return `403` before authentication is checked.

## Doctors and patients

| Method | Path | Access | Result |
| --- | --- | --- | --- |
| POST | `/api/doctors` | ADMIN | `201` profile and Location |
| GET | `/api/doctors` | ADMIN, PATIENT | `200` doctor summaries |
| GET | `/api/doctors?specialty=Cardiology` | ADMIN, PATIENT | `200` case-insensitive exact matches |
| GET | `/api/doctors/me` | DOCTOR | `200` own profile |
| GET | `/api/doctors/{doctorId}` | ADMIN | `200` full profile |
| PUT | `/api/doctors/{doctorId}` | ADMIN | `200` name/specialty updated |
| DELETE | `/api/doctors/{doctorId}` | ADMIN | `204` unused profile/login removed |
| POST | `/api/patients` | ADMIN | `201` profile and Location |
| GET | `/api/patients` | ADMIN | `200` patient profiles |
| GET | `/api/patients/me` | PATIENT | `200` own profile |
| GET | `/api/patients/{patientId}` | ADMIN, owning PATIENT, related DOCTOR | `200` one profile |
| PUT | `/api/patients/me` | PATIENT | `200` own name/phone updated |
| PUT | `/api/patients/{patientId}` | ADMIN | `200` name/phone updated |
| DELETE | `/api/patients/{patientId}` | ADMIN | `204` unused profile/login removed |

A doctor is related for profile reads when a BOOKED or COMPLETED appointment connects them. Cancelled appointments alone do not grant access. Clinical rules below are stricter.

Register a doctor:

~~~json
{
  "account":{"email":"doctor-demo@example.test","password":"replace-with-your-own-password"},
  "name":"Demo Doctor",
  "specialty":"Cardiology"
}
~~~

Register a patient:

~~~json
{
  "account":{"email":"patient-demo@example.test","password":"replace-with-your-own-password"},
  "name":"Demo Patient",
  "phone":"+970123456789"
}
~~~

Choose distinct private local passwords. Endpoints assign DOCTOR/PATIENT; bodies cannot create ADMIN. Email is normalized and unique across roles. Passwords require 8–72 characters and at most 72 UTF-8 bytes.

Doctor updates use `{"name":"Updated Doctor","specialty":"Neurology"}`. Patient updates use `{"name":"Updated Patient","phone":"12345"}`. These routes do not update email, password, role, or enabled state. Null/blank phone clears it.

Names are required, maximum 150 characters. Specialty is required, maximum 100; phone is optional, maximum 30. Unknown specialty returns `[]`; blank/oversized specialty returns `400`. Search summaries contain only ID, name, and specialty.

Any appointment history prevents deleting its doctor/patient, including cancelled history. An unused doctor's unbooked slots are removed with its profile. Removing an unused profile also removes its login.

## Slots and appointments

| Method | Path | Access | Result |
| --- | --- | --- | --- |
| POST | `/api/doctors/{doctorId}/slots` | ADMIN | `201` new slot |
| GET | `/api/doctors/{doctorId}/slots` | ADMIN, PATIENT, owning DOCTOR | `200` future unoccupied slots |
| DELETE | `/api/doctors/{doctorId}/slots/{slotId}` | ADMIN | `204` slot without appointment history removed |
| POST | `/api/appointments` | PATIENT | `201` booking and Location |
| GET | `/api/appointments/me` | PATIENT, DOCTOR | `200` own appointments/history; optional `date` filter |
| GET | `/api/appointments/{appointmentId}` | Owning PATIENT or DOCTOR | `200` one appointment |
| PATCH | `/api/appointments/{appointmentId}/cancel` | Owning PATIENT | `200` CANCELLED |
| PATCH | `/api/appointments/{appointmentId}/complete` | Owning DOCTOR | `200` COMPLETED |

List today's appointments as DOCTOR with `GET /api/appointments/me?date=today`, or select a date with `GET /api/appointments/me?date=2030-01-07`. PATIENT accounts can use the same filter for their own appointments. Omitting `date` preserves the complete history response.

Dates use UTC, matching the appointment timestamps. The filter selects slot start times from 00:00 inclusive to the next day's 00:00 exclusive, keeps chronological order and all appointment statuses, and never expands ownership access. A date without matches returns `[]`; an empty, malformed, or impossible date returns `400`. Use exactly `today` or `YYYY-MM-DD`.

Create a slot as ADMIN, choosing a future date on a UTC half-hour boundary:

~~~json
{"startTime":"2030-01-07T10:00:00Z"}
~~~

Slots last 30 minutes. The server calculates `endTime`. Fractional seconds and UTC minutes other than 00/30 are rejected. Inputs may use `Z` or an offset; alignment is checked after UTC conversion. Slots cannot be moved/resized; replace an unused slot.

Book as PATIENT with the returned slot ID:

~~~json
{"slotId":123}
~~~

The server derives patient identity from authentication and doctor/time from the slot. Clients cannot select another patient or set status. Expired/occupied slots and disabled doctors' slots return `409`.

Cancel/complete requests have no body. Only BOOKED changes to CANCELLED or COMPLETED. Cancellation releases a future slot for a new booking while preserving history. Completed slots stay occupied. Repeated/terminal transitions return `409`. This version imposes no additional time-based cancellation/completion cutoff.

Admins manage slots but do not book/read clinical appointments through these routes.

## Prescriptions and medical records

| Method | Path | Access | Result |
| --- | --- | --- | --- |
| POST | `/api/prescriptions` | Appointment's DOCTOR | `201` document and Location |
| GET | `/api/prescriptions/me` | PATIENT, DOCTOR | `200` own patient/authored history |
| GET | `/api/prescriptions/{prescriptionId}` | Owning PATIENT or authoring DOCTOR | `200` document |
| PUT | `/api/prescriptions/{prescriptionId}` | Authoring DOCTOR | `200` content/version updated |
| POST | `/api/medical-records` | Relevant DOCTOR | `201` document and Location |
| GET | `/api/medical-records/me` | PATIENT, DOCTOR | `200` own patient/authored history |
| GET | `/api/medical-records/{recordId}` | Owning PATIENT or authoring DOCTOR | `200` document |
| PUT | `/api/medical-records/{recordId}` | Authoring DOCTOR | `200` content/version updated |

A prescription requires a completed appointment owned by its doctor. Patient/doctor references are derived from that appointment:

~~~json
{
  "appointmentId":123,
  "medicines":[{"name":"Example medicine","dosage":"Example dosage","instructions":"Example instructions"}],
  "notes":"Example consultation notes"
}
~~~

A record includes a patient ID. Its appointment ID is optional; if supplied, it must match a completed doctor-owned appointment for that patient. Without it, an existing completed appointment must establish the relationship:

~~~json
{
  "patientId":45,
  "appointmentId":123,
  "medicines":[],
  "notes":"Example follow-up notes",
  "labResults":["Example lab metadata"]
}
~~~

Use actual development IDs. Only the authoring doctor can read/update as DOCTOR, even if another doctor has also treated the patient. Patients read their own documents and cannot edit them. ADMIN has no clinical access. History is newest first, with ID as a tie breaker. Clinical DELETE routes are unavailable.

| Field | Validation |
| --- | --- |
| Prescription medicines | Required, 1–20 entries |
| Record medicines/labResults | Required arrays, 0–20 entries |
| Medicine name/dosage | Nonblank; maximum 150/100 characters |
| Medicine instructions | Optional; maximum 500 characters |
| Notes | Maximum 4,000; required/nonblank for records, optional for prescriptions |
| Each lab result | Nonblank string, maximum 500 characters |
| Update version | Required nonnegative number |
| Relational IDs | Positive; existence/ownership checked |

Null medicine entries are rejected. Responses contain string `id`, numeric references, content, UTC timestamps at millisecond precision, and `version`.

PUT replaces editable content while keeping identity, references, and creation time fixed. Send the latest version from GET. A record update:

~~~json
{"version":0,"medicines":[],"notes":"Updated example record","labResults":[]}
~~~

Prescription updates contain `version`, nonempty `medicines`, and optional `notes`. Saves increase the version. Stale/competing updates return `409` with `Document changed; reload it before updating`. Reload and reconcile before retrying.

## Chatbot

POST `/api/chatbot/messages` accepts any supported authenticated role with CSRF:

~~~json
{"message":"How do I book an appointment?"}
~~~

Example PATIENT response:

~~~json
{
  "role":"PATIENT",
  "model":"stub",
  "reply":"View available slots with GET /api/doctors/{doctorId}/slots, then POST /api/appointments with a slotId. GET /api/appointments/me lists your appointments. An occupied slot returns 409 Conflict."
}
~~~

Messages must be nonblank and at most 1,000 characters. The local stub matches English keywords without case sensitivity, returns role-specific fallback help, and does not echo input or execute actions. Body role/model/ID fields cannot override authentication or retrieve records.

ADMIN help covers doctors, patients, and slots; DOCTOR help covers appointments and authored documents; PATIENT help covers search, booking/cancellation, profile updates, and own history.

Chatbot replies also include a server-selected `model`: `stub` in offline mode, or the configured OpenAI model ID in remote mode. Public clients cannot override role or model. The independent service and private-key configuration are described in [OpenAI setup](../chatbot-service/README.md). A provider outage, missing model, or inference timeout returns `503`; generated replies are workflow guidance and must not be treated as medical advice.
