# 18 — Production Security Posture (verified 2026-08-28)

Status of the Callsagents production environment on Railway (`renewed-reverence`,
environment `production`), verified live against deployment
`39746611-ccf4-4cbc-87d8-64e25a4621fb`.

## 1. Summary

- Backend online and healthy: `https://callsagents-production.up.railway.app/api/health`.
- WhatsApp/Vonage inbound -> AI chatbot -> outbound reply works end-to-end in the sandbox
  (verified with a real WhatsApp message: webhook received, Groq responded, Vonage returned 202).
- **Not yet customer-ready.** Two webhook signatures are still fail-open, lead capture via the
  AI tag is broken in the real model, voice (Retell) has no from-number, and the Cloud
  Hobby plan sleeps Postgres/Redis when idle.

## 2. Secrets & configuration matrix

| Variable | Set | Validated | Fail mode | Notes |
| --- | --- | --- | --- | --- |
| `VONAGE_API_KEY` | ✅ | ✅ | — | `75800ef5` (Master); balance check OK |
| `VONAGE_API_SECRET` | ✅ | ✅ | — | Rotated 2026-08-28 (previous value was invalid); sandbox send returned a message UUID |
| `VONAGE_SANDBOX_NUMBER` | ✅ | ✅ | — | `14157386102` |
| `VONAGE_SIGNATURE_SECRET` | ❌ | — | **fail-open** | NOT set. Inbound webhooks are not cryptographically verified. Attacker can POST forged webhooks (fake leads, abuse). Scheme mismatch: Vonage sandbox dashboard signs a **JWT in the `Authorization` header**; backend `VonageWebhookValidator` expects `X-Vonage-Signature` HMAC-SHA256 hex. Setting the secret today would fail-closed and break webhooks until the validator is aligned to the JWT scheme. |
| `TWILIO_AUTH_TOKEN` | ❌ | — | **fail-open** | Twilio webhooks accepted without signature verification. |
| `RETELL_API_KEY` | ✅ | — | — | Present. |
| `RETELL_AGENT_ID` | ✅ | — | — | `agent_9fda91a4d3ddaa0f8c8cbfa7c9` (Script9 profile). |
| `RETELL_FROM_NUMBER` | ❌ | — | voice blocked | Empty. Outbound voice calls are skipped until a number is configured. |
| `GROQ_API_KEY` / `GROQ_MODEL` | ✅ | ✅ | — | `openai/gpt-oss-20b`; chatbot replies confirmed. |
| `JWT_SECRET` | ✅ | — | — | Present. |
| `ENCRYPTION_KEY` | ✅ | — | — | Present. |
| `SPRINGDOC_ENABLED` | ✅ (`false`) | — | — | OpenAPI UI disabled in prod. |

## 3. Verified today (2026-08-28)

- Postgres + Redis were sleeping on the Hobby plan (no inbound traffic). Woken via
  `railway agent -p "start service <id>" -e production`; volume data intact.
- Migrations `V18__leads_created_by.sql` (NOT NULL `created_by` + backfill + index) and
  `V19__rotate_admin_password.sql` applied; schema version = 19.
- Admin login: NEW password accepted, OLD password rejected (401).
- Scoping smoke tests: `/api/leads`, `/api/campaigns`, `/api/business/profile`,
  public widget-config all scoped to Script9.
- Real WhatsApp flow: inbound `from=34687723287 to=14157386102` -> `step=initial`
  -> buttons -> timing -> confirmation buttons -> escalation path invoked.

## 4. Defect found: `[LEAD:...]` tag not emitted by the real model

The system prompt (`BusinessPromptComposer`) explicitly instructs the model to append
`[LEAD:name=...|email=...|service=...]` once name + email are captured. In the live test,
the lead was NOT saved (leads table still has 8 rows; the test contact never appeared),
and the confirmation buttons showed empty Name/Email — the model replied "Hola Antonio,
soy Nai…" without ever emitting the tag.

Root cause is not yet confirmed. Hypotheses:

1. `openai/gpt-oss-20b` is unreliable at emitting a trailing machine tag, especially
   when also instructed to answer in 2–3 sentences with one question.
2. The tag instruction sits inside the persona block; the model may prioritize natural
   conversational text.

Impact: WhatsApp leads are not persisted in production; the escalation path is wired to
`leadRepository.findByPhone` and will not fire for new contacts.

Mitigation options (not yet applied — requires a decision):

- Post-process the conversation: when `collecting_info` step has an email and the AI did
  not emit a tag, parse name/email from the conversation and save the lead directly.
- Stronger tag-only instruction (e.g. "end your reply with exactly one line starting
  `[LEAD:`") plus a validator that warns when the tag is missing.
- Use a larger/higher-reasoning model for the extraction path.

## 5. Known gaps before "production permanent"

1. **Webhook signature verification (security)**: align `VonageWebhookValidator` to
   Vonage's JWT-in-Authorization scheme, then set `VONAGE_SIGNATURE_SECRET`; generate and
   set a Twilio auth token, then switch Twilio validation to fail-closed.
2. **Lead capture fix** (see §4) so every qualified WhatsApp contact becomes a real lead
   with `created_by` set.
3. **Retell number**: buy a number, set `RETELL_FROM_NUMBER`, then outbound voice works.
4. **Sleep on Hobby plan**: Postgres/Redis sleep when idle; the backend then crashes on
   startup until the DB is woken. Upgrade the plan or add keep-alive ping for 24/7.
5. **Frontend**: `callsagents-frontend` service is Offline (no deployment in production).
6. **Flyway warning**: prod Postgres is 18.6, above Flyway's supported 17 — monitor.