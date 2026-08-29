# 18 — Production Security Posture (verified 2026-08-29)

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
| `VONAGE_SIGNATURE_SECRET` | ❌ | — | **fail-open** | NOT set. Inbound webhooks are not cryptographically verified. Attacker can POST forged webhooks (fake leads, abuse). Backend `VonageWebhookValidator` now verifies Vonage's **JWT-in-Authorization** scheme (HS256, `iss=Vonage`, `payload_hash` vs raw-body SHA-256) — aligned 2026-08-29. **Set the secret from the Dashboard (Settings → Signed webhooks → Signature secret) and redeploy.** The secret used must be the same one associated with the `api_key` claim of the inbound JWT. Until set, webhooks are accepted without verification (fail-open). |
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

## 4. Lead capture: `[LEAD:...]` tag is best-effort; deterministic fallback covers it (resolved 2026-08-29)

The system prompt (`BusinessPromptComposer`) instructs the model to append
`[LEAD:name=...|email=...|service=...]` once name + email are captured. The live test exposed
that `openai/gpt-oss-20b` is **unreliable at emitting the machine tag** — the conversation
succeeded but the lead was not persisted because persistence depended on the tag.

Mitigation chosen: **deterministic fallback** (the AI tag remains best-effort, never the only
path):

- `WhatsAppAiChatbotService.saveLeadFromMessage` saves a lead directly whenever a user message
  contains an email and a business profile is resolved — regardless of the chat step or whether
  the AI emitted a tag (verified in production; T1 real-flow WhatsApp lead lands in CRM with
  correct `created_by`).
- Name recovery (2026-08-29): if the current message has the email but no name, the fallback
  scans recent user turns for an unambiguous introduction ("me llamo X", "mi nombre es X") so
  "Me llamo Juan" + "mi email es juan@x.com" in **separate messages** saves the lead as **Juan**
  instead of "Desconocido". Button labels / generic words ("Ventas") are never mistaken for a name.
- The `[LEAD:...]` parser still runs first; when present it wins and the fallback does not
  double-save. Test coverage: `WhatsAppAiChatbotServiceTest` (229 tests, 0 failures).

Remaining model-side hardening (optional, not blocking): strengthen the tag instruction to
"end your reply with exactly one line starting `[LEAD:`" + validator warning, or move to a
larger/higher-reasoning model for the extraction step.

## 5. Known gaps before "production permanent"

1. ~~**Webhook signature verification (security)**: align `VonageWebhookValidator` to
   Vonage's JWT-in-Authorization scheme, then set `VONAGE_SIGNATURE_SECRET`.~~
   **Validator aligned 2026-08-29** (JWT HS256 + `iss` + `payload_hash`). Remaining action:
   copy the Dashboard signature secret into `VONAGE_SIGNATURE_SECRET` and redeploy — then
   the webhook path flips from fail-open to fail-closed.
2. ~~**Lead capture fix** (see §4) so every qualified WhatsApp contact becomes a real lead
   with `created_by` set.~~ **Resolved 2026-08-29** — deterministic email-triggered fallback
   + name recovery from earlier messages; verified in production.
3. **Retell number**: buy a number, set `RETELL_FROM_NUMBER`, then outbound voice works.
4. **Sleep on Hobby plan**: Postgres/Redis sleep when idle; the backend then crashes on
   startup until the DB is woken. Upgrade the plan or add keep-alive ping for 24/7.
5. **Frontend**: `callsagents-frontend` service is Offline (no deployment in production).
6. **Flyway warning**: prod Postgres is 18.6, above Flyway's supported 17 — monitor.