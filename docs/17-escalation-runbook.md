# Escalation Orchestrator — Local Test + Production Activation Runbook

Updated: 2026-08-27 — Escalation Orchestrator implemented (V17) and verified
end-to-end in the local Docker stack **with the real Vonage sandbox and a real
WhatsApp number** through a live cloudflared tunnel. This runbook documents the
verified behavior, two bugs found & fixed along the way, the tunnel recipe, and
(2) how to turn on real outbound voice once you hold a paid Vonage number
registered in Retell.

---

## 0. What the orchestrator does (recap)

After the WhatsApp chatbot qualifies a lead (the lead confirms a demo via
`confirm_yes`), the system:

1. `EscalationService.qualify(...)` sends a WhatsApp **follow-up** message
   (template with `{name}` placeholder, per-business `followup_message`).
2. Arms a timeout: `waiting_until = now + reply_timeout_minutes` (default 30).
3. If the lead **replies** → `handleReply(...)` marks the escalation `RESOLVED`
   (no voice call).
4. If the lead **does not reply** before `waiting_until` → the scheduler
   (`EscalationScheduledTask`, every 60 s) calls `escalateToVoice(...)` which
   places a **Retell AI outbound voice call** — fallback only, never a first
   contact.

State machine: `QUALIFIED → FOLLOWUP_SENT → WAITING_REPLY → VOICE_CALLED →
RESOLVED | ABANDONED | CANCELLED`.

Guards (all safe by design): `escalation_enabled=false` → record QUALIFIED, stop;
`doNotCall=true` → record, never message/call; no phone → ABANDONED; follow-up
send failed → stays QUALIFIED (never arm wait/voice on a message not delivered).

### Verdict on this build

Updated after the real E2E session (Test C circuit, live sandbox + tunnel):

