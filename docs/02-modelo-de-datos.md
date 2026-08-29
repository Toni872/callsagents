# Callsagents — Data Model (current schema V1–V20)

> Live schema reference. Entities are marked **SaaS-core** (the product), **MVP-origin** (born in the outbound MVP; several are actively wired into the live flow — see `01-arquitectura.md §8`), or **DROPPED**. `Chat` is **ephemeral** — no table. This describes the **real, current** schema as materialized by Flyway migrations V1–V20.

## Schema conventions

| Convention | Rule |
|---|---|
| **PK** | UUID, `gen_random_uuid()` (via `pgcrypto`, no-op on PG13+) |
| **Enums** | Postgres **native** ENUMs, added via `CREATE TYPE` / `ALTER TYPE ... ADD VALUE` |
| **ORM mapping** | `@Enumerated(STRING)` + `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` — required or you get "expression is of type character varying" |
| **Case** | `snake_case` tables/columns, `TIMESTAMPTZ` for timestamps |
| **JSONB** | via `hypersistence-utils` (`@JdbcTypeCode(SqlTypes.JSON)` on `Map<String,Object>`) |
| **Audit** | `created_at` (NOT NULL, `@PrePersist`) + `updated_at` (`@PreUpdate`) on every table |
| **Soft delete** | NO — hard delete + `AuditLog` |
| **Index naming** | `idx_<table>_<cols>` (e.g. `idx_leads_status`) |
| **Migrations** | Never edit a shipped migration; add `V{n}__...sql`. `V13` is **missing**; `V2` is **dev-only** under `db/migration/dev` |

**Migration inventory (V1–V20):**

| Migration | Purpose |
|---|---|
| V1 | Initial schema (users, leads, campaigns, campaign_leads, calls, appointments, integration_configs, audit_logs + all base ENUMs) |
| V2 (dev-only) | Seed admin (`db/migration/dev`) |
| V3 | `campaign_leads` audit timestamps defaults |
| V4 | `calendar_integrations` table + `calendar_sync_status` |
| V5 | `appointments.external_provider/event_id/synced_at` |
| V6 | `voice_calls` table + `voice_call_status` ENUM |
| V7 | `campaigns` voice-agent config columns (company/website/industry/services/tone) |
| V8 | Seed admin (production) |
| V9 | `appointments.external_event_url` (Google htmlLink) |
| V10 | `users.trial_ends_at` — comment wrongly says **14-day**; real trial is **7 days** |
| V11 | `lead_source` + `WHATSAPP` |
| V12 | Admin email → `contact@script-9.com`; `lead_source` + `WEB_CHAT` |
| V13 | **MISSING** (gap — no such migration file) |
| V14 | Update admin password (hash; previous rotation) |
| V15 | `business_profiles` table (SaaS tenancy) |
| V16 | `business_profiles.whatsapp_number` + partial index for webhook routing |
| V17 | Escalation Orchestrator: `escalations` table + `escalation_stage` ENUM + 4 `business_profiles` escalation columns |
| V18 | `leads.created_by` + backfill + NOT NULL + index (per-user scoping) |
| V19 | Rotate admin password (hash; plaintext only in secrets/env `CALLSAGENTS_ADMIN_PASSWORD`) |
| V20 | Drop dead `integration_configs` table + index (2026-08-29 cleanup) |

---

## Entities

### `users` — User (SaaS-core)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `email` | VARCHAR(255) **UNIQUE NOT NULL** | login |
| `password_hash` | VARCHAR(255) NOT NULL | BCrypt |
| `full_name` | VARCHAR(255) NOT NULL | |
| `role` | `user_role` ENUM NOT NULL | `ADMIN\|SUPERVISOR\|AGENT` |
| `status` | `user_status` ENUM NOT NULL | default `ACTIVE` |
| `last_login_at` | TIMESTAMPTZ | nullable |
| `trial_ends_at` | TIMESTAMPTZ | nullable; **real trial 7 days** |
| `created_at`/`updated_at` | TIMESTAMPTZ NOT NULL | |

Indexes: `idx_users_role`, `idx_users_status`, `UNIQUE(email)`.

