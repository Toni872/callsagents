# Callsagents — Architecture Decision Records

Each ADR records a real decision made during the lifecycle of the product, in `Context / Decision / Consequences` form. These capture the **why** behind choices so a future reader (or human) can evaluate them without re-deriving the reasoning.

---

## ADR-001 — Vonage for WhatsApp (not Twilio)

**Status:** Accepted

**Context:** The product needs programmatic WhatsApp messaging to write to leads that come in through the web chat widget and to handle inbound WhatsApp conversations. Twilio was the incumbent in the original MVP (`WhatsAppService`), but the team needed a provider well-suited to interactive WhatsApp message flows (text, buttons, lists) with a viable dev sandbox.

**Decision:** Use **Vonage** for WhatsApp messaging. Vonage is used for both dev (sandbox number) and production flows. The legacy Twilio `WhatsAppService` was retained in-tree as deprecated reference at the time; it was **fully removed on 2026-08-29** (see ADR-011). Production outbound uses a paid Vonage number; the Retell voice path covers the outbound-call escalation.

**Consequences:**
- Interactive message types (buttons/lists) via `VonageMessageService` (sendText/sendButtons/sendList).
- Sandbox is fine for dev/dogfooding but cannot reach real contacts without a paid number.
- Keeping Twilio code in-tree adds maintenance surface but preserves the original design as reference.
- Tenant routing for inbound webhooks is done by resolving the `to` number against `business_profiles.whatsapp_number`.

---

## ADR-002 — Retell AI as the voice provider (Vapi present as abstraction only)

**Status:** Accepted

**Context:** The voice escalation needs a provider-backed call engine. Two candidates were real (Vapi and Retell). The team wanted to avoid hard-wiring the product to a single vendor while still having a concrete default.

**Decision:** Introduce a `VoiceProvider` **abstraction** with two implementations — `RetellProvider` (against `api.retellai.com`) and `VapiProvider` — and **choose Retell as the default**. `VoiceProviderType` (`VAPI`/`RETELL`) and a `VoiceCallService.placeCall` sit on top, and `POST /api/voice/webhook/{provider}` receives status updates from whichever provider is active.

**Consequences:**
- Swapping voice engine is confined to the provider layer, not scattered through services.
- Retell requires a `RETELL_FROM_NUMBER` to place real outbound phone calls — that value is currently **empty**, so only the WebRTC **web-call** path works today.
- Voice webhook signature validation is **fail-closed** (`WebhookSignatureValidator`).
- Retell agent id currently configured: `agent_9fda91a4d3ddaa0f8c8cbfa7c9`.

---

## ADR-003 — SaaS multi-tenancy via BusinessProfile 1:1 User (@MapsId, businessId == userId)

**Status:** Accepted

**Context:** The product pivoted from a single-tenant outbound tool to a **SaaS** where each client business has its own branding, WhatsApp number, widget, and prompt. Tenancy needed to be simple and correct.

**Decision:** Each `User` owns exactly one `BusinessProfile` in a **1:1 relationship using `@MapsId`**, so the `BusinessProfile.id` **equals** `user_id` (`businessId == userId`). The same table stores branding, `whatsapp_number` (for webhook routing), and onboarding state.

**Consequences:**
- No separate account/tenant table — tenancy is anchored on the `User` PK, keeping lookups cheap and identity unambiguous.
- Adding tenants is just registering a user + profile; there is no org/team hierarchy (out of scope).
- `BusinessPromptComposer` builds the per-tenant prompt from profile fields.
- Legacy role model (ADMIN/SUPERVISOR/AGENT) is now mostly vestigial for SaaS tenants (each is effectively an admin of their own profile).

---

## ADR-004 — Groq for the chatbot LLM (openai/gpt-oss-20b)

**Status:** Accepted

**Context:** The WhatsApp and web chat bots need a responsive, low-cost LLM for lead qualification. Latency matters (goal: reply in <1 min to avoid ghosting), and the team wanted to avoid a provider whose output carries "thinking-block" artifacts.

**Decision:** Use **Groq** as the chat LLM provider, model `openai/gpt-oss-20b`, via `GroqService`. Chosen for **cost and speed** and because its output has no thinking-block artifacts that would pollute user-facing replies.

