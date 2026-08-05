# Fase 2 — Modelo de datos

## Decisiones de diseño

| Decisión | Elección | Justificación |
|---|---|---|
| **PK** | UUID | Mejor para APIs, evita enumeration, distribuible. |
| **Auditoría base** | `created_at` + `updated_at` en todas las entidades | Lo pide el playbook (Fase 2). |
| **Auditoría de autor** | `created_by` en entidades donde importa (Campaign, Lead, Call, Appointment) | User no necesita `created_by`. |
| **Soft delete** | NO. Hard delete + `AuditLog` registra la acción | Playbook: "soft delete solo si el negocio lo requiere". |
| **Multi-tenant** | NO en MVP | PRD lo define fuera del alcance. |
| **Compliance** | 3 campos mínimos en Lead: `consent_at`, `do_not_call`, `data_retention_until` | Toda app de llamadas salientes tiene regulación obligatoria. |
| **`Role`** | ENUM dentro de `User` | Solo 3 roles, sin RBAC granular. Migrable si crece. |
| **`CallOutcome`** | ENUM dentro de `Call` | Idem. |
| **Custom fields en Lead** | JSONB | Flexibilidad sin migrar DB. |
| **Hard delete vs soft** | Hard + AuditLog | Mínimo útil. |
| **Índices** | En columnas de filtro/búsqueda: `email`, `phone`, `status`, `assigned_to`, FKs, `created_at` | Performance en listados. |

## Entidades

### 1. `User`
- `id` UUID PK
- `email` VARCHAR unique (login)
- `password_hash` VARCHAR (bcrypt/argon2)
- `full_name` VARCHAR
- `role` ENUM: `ADMIN`, `SUPERVISOR`, `AGENT`
- `status` ENUM: `ACTIVE`, `DISABLED`
- `last_login_at` TIMESTAMP nullable
- `created_at`, `updated_at`

### 2. `Role`
❌ No es tabla. Es ENUM dentro de `User`.

### 3. `Lead`
- `id` UUID PK
- `first_name` VARCHAR, `last_name` VARCHAR
- `email` VARCHAR nullable
- `phone` VARCHAR nullable (formato E.164)
- `company` VARCHAR nullable (B2B, opcional para flexibilidad B2C)
- `status` ENUM: `NEW`, `ASSIGNED`, `IN_PROGRESS`, `QUALIFIED`, `NOT_QUALIFIED`, `CONVERTED`, `DISQUALIFIED`
- `source` ENUM: `MANUAL`, `IMPORT`, `API`
- `assigned_to` FK → User nullable
- `notes` TEXT nullable
- `custom_fields` JSONB nullable
- **`consent_at` TIMESTAMP nullable** *(compliance)*
- **`do_not_call` BOOLEAN default false** *(compliance)*
- **`data_retention_until` DATE nullable** *(compliance)*
- `created_at`, `updated_at`

**Constraints**:
- Al menos UNO entre `email` y `phone` debe estar presente (CHECK constraint).

### 4. `Campaign`
- `id` UUID PK
- `name` VARCHAR
- `description` TEXT nullable
- `status` ENUM: `DRAFT`, `SCHEDULED`, `RUNNING`, `PAUSED`, `FINISHED`, `CANCELLED`
- `start_at`, `end_at` TIMESTAMP nullable (ventana de ejecución)
- `script` TEXT nullable
- `created_by` FK → User
- `created_at`, `updated_at`

### 5. `CampaignLead` (tabla de unión con metadata)
- `campaign_id` FK → Campaign (parte de PK compuesta)
- `lead_id` FK → Lead (parte de PK compuesta)
- `status` ENUM: `PENDING`, `IN_PROGRESS`, `COMPLETED`, `SKIPPED`
- `attempts` INT default 0
- `last_attempt_at` TIMESTAMP nullable
- `assigned_to` FK → User nullable (override de Lead.assigned_to)
- `next_attempt_at` TIMESTAMP nullable (para retries programados)