### `business_profiles` — BusinessProfile (SaaS-core, the tenancy anchor)

1:1 with `User` via `@MapsId` → **`id == user_id` (businessId == userId)**.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | `gen_random_uuid()` |
| `user_id` | UUID **UNIQUE NOT NULL** FK → users | `@MapsId` |
| `company_name` | VARCHAR(255) NOT NULL | default `''` |
| `website` | VARCHAR(500) | |
| `industry` | VARCHAR(100) | |
| `services` | TEXT | |
| `tone` | VARCHAR(20) | default `professional` |
| `bot_name` | VARCHAR(100) | default `Naiara` |
| `greeting` | TEXT | |
| `chat_color` | VARCHAR(7) | default `#25D366` |
| `whatsapp_number` | VARCHAR(20) | tenant routing for webhooks (V16) |
| `onboarding_complete` | BOOLEAN NOT NULL | default FALSE |
| `created_at`/`updated_at` | TIMESTAMP NOT NULL | |

Index: `idx_business_profiles_whatsapp_number` (partial, `WHERE whatsapp_number IS NOT NULL`).
FK: `fk_business_user` → users ON DELETE CASCADE.

### `leads` — Lead (SaaS-core)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `first_name` | VARCHAR(100) NOT NULL | |
| `last_name` | VARCHAR(100) NOT NULL | |
| `email` | VARCHAR(255) | nullable |
| `phone` | VARCHAR(32) | nullable, E.164 |
| `company` | VARCHAR(255) | nullable |
| `status` | `lead_status` ENUM NOT NULL | default `NEW` |
| `source` | `lead_source` ENUM NOT NULL | includes WHATSAPP / WEB_CHAT (V11/V12) |
| `assigned_to` | UUID FK → users | nullable, ON DELETE SET NULL |
| `notes` | TEXT | |
| `custom_fields` | JSONB | flexible metadata |
| `consent_at` | TIMESTAMPTZ | compliance |
| `do_not_call` | BOOLEAN NOT NULL | default FALSE |
| `data_retention_until` | DATE | |
| `created_at`/`updated_at` | TIMESTAMPTZ NOT NULL | |

Constraints: `chk_leads_contact` (email OR phone present). Indexes on email, phone, status, assigned_to, company, do_not_call.

### `voice_calls` — VoiceCall (SaaS-core)

Records voice call attempts through a `VoiceProvider` (Retell/Vapi) or manually logged.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `lead_id` | UUID FK → leads | nullable, SET NULL |
| `appointment_id` | UUID FK → appointments | nullable, SET NULL |
| `user_id` | UUID NOT NULL FK → users | owner/initiator |
| `provider` | VARCHAR(32) | `VAPI`/`RETELL` / NULL (manual) |
| `provider_call_id` | VARCHAR(255) | provider identifier |
| `phone_number` | VARCHAR(32) NOT NULL | dialed |
| `status` | `voice_call_status` ENUM NOT NULL | default `SCHEDULED` |
| `direction` | VARCHAR(16) NOT NULL | default `OUTBOUND` |
| `started_at`/`ended_at` | TIMESTAMPTZ | |
| `duration_seconds` | INT | |
| `cost_usd` | NUMERIC(10,4) | webhook-populated |
| `transcript` | TEXT | on end |
| `recording_url` | VARCHAR(512) | |
| `error_message` | TEXT | |
| `metadata` | JSONB | provider extras |
| `created_at`/`updated_at` | TIMESTAMPTZ NOT NULL | |

Indexes: `idx_vc_user_status`, `idx_vc_provider_call`, `idx_vc_lead`, `idx_vc_started_at`.

### Chat (ephemeral — NO table)

Web chat conversations are **not persisted**. `ChatService` keeps conversation history in a **Caffeine cache** (`MAX_HISTORY = 20`). There is also a **GLOBAL 50-lead trial cap** (see `ChatService`/`LeadService` `TRIAL_LEAD_LIMIT = 50`).