| Check | Status |
|---|---|
| Migration V17 applies clean | ✅ PASS |
| Schema validated (`ddl-auto=validate`) | ✅ PASS |
| Scheduler bean / `@EnableScheduling` | ✅ PASS (query every 60 s in logs) |
| Trigger→DB wiring (`qualify` records QUALIFIED, no I/O) | ✅ PASS (E2E, side-effect-free) |
| Reply cancels escalation (`handleReply`) | ✅ **PASS — real E2E** |
| `handleReply` actually wired to incoming webhook | ✅ **PASS** (was dead code → fixed) |
| No duplicate reply (double-send bug) | ✅ **PASS** (fixed) |
| Real WhatsApp send to registered number | ✅ PASS (real send reached user's phone) |
| Real inbound → chatbot reply round-trip | ✅ PASS (live tunnel + sandbox) |
| Timeout → `escalateToVoice` | ✅ wiring PASS (scheduler+guard); voice leg gated |
| Real outbound voice call | ⛔ BLOCKED until number + Retell BYON |

---

## 1. Local E2E verification (executed with the real sandbox)

These integration tests were run live against the docker stack with the real
Vonage sandbox and a real WhatsApp number. The single biggest blocker was **not**
code — it was that the Vonage Dashboard inbound webhook pointed at a **dead
trycloudflare tunnel**. Fix that first (1.1) or nothing arrives.

### 1.1 The tunnel recipe (critical)

The Vonage **sandbox** needs a public URL to forward inbound messages to your
local backend. Use `cloudflared` (installed). **Every tunnel is a random URL
that dies when the process closes** — you must update the Dashboard URLs each
time you start a new one.

```powershell
# 1) Start the tunnel (keep the terminal open; note the https://...trycloudflare.com URL)
& "C:\Program Files (x86)\cloudflared\cloudflared.exe" tunnel --url http://localhost:8080 --no-autoupdate
# 2) In the Vonage Dashboard → Messaging → Messages API Sandbox, set:
#    Inbound : https://<random>.trycloudflare.com/api/webhooks/vonage
#    Status  : https://<random>.trycloudflare.com/api/webhooks/vonage/status
# 3) Verify the tunnel reaches the backend:
Invoke-WebRequest https://<random>.trycloudflare.com/api/health   # expect 200 {"status":"UP"}
```

> ⚠️ **24-hour window**: the sandbox only delivers a message to you if your
> number is in the allowlist **and** you messaged the sandbox number within the
> last 24 h. If the bot goes silent, first message the sandbox number, then
> re-check the tunnel URL + Dashboard webhooks.

### 1.2 Prerequisites (once)

```bash
cd C:\Users\Antonio\Desktop\Callsagents
docker compose up -d --build backend      # applies V17; backend healthy on :8080
# health:
Invoke-WebRequest http://localhost:8080/api/health
```

Vonage sandbox creds live in the backend env (`VONAGE_API_KEY`,
`VONAGE_API_SECRET`, `VONAGE_SANDBOX_NUMBER`). Sandbox only reaches
**registered** recipients; use your own WhatsApp number added to the Vonage
sandbox allowlist, and message the sandbox number first to open the window.

> ⚠️ On this Windows host the docker Postgres is on port **5433** (a native
> Postgres owns 5432). Any psql/Java client must use `-p 5433`.

### 1.3 Test A — Happy path: real follow-up message

`EscalationService.qualify(...)` sends the WhatsApp **follow-up** and moves the
row `QUALIFIED → FOLLOWUP_SENT` with `followup_sent_at` and
`waiting_until = now + reply_timeout_minutes`.

- **How verified**: with a throwaway `@SpringBootTest` wiring `qualify(leadId,
  userId)` to the docker PG on 5433, the log printed
  `Escalation qualified + followup sent leadId=... timeoutMinutes=30` and the
  row showed `FOLLOWUP_SENT`, `followup_sent_at`, `waiting_until=now+30min`.
- **Defensive behavior confirmed**: if the Vonage send fails, the row **stays**
  `QUALIFIED` — it never arms wait/voice on a message the lead may not have
  received.
- **Real-send note**: a real `qualify` to a sandbox number assumes the 24 h
  window is open (you messaged the sandbox first). The generic `sendText`
  path is proven live: `Vonage message sent: status=202 ACCEPTED` reaching the
  registered phone.

### 1.4 Test B — Timeout → escalateToVoice

Push `waiting_until` into the past so the 60 s scheduler fires immediately:

```bash
docker compose exec -T postgres psql -U callsagents -d callsagents -p 5433 \
  -c "update escalations set stage='WAITING_REPLY', waiting_until=now()-interval '30 seconds';"
```

- **Verified wiring**: `EscalationScheduledTask` logs the due count and calls
  `escalateToVoice(...)`. With a lead that has **no phone** it correctly lands
  in `ABANDONED`. The outbound **voice leg** itself stays gated
  (`RETELL_FROM_NUMBER` empty) until a paid number is configured — this is the
  expected behavior, not a bug.

### 1.5 Test C — Reply cancels escalation

- **Real E2E — PASS.** We created a lead (`+34687723287`) with an active
  `FOLLOWUP_SENT` escalation, messaged the sandbox, and the incoming webhook
  fired `handleReply`. Log:
  `Escalation RESOLVED on lead reply: leadId=... escalationId=...`; the row went
  `FOLLOWUP_SENT → RESOLVED` with `voice_outcome='LEAD_REPLIED'`, and the bot
  replied **once** (202). The scheduler then ignores the terminal row — no voice
  call.
- **Bug found & fixed while testing**: `handleReply` existed in the service but
  was **dead code** — no webhook/chatbot called it, so a lead's reply never
  cancelled anything. It is now wired into `VonageWebhookController.handleInbound`
  (resolve lead by `from`, call `escalationService.handleReply(lead.id)`).
- **Bug found & fixed while testing (double-send)**: when the message is a
  reset/`hola`, the chatbot already sends the greeting and returns `null`, but
  the controller fell through to the basic state machine and sent a **second**
  message (log: `Vonage message sent` then `Vonage message error` +
  `Failed to send Vonage reply`). Fixed by only falling back when Groq is not
  configured, and only sending when `reply != null`. Verified: now a single
  `Vonage message sent` and no error.

### 1.6 Test D — Real trigger in a live conversation

The chatbot flow drives the lead: greeting → intent → name/email → timing →
**confirm_yes**. On `confirm_yes`, `triggerEscalation(phone, businessId) → qualify`.

- **Verified via a driven conversation to `confirm_yes`**: `businessId` resolves
  non-null (it requires `business_profiles.whatsapp_number` to match the inbound
  `to`; it is NULL by default for Script9, so set it before testing), and a new
  `escalations` row is created for that business.
- **Key config**: with `whatsapp_number` NULL the tenant lookup returns
  `businessId = null` and the trigger is silently skipped. Set it (or use the
  `to` that Vonage actually delivers) for the trigger to fire.

### 1.7 Test E — API surface (auth + config)

With a session for an ADMIN/SUPERVISOR user:

- `GET /api/escalation/config` → current per-business config.
- `PUT /api/escalation/config` → update `reply_timeout_minutes`/`followup_message`
  /`voice_agent_id`/`escalation_enabled`.
- `GET /api/escalation/leads/{leadId}` → escalation status for a lead.
- `POST /api/escalation/{id}/cancel` → cancel an active escalation (ADMIN/SUPERVISOR).

Expected shape (matches the rest of the API): `{ "success": true, "data": ... }`.

> **Verified**: `PUT /api/escalation/config` and `GET /api/escalation/leads/{id}`
> were previously crashing with an NPE because `Map.of(...)` rejects `null`
> values (e.g. a business with no follow-up message). Fixed by returning
> null-safe records / `LinkedHashMap` instead of `Map.of`. The endpoint now
> returns `{success:true, data:{...}}` and persists.

---

## 2. Activating real outbound voice in production

The orchestrator is already in the code. What's missing is a telephony number
Retell can call **from**. Steps below are the ones flagged as requiring *user
action*; the code work is already done.

### 2.1 Required pieces

| Piece | Status | Where |
|---|---|---|
| Paid Vonage account | USER ACTION | portal.vonage.com |
| Vonage **Voice** number (e.g. `+34 ...`) | USER ACTION | Vonage Numbers |
| Number registered in Retell as **BYON** | USER ACTION | app.retellai.com → Phone Numbers |
| `RETELL_API_KEY` | ✅ present (web-call works) | Railway env |
| `RETELL_AGENT_ID` | ✅ present | Railway env |
| `RETELL_FROM_NUMBER` | ⛔ **empty** → set to the Retell BYON number | Railway env |
| Per-business `voice_agent_id` | optional (falls back to `RETELL_AGENT_ID`) | business_profiles via `PUT /api/escalation/config` |

### 2.2 Why `RETELL_FROM_NUMBER` blocks the call

`RetellProvider.startCall` throws when `RETELL_AGENT_ID` **or** `RETELL_FROM_NUMBER`
is blank. Today only the **web-call** path works because it needs neither a phone
number nor Retell BYON. The Escalation Orchestrator uses `create-phone-call`,
so it needs both.

### 2.3 Activation checklist

1. **Buy a Vonage Voice number** and make outbound calls possible on the account
   (top-up / contract as required).
2. **Register that number in Retell BYON** so Retell can place outbound calls
   from it.
3. **On Railway** (`renewed-reverence`, service `callsagents-backend`) set
   `RETELL_FROM_NUMBER=<the BYON number>` (E.164, e.g. `+346...`). Keep
   `RETELL_API_KEY`/`RETELL_AGENT_ID` as-is.
4. **Redeploy the backend** via `railway up --service callsagents-backend`
   (never `railway redeploy` — it reuses old code).
5. **(Optional) set a per-business agent**: `PUT /api/escalation/config` with
   `voice_agent_id=<a Retell agent id>` if the call should use a specific agent;
   otherwise it falls back to `RETELL_AGENT_ID`.
6. **Smoke test**: run Test B again locally (or a controlled prod lead), confirm
   a **VOICE_CALLED** row with a `provider_call_id`, then check Retell's dashboard
   for the outbound call ringing the lead.

### 2.4 Gotchas

- **`railway redeploy` re-deploys old code** — always `npx railway up
  --service callsagents-backend --detach` after setting env vars.
- Outbound calls cost money and hit a REAL number — only run against a consenting
  test number, not a real customer, until you've validated the voice prompt.
- The voice call is **fallback-only** by design: it fires only for a qualified
  lead who got a follow-up and never replied. It never first-contacts.
- `VONAGE_*` sandbox vars cannot make outbound calls or reach Retell. The real
  flow needs the **paid** Vonage number + Retell BYON; there is no "sandbox
  shortcut" for the voice leg.

---

## 3. Quick reference — env / config

| Key | Purpose | Status |
|---|---|---|
| `RETELL_API_KEY` | Retell auth | ✅ |
| `RETELL_AGENT_ID` | default voice agent | ✅ |
| `RETELL_FROM_NUMBER` | **outbound phone (BYON)** | ⛔ empty → set |
| `VONAGE_API_KEY/SECRET` | WhatsApp messaging | sandbox |
| `escalation_enabled` | per-business toggle | default true |
| `reply_timeout_minutes` | per-business timeout | default 30 |
| `followup_message` | `{name}` template | per-business |

## 4. Related

- `docs/03-adrs.md` — ADR-009 (Escalation Orchestrator, accepted/designed).
- `docs/02-modelo-de-datos.md` — `escalations` table + enum (now implemented).
- `backend/src/main/resources/db/migration/V17__escalation_orchestrator.sql`.
- `INFRASTRUCTURE.md` — Railway services, production URLs.