### 6. `Call`
- `id` UUID PK
- `campaign_lead_id` FK → CampaignLead
- `user_id` FK → User (agente que hizo/registró)
- `started_at`, `ended_at` TIMESTAMP nullable
- `duration_seconds` INT nullable (derivable)
- `status` ENUM: `CONNECTED`, `VOICEMAIL`, `NO_ANSWER`, `BUSY`, `FAILED`
- `outcome` ENUM: `INTERESTED`, `NOT_INTERESTED`, `CALLBACK`, `APPOINTMENT_SET`, `NOT_REACHED`
- `recording_url` VARCHAR nullable
- `provider_call_id` VARCHAR nullable (ID del provider externo)
- `notes` TEXT nullable
- `created_at`, `updated_at`

### 7. `CallOutcome`
❌ No es tabla. Es ENUM dentro de `Call`.

### 8. `Appointment`
- `id` UUID PK
- `lead_id` FK → Lead
- `user_id` FK → User (agente que agendó)
- `scheduled_at` TIMESTAMP
- `duration_minutes` INT default 30
- `status` ENUM: `PENDING`, `CONFIRMED`, `COMPLETED`, `CANCELLED`, `NO_SHOW`
- `notes` TEXT nullable
- `created_at`, `updated_at`

### 9. `IntegrationConfig`
- `id` UUID PK
- `provider` VARCHAR (libre: `VAPI`, `RETELL`, `BLAND`, etc.)
- `api_key_encrypted` VARCHAR (nunca en plano)
- `webhook_secret` VARCHAR (para validar webhooks entrantes)
- `config_json` JSONB (config específica del provider)
- `enabled` BOOLEAN (kill switch)
- `created_at`, `updated_at`

### 10. `AuditLog`
- `id` UUID PK
- `user_id` FK → User nullable (null = sistema)
- `entity_type` VARCHAR
- `entity_id` UUID
- `action` ENUM: `CREATE`, `UPDATE`, `DELETE`, `STATUS_CHANGE`
- `changes_json` JSONB nullable
- `created_at` (solo este, no se actualiza)

## Cardinalidades

```
User 1 ── * Lead        (assigned_to)
User 1 ── * Campaign    (created_by)
User 1 ── * CampaignLead (assigned_to)
User 1 ── * Call
User 1 ── * Appointment
User 1 ── * AuditLog

Campaign 1 ── * CampaignLead * ── 1 Lead
CampaignLead 1 ── * Call
Lead 1 ── * Appointment
```

## Índices planeados

- `users(email)` UNIQUE
- `users(role)`, `users(status)`
- `leads(email)`, `leads(phone)`, `leads(status)`, `leads(assigned_to)`, `leads(company)`, `leads(do_not_call)`
- `campaigns(status)`, `campaigns(start_at)`, `campaigns(end_at)`, `campaigns(created_by)`
- `campaign_leads(campaign_id, status)`, `campaign_leads(lead_id)`, `campaign_leads(assigned_to)`
- `calls(campaign_lead_id)`, `calls(user_id)`, `calls(status)`, `calls(outcome)`, `calls(created_at)`
- `appointments(lead_id)`, `appointments(user_id)`, `appointments(scheduled_at)`, `appointments(status)`
- `integration_configs(provider, enabled)`
- `audit_logs(entity_type, entity_id)`, `audit_logs(user_id)`, `audit_logs(created_at)`
- `audit_logs` GIN en `changes_json` (búsqueda por contenido)

## Lo que NO incluimos (fuera del MVP)

- ❌ Tabla `Role` separada (ENUM alcanza)
- ❌ Tabla `CallOutcome` separada (ENUM alcanza)
- ❌ `Tag` / `Label` para Leads
- ❌ `Comment` separado (notes en cada entidad alcanza)
- ❌ `Team` o jerarquías (no confirmadas)
- ❌ Calendar sync (mejora futura)
- ❌ Multi-tenant
- ❌ Provider de voz propio (IntegrationConfig es solo el conector, no el motor)

## Migración inicial

Se materializará en `backend/src/main/resources/db/migration/V1__initial_schema.sql` cuando se cree el proyecto Spring Boot (próximo paso de Fase 2).