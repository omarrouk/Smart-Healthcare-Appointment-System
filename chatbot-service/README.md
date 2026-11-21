# OpenAI chatbot service

An independent Spring Boot process selects an OpenAI model from the authenticated user's role. The healthcare backend derives that role from Basic Auth and calls this service with a private service key. Clients cannot choose their role or model. The chatbot process has no PostgreSQL/MongoDB dependencies or patient-record access.

| Role | Configured default model |
| --- | --- |
| ADMIN | `gpt-4.1-mini` |
| DOCTOR | `gpt-4.1` |
| PATIENT | `gpt-4.1-nano` |

These models provide application-workflow guidance. They are not clinically validated models. The service has no action tools, and generated text must be treated as untrusted. Use synthetic messages for development.

## Run without an OpenAI account

Keep `CHATBOT_MODE=stub` in the root `.env`. Start the databases and healthcare API normally:

```powershell
docker compose up -d --wait
.\mvnw.cmd spring-boot:run
```

The separate chatbot process is unnecessary in this mode. Replies report `model: "stub"` and use deterministic English guidance. No OpenAI request, account, or key is needed. This mode does not perform AI inference.

## Enable OpenAI later

An OpenAI API account, API key, model access, and available API quota are required. Review provider pricing and data handling before enabling it. In the ignored root `.env`, set:

```dotenv
CHATBOT_MODE=remote
CHATBOT_SERVICE_KEY=<private-random-service-key>
OPENAI_API_KEY=<private-openai-api-key>
```

Generate a service key locally with `[guid]::NewGuid().ToString('N')`. It is independent of the OpenAI key and user passwords. Keep both keys out of frontend code, Postman shared variables, Git, and logs. Do not paste credentials into chat.

Run the independent service from the project root in one terminal:

```powershell
.\mvnw.cmd -f chatbot-service/pom.xml spring-boot:run
```

Start the healthcare API in another terminal:

```powershell
.\mvnw.cmd spring-boot:run
```

Both processes load the root `.env`. The service defaults to `127.0.0.1:8090` and calls `https://api.openai.com/v1/chat/completions` over HTTPS. `CHATBOT_ADMIN_MODEL`, `CHATBOT_DOCTOR_MODEL`, and `CHATBOT_PATIENT_MODEL` configure model selection. Defaults are distinct GPT-4.1 variants with compatible Chat Completions parameters; changing to another model family may require request-parameter changes. Confirm availability in your account before running a live test. `OPENAI_BASE_URL` exists for local protocol tests; use the official HTTPS endpoint with real keys.

## Request flow

Call public `POST /api/chatbot/messages` with Basic Auth and a fresh CSRF token/cookie. The request body remains `{"message":"How do I book an appointment?"}`. Client-supplied role/model fields cannot override the authenticated account or server configuration.

The backend forwards only role and submitted message to `POST /internal/chatbot/messages`, using `X-Chatbot-Service-Key`. The independent service sends the selected model, role-specific workflow instructions, and submitted message to OpenAI with Bearer authentication, streaming disabled, `store: false`, and a 256-token completion limit. It returns `role`, `reply`, and the configured model.

Remote mode sends submitted text to OpenAI. It does not retrieve or forward clinical records or account credentials, but user-entered text can still contain sensitive data. Do not use real patient information in demonstrations. `store: false` does not guarantee zero retention; consult the provider's data policies. System instructions guide responses and do not form a security boundary or guarantee medical safety.

Provider errors, rate limits, missing/invalid credentials, malformed replies, and timeouts return `503` through the public API without silently falling back to a canned response. Missing API or service keys prevent the independent process from starting. Connect timeout is three seconds, provider timeout sixty seconds, and the backend permits sixty-five seconds for the service hop. No automatic paid-request retries are configured.

## Verification

```powershell
.\mvnw.cmd verify
.\mvnw.cmd -f chatbot-service/pom.xml verify
```

Tests use a local HTTP provider fixture with dummy credentials. They verify request format, Bearer authentication, role-based model selection, private service-key enforcement, validation, provider errors, and invalid replies. These tests do not contact OpenAI or prove live model output. Live OpenAI generation remains unverified because no account/API key is configured.

The current public API and offline Postman workflows remain usable without a provider account. After enabling remote mode, test one workflow-guidance request as each role and inspect the returned model. Never add the OpenAI key to Postman: only the independent service needs it.

Official references: [Chat Completions API](https://developers.openai.com/api/reference/resources/chat/subresources/completions/methods/create), [model catalog](https://developers.openai.com/api/docs/models), and [API data controls](https://developers.openai.com/api/docs/guides/your-data).