**Consequences:**
- Very low per-message cost, fast responses — aligns with the instant-response product goal.
- Model choice is config-driven (`app.groq.model`), so it can change without code changes.
- Chat intelligence is text-qualification focused; the Retell voice agent handles the voice path separately.

---

## ADR-005 — Postgres native ENUMs + NAMED_ENUM + JSONB pattern

**Status:** Accepted

**Context:** The schema uses strongly-typed status/source fields plus flexible per-row metadata. The team needed reliable mapping between JPA entities and Postgres, and flexible columns without repeated migrations.

**Decision:** Use **Postgres native ENUM types** for finite-state columns, mapped in ORM with `@Enumerated(EnumType.STRING)` **plus** `@JdbcTypeCode(SqlTypes.NAMED_ENUM)`; use **JSONB** for flexible metadata (`custom_fields`, `metadata`, `changes_json`, `config_json`) via `hypersistence-utils`.

**Consequences:**
- Strong typing and readable `psql` output (enum labels, not ints).
- **Gotcha:** without `NAMED_ENUM`, Hibernate sends VARCHAR for ENUM columns and Postgres rejects with "column X is of type Y but expression is of type character varying". This bit us and is documented (`docs/00-handoff.md` #2).
- Postgres ENUMs are immutable — adding a value requires `ALTER TYPE ... ADD VALUE` (see V11/V12) and a backend restart.
- JSONB gives flexibility (custom lead fields, voice metadata) without schema churn.

---

## ADR-006 — JWT HS256 access/refresh rotation + Redis revocation with reuse detection

**Status:** Accepted

**Context:** Stateless auth is needed across a Spring Boot API, but logout/revocation must actually invalidate tokens rather than waiting for expiry.

**Decision:** Use **Nimbus JWT MAC HS256**: **access 15 min**, **refresh 7 d** with **rotation** (each `/auth/refresh` issues a new refresh and revokes the old). Revocation lives in **Redis** (`refresh:`/`revoked:` keys); a reused/rotated token triggers **reuse detection which revokes the entire session**. Passwords hashed with `BCryptPasswordEncoder(10)`.

**Consequences:**
- Short-lived access tokens bound the blast radius; rotation limits refresh-token theft windows.
- Reuse detection turns token replay into a session-wide revoke (stronger than per-token).
- Adds Redis as a hard dependency at runtime and on the auth path — an operational cost accepted for correct revocation.
- `/auth/me` uses `Authentication.getName()` (principal is a String, not `UserDetails`).

---

## ADR-007 — Global trial cap (7 days, 50 leads)

**Status:** Accepted — with a documented caveat

**Context:** Public self-registration grants a trial. The team needed a cap that both limits abuse and is simple to enforce, without building per-tenant billing/entitlement infrastructure prematurely.

**Decision:** Enforce a **global** trial: **7 days** and **50 leads** maximum, enforced in `ChatService`/`LeadService` (`TRIAL_LEAD_LIMIT = 50`). The V10 migration comment wrongly says 14 days — **the real behavior is 7 days / 50 leads**.

**Consequences:**
- **Current behavior is global, not per-tenant**: the 50-lead cap is a single global counter, not scoped to each `BusinessProfile`. This is acceptable for the singe-tenant dogfood stage but **must become per-tenant** before real multi-tenant rollout (see ADR-007 caveat → scaling phase).
- Simple to reason about, zero billing integration needed.
- Enforcing per-tenant trials is explicitly a **non-goal for now** and deferred to Stripe billing.

---

## ADR-008 — nginx Origin-strip + COOP/COEP unsafe-none for Google OAuth popup + CORS bypass

**Status:** Accepted

**Context:** The Angular app talks to the backend via `/api/`, but two integration issues arose: CORS friction between origins, and the **Google Identity popup failing under default COOP/COEP** security headers.

**Decision:** Use **nginx** to:
- reverse-proxy `/api/` to the backend **and strip the Origin/Accept headers** so the backend's CORS layer is bypassed;
- emit **`COOP`/`COEP: unsafe-none`**, which is **critical for the Google Identity popup** to open.

**Consequences:**
- Simpler backend CORS config; origin-independent requests.
- Loosening COOP/COEP is a deliberate security trade-off forced by Google Identity popup behavior — documented so it's not "accidentally tightened" and breaks the popup.
- Security headers/`unsafe-none` must be preserved in any future proxy change.

---

## ADR-009 — Escalation Orchestrator (WhatsApp follow-up → timeout → Retell outbound voice)

**Status:** **ACCEPTED / DESIGNED — not yet implemented**

**Context:** The core differentiator is recovering leads that go silent. Today the WhatsApp chatbot writes to a lead, but there is **no** automated follow-up or automatic voice retry. The voice call must be a **fallback only**, driven by a timeout, and configured **per business**.

**Decision:** Design an **Escalation Orchestrator** that, after a WhatsApp (or web chat) outreach goes unanswered past a per-business timeout, places a **Retell outbound voice call** to re-qualify the lead and, if interested, drive them to a booking. Per-business configuration controls delays/timeouts/voice enablement. (Planned data model documented in `docs/02-modelo-de-datos.md`: a new `escalations` table + 4 new `business_profiles` columns.)

**Consequences:**
- This is the **first place a scheduled/background mechanism actually needs to exist**; today there is **no `@Scheduled`/`@EnableScheduling`** anywhere.
- Can only fully ship once `RETELL_FROM_NUMBER` is set (retell phone outbound currently blocked).
- Must be built testably with the Vonage **sandbox** + WebRTC web-call before real paid-number outbound.
- Voice remains a fallback, never the first contact — consistent with Spanish cold-call legal constraints and the product's "respond to inbound" positioning.

---

## ADR-010 — Legacy outbound "campaigns" modules retained in-tree but deprecated; SaaS chat+voice is the product

**Status:** Accepted

**Context:** The original MVP was an outbound-campaigns product (leads/campaigns/calls/appointments, ADMIN/SUPERVISOR/AGENT roles, Twilio). The product **pivoted** to the SaaS chat + voice lead-capture model. Removing everything would lose reference and risk breaking the repo; keeping everything unmarked would confuse contributors.

**Decision:** Keep the outbound **campaigns/calls/appointments/calendar** modules **in-tree as deprecated LEGACY scaffolding**, clearly marked in every doc, and declare the **SaaS core** (auth, leads, chat, whatsapp, voice, business) as the product. Do not extend the legacy modules for product work; build new behavior on the SaaS core. **Twilio code was fully removed on 2026-08-29 (see ADR-011).**

**Consequences:**
- Lower risk than a hard delete, but ongoing maintenance surface for code that is not the product.
- Contributors must resist the temptation to "fix up" legacy modules instead of extending the SaaS path.
- The legacy code now primarily serves as reference for the Escalation Orchestrator design.
- `dashboard/summary` and `users`/`calendar` remain partly useful but are not the growth path.

---

## ADR-011 — Twilio fully removed (WhatsApp is Vonage-only)

**Status:** Accepted (2026-08-29)

**Context:** The original MVP implemented WhatsApp via Twilio (`TwilioWebhookValidator`, `WhatsAppWebhookController`, `WhatsAppConfig`, `WhatsAppService` legacy state machine, `com.twilio.sdk:twilio` dependency, `twilio:` config block, `/webhooks/whatsapp` permit rule, TWILIO_* env vars/docs). The product runs on **Vonage + Groq**; the Twilio path was dead code (its only hook was a fallback in `VonageWebhookController` that never ran because Groq is always configured).

**Decision:** **Remove all Twilio code, config, dependencies, tests, and documentation references** from the repository. Delete the four Twilio classes plus their domain records (`ConversationState`, `ConversationStep`) and tests; drop the `twilio:` block from `application.yml`, the `com.twilio.sdk:twilio` dependency from `pom.xml`, the `/webhooks/whatsapp` permit rule from `SecurityConfig`, TWILIO_* vars from `docker-compose.yml`/`.env.example`, and the Twilio row/notes from `docs/18`. The legacy `WhatsAppService` fallback in `VonageWebhookController` was removed; the AI chatbot is the single WhatsApp path.

**Consequences:**
- Cleaner repo: no dead provider surface, no maintenance on code that never runs.
- WhatsApp provider switching now touches one place (`backend/.../whatsapp/`): Vonage for messaging + Groq for AI.
- Historical reference for the old Twilio design is lost; the decision record above preserves the rationale.
- The remaining **legacy outbound** modules (campaigns/calls/appointments/calendar) stay in-tree per ADR-010; removing those is a separate, larger decision.