### `campaigns` — Campaign (MVP-origin)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `name` | VARCHAR(255) NOT NULL | |
| `description` | TEXT | |
| `status` | `campaign_status` ENUM NOT NULL | default `DRAFT` |
| `start_at`/`end_at` | TIMESTAMPTZ | |
| `script` | TEXT | |
| `company`/`website`/`industry`/`services`/`tone` | VARCHAR/TEXT | per-campaign voice config (V7) |
| `created_by` | UUID NOT NULL FK → users | |
| `created_at`/`updated_at` | TIMESTAMPTZ NOT NULL | |

### `campaign_leads` — CampaignLead (MVP-origin)

Composite PK `(campaign_id, lead_id)`.

| Column | Type | Notes |
|---|---|---|
| `campaign_id` | UUID NOT NULL FK → campaigns | cascade |
| `lead_id` | UUID NOT NULL FK → leads | cascade |
| `status` | `campaign_lead_status` ENUM NOT NULL | default `PENDING` |
| `attempts` | INT NOT NULL | default 0 |
| `last_attempt_at` | TIMESTAMPTZ | |
| `assigned_to` | UUID FK → users | nullable |
| `next_attempt_at` | TIMESTAMPTZ | **written by no service** — no scheduler |
| `created_at`/`updated_at` | TIMESTAMPTZ NOT NULL | defaults via V3 |

### `calls` — Call (MVP-origin, Twilio-era)

FK to composite PK of `campaign_leads`.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `campaign_id`/`lead_id` | UUID NOT NULL | composite FK → campaign_leads |
| `user_id` | UUID NOT NULL FK → users | agent |
| `started_at`/`ended_at` | TIMESTAMPTZ | |
| `duration_seconds` | INT | |
| `status` | `call_status` ENUM | |
| `outcome` | `call_outcome` ENUM | |
| `recording_url` | VARCHAR(512) | |
| `provider_call_id` | VARCHAR(255) | |
| `notes` | TEXT | |
| `created_at`/`updated_at` | TIMESTAMPTZ NOT NULL | |

### `appointments` — Appointment (MVP-origin)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `lead_id` | UUID NOT NULL FK → leads | |
| `user_id` | UUID NOT NULL FK → users | |
| `scheduled_at` | TIMESTAMPTZ NOT NULL | |
| `duration_minutes` | INT NOT NULL | default 30 |
| `status` | `appointment_status` ENUM NOT NULL | default `PENDING` |
| `notes` | TEXT | |
| `external_provider` | VARCHAR(32) | null/Gogle/Outlook (V5) |
| `external_event_id` | VARCHAR(255) | (V5) |
| `external_synced_at` | TIMESTAMPTZ | (V5) |
| `external_event_url` | VARCHAR(1024) | Google htmlLink (V9) |
| `created_at`/`updated_at` | TIMESTAMPTZ NOT NULL | |

### `calendar_integrations` — CalendarIntegration (MVP-origin / PARTIAL)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID NOT NULL FK → users | cascade |
| `provider` | VARCHAR(32) NOT NULL | `GOOGLE`/`OUTLOOK` |
| `external_calendar_id` | VARCHAR(255) | |
| `external_account_email` | VARCHAR(255) | |
| `access_token_encrypted` | VARCHAR(2048) NOT NULL | AES-GCM (ENCRYPTION_KEY) |
| `refresh_token_encrypted` | VARCHAR(2048) | |
| `access_token_expires_at` | TIMESTAMPTZ | |
| `scopes` | VARCHAR(1024) | |
| `sync_enabled` | BOOLEAN NOT NULL | default TRUE |
| `last_sync_at` / `last_sync_status` / `last_sync_error` | TIMESTAMPTZ / `calendar_sync_status` / TEXT | |
| `created_at`/`updated_at` | TIMESTAMPTZ NOT NULL | |

UNIQUE `(user_id, provider)`. Indexes `idx_cal_user`, `idx_cal_provider_enabled`.

### `integration_configs` — DROPPED (V20, 2026-08-29)

Generic provider connector (Vapi/Retell/Bland era). **Unused dead table:** created in V1 but never referenced by any entity, repository, service, or controller (the "integrations" module has no Java classes). Removed by `V20__drop_integration_configs.sql`.

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `provider` | VARCHAR(50) NOT NULL | |
| `api_key_encrypted` | VARCHAR(512) NOT NULL | |
| `webhook_secret` | VARCHAR(255) NOT NULL | |
| `config_json` | JSONB | |
| `enabled` | BOOLEAN NOT NULL | default TRUE |
| `created_at`/`updated_at` | TIMESTAMPTZ NOT NULL | |

Index: `idx_intcfg_provider_enabled`.

### `audit_logs` — AuditLog (common)

| Column | Type | Notes |
|---|---|---|
| `id` | UUID PK | |
| `user_id` | UUID FK → users | null = system |
| `entity_type` | VARCHAR(100) NOT NULL | |
| `entity_id` | UUID NOT NULL | |
| `action` | `audit_action` ENUM NOT NULL | CREATE/UPDATE/DELETE/STATUS_CHANGE |
| `changes_json` | JSONB | |
| `created_at` | TIMESTAMPTZ NOT NULL | no updated_at |

Indexes: `idx_audit_entity`, `idx_audit_user`, `idx_audit_created_at`, `idx_audit_changes_gin` (GIN).

---

## Enum definitions (full)

| Enum (PG type) | Values |
|---|---|
| `user_role` | `ADMIN`, `SUPERVISOR`, `AGENT` |
| `user_status` | `ACTIVE`, `DISABLED` |
| `lead_status` | `NEW`, `ASSIGNED`, `IN_PROGRESS`, `QUALIFIED`, `NOT_QUALIFIED`, `CONVERTED`, `DISQUALIFIED` |
| `lead_source` | `MANUAL`, `IMPORT`, `API`, `WHATSAPP` (V11), `WEB_CHAT` (V12) |
| `campaign_status` | `DRAFT`, `SCHEDULED`, `RUNNING`, `PAUSED`, `FINISHED`, `CANCELLED` |
| `campaign_lead_status` | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `SKIPPED` |
| `call_status` | `CONNECTED`, `VOICEMAIL`, `NO_ANSWER`, `BUSY`, `FAILED` |
| `call_outcome` | `INTERESTED`, `NOT_INTERESTED`, `CALLBACK`, `APPOINTMENT_SET`, `NOT_REACHED` |
| `appointment_status` | `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `NO_SHOW` |
| `audit_action` | `CREATE`, `UPDATE`, `DELETE`, `STATUS_CHANGE` |
| `voice_call_status` | `SCHEDULED`, `RINGING`, `IN_PROGRESS`, `FORWARDING`, `ENDED`, `FAILED`, `NO_ANSWER` |
| `calendar_sync_status` | `PENDING`, `SYNCED`, `FAILED` |

`VoiceProviderType` (`VAPI`/`RETELL`) and `CalendarProviderType` (`GOOGLE`/`OUTLOOK`) are **Java-only** enums mapped with `@Enumerated(STRING)` to the `provider` VARCHAR columns (not PG ENUM types).

---

## Escalation Orchestrator (IMPLEMENTED — V17, ADR-009)

The escalation data model is **built** (not planned). Migration `V17__escalation_orchestrator.sql` creates:

- **`escalations` table** — per-lead escalation lifecycle: `id`, `lead_id`, `user_id`, `stage`, `followup_sent_at`, `waiting_until`, `voice_called_at`, `provider_call_id`, `voice_outcome`, `metadata` (JSONB), `created_at`. `stage` is the PG enum `escalation_stage` (`QUALIFIED`, `FOLLOWUP_SENT`, `WAITING_REPLY`, `VOICE_CALLED`, `RESOLVED`, `ABANDONED`, `CANCELLED`).
- **4 `business_profiles` columns** — per-tenant escalation config: `escalation_enabled` (BOOLEAN, default true), `reply_timeout_minutes` (INTEGER, default 30), `followup_message` (TEXT), `voice_agent_id` (VARCHAR).

`EscalationScheduledTask` polls `WAITING_REPLY` escalations past their timeout every 60s and elevates them via `EscalationService.escalateToVoice`.
