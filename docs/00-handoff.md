# Handoff Guide — Callsagents

> **Read this first if you're taking over this project.** It answers: what is this, how do I run it, where do I make changes, and what's already done vs. what's missing.

---

## 1. What is this

Callsagents is a **multi-tenant SaaS** that captures and converts website leads through an instant **WhatsApp chatbot** and **web chat widget**, with an **AI voice call as a fallback escalation** when a lead doesn't respond. It is **dogfooded on Script9** (www.script-9.com); all user-facing copy says "Script9", never "Callsagents". Each client business sets up its own branding, WhatsApp number, widget, and prompt through its `BusinessProfile`.

> **Do NOT read the words "outbound sales platform"** — the product has **pivoted** away from that. The original outbound-campaigns MVP (leads/campaigns/calls/appointments with ADMIN/SUPERVISOR/AGENT roles + Twilio) is **legacy scaffolding kept in-tree but deprecated**. It is not the product.

**This is a working SaaS, dogfooded.** It runs locally via Docker and on Railway in prod. Voice AI is **implemented** (Retell + web-call + webhooks) and the **Escalation Orchestrator** (WhatsApp follow-up → timeout → Retell voice) is **implemented and E2E-verified** (V17 + `EscalationScheduledTask`).

## 2. Stack (frozen — don't change without team discussion)

| Layer | Technology | Version |
|---|---|---|
| Frontend | Angular standalone components, signals, inject(), native `<dialog>` | 18.2 |
| Backend | Spring Boot, Java, Maven, `@ConfigurationPropertiesScan`, Flyway | 3.5.16, 21 |
| DB | PostgreSQL with native ENUMs + JSONB (`hypersistence-utils`) | 16-alpine |
| Cache + Auth state | Redis (refresh-token revocation: `refresh:`/`revoked:`) | 7-alpine |
| Auth | JWT (HS256, access 15min / refresh 7d rotatable) + BCrypt | nimbus-jose-jwt 10.0.2 |
| OAuth | Google (client id `557204149721-...`) | n/a |
| Chatbot LLM | Groq (`openai/gpt-oss-20b`) | n/a |
| WhatsApp | Vonage (sandbox dev / paid prod) | n/a |
| Voice | Retell AI via `VoiceProvider` abstraction; Vapi present as alternative; `WebhookSignatureValidator` fail-closed | n/a |
| ORM | Hibernate `@JdbcTypeCode(SqlTypes.NAMED_ENUM)` for Postgres ENUMs | 6.x |
| Migrations | Flyway (V1–V19; V13 missing, V2 dev-only under `db/migration/dev`) | 11.7.2 |
| Docs | springdoc-openapi (Swagger UI) | 2.9.0 |
| Containerization | Docker Compose (dev) + Railway (prod) | Docker 29+ |
| Scheduling | `@Schedule`/`@EnableScheduling` — **none exists** | n/a |

**Read the playbook-equivalent too**: `C:\Users\Antonio\Desktop\Callsagents\RUNBOOK.md` covers operational how-to (incl. the new Deploy-to-Railway section). This document is the developer-onboarding complement.

## 3. Prerequisites for your dev machine

| Tool | Required version | Check |
|---|---|---|
| Docker Desktop | 4.x with Compose v2 | `docker --version && docker compose version` |
| Git | 2.30+ | `git --version` |
| Java JDK | 21 (Temurin recommended) | `java --version` |
| Maven | 3.9+ | `mvn --version` |
| Node | 22 LTS | `node --version` |
| npm | 10+ | `npm --version` |
| Go | 1.25+ (only for `gentle-ai` tooling) | `go version` |

## 4. First-time setup

```bash
git clone https://github.com/Toni872/callsagents.git
cd callsagents

# Copy and edit .env
cp .env.example .env
# Edit .env: set JWT_SECRET and ENCRYPTION_KEY (use openssl rand -base64 32 for both)
# Also set RETELL_API_KEY / RETELL_AGENT_ID, VONAGE / GROQ keys if you need those paths.

# Start the stack
docker compose up -d
```

> **Windows Port note:** `docker-compose.override.yml` maps Postgres to **5433:5432** (because native Windows Postgres owns 5432). See RUNBOOK § "Acceder a la DB" — on Windows use `5433`.

Wait ~60s for the backend to apply Flyway migrations and start. Check health:

```bash
docker compose ps
# All 4 should show 'healthy' (frontend may show 'Up' without healthcheck)

# Smoke test — NOTE: the production admin is contact@script-9.com (V12/V19)
curl -X POST -H "Content-Type: application/json" \
  -d '{"email":"contact@script-9.com","password":"<ver secrets/env CALLSAGENTS_ADMIN_PASSWORD>"}' \
  http://localhost:8080/api/auth/login
```

Open in browser: `http://localhost/`. Login with `contact@script-9.com` / `<ver secrets/env CALLSAGENTS_ADMIN_PASSWORD>` (admin, prod seed V12/V19).

## 5. Repository structure

```
callsagents/
├── RUNBOOK.md                          # Operational how-to (start/stop/troubleshoot/deploy)
├── docs/                               # Architecture + decisions + handoff
│   ├── 00-handoff.md                   # ← you are here
│   ├── 01-arquitectura.md              # Live architecture (SaaS core vs legacy)
│   ├── 02-modelo-de-datos.md           # Live schema V1–V19
│   ├── 03-adrs.md                      # Architecture Decision Records (ADR-001..010)
│   ├── 16-railway-deploy.md            # Railway deploy specifics
│   ├── rdd-workflow.md                 # gentle-ai RDD gate
├── backend/                            # Spring Boot app
│   ├── src/main/java/com/callsagents/backend/
│   │   ├── auth/                       # JWT, security, login/refresh/logout, Google OAuth
│   │   ├── leads/                      # Lead CRUD + CSV import + filters
│   │   ├── chat/                       # ChatService (Caffeine) + /chat endpoints
│   │   ├── whatsapp/                   # Vonage + WhatsAppAiChatbotService + GroqService (Twilio removed 2026-08-29)
│   │   ├── voice/                      # VoiceCallService, VoiceProvider (Retell/Vapi), web-call, webhooks
│   │   ├── business/                   # BusinessProfile + BusinessPromptComposer + widget-config
│   │   ├── campaigns/                  # LEGACY outbound
│   │   ├── calls/                      # LEGACY call logging
│   │   ├── appointments/               # LEGACY appointments + calendar hook
│   │   ├── calendar/                   # LEGACY/PARTIAL Google (Outlook stub throws)
│   │   ├── users/                      # LEGACY user management
│   │   ├── dashboard/                  # LEGACY /dashboard/summary
│   │   ├── integrations/               # LEGACY IntegrationConfig entity
│   │   ├── audit/                      # AuditLog entity
│   │   ├── common/                     # GlobalExceptionHandler, ApiError, PaginationUtils, RateLimitFilter
│   │   └── config/                     # @ConfigurationPropertiesScan + app.* beans
│   ├── src/main/resources/
│   │   ├── application.yml             # Common config (app.voice, app.vonage, app.groq)
│   │   ├── application-dev.yml         # Dev profile
│   │   ├── application-test.yml        # Test profile
│   │   └── db/migration/               # Flyway V1..V19 (V13 gap; V2 under db/migration/dev)
│   ├── src/test/                       # 223 @Test methods across 23 test classes
│   └── Dockerfile                      # Backend (Railway root constraint)
├── frontend/                           # Angular app
│   ├── src/app/
│   │   ├── core/                       # api, auth, errors, layout, loading
│   │   ├── features/                   # dashboard, leads, campaigns, calls, voice-calls,
│   │   │                               #   appointments, users, settings(profile+calendar),
│   │   │                               #   auth login/register, onboarding wizard, chat-widget,
│   │   │                               #   landing, terms, privacy, widget
│   │   ├── shared/models/              # TS interfaces matching backend DTOs
│   │   ├── app.config.ts / app.routes.ts / app.component.ts
│   ├── nginx.conf                      # Reverse proxy /api/ + Origin-strip + COOP/COEP + SPA fallback
│   └── Dockerfile                      # Node 22 → nginx (npm@12 upgrade workaround)
├── docker-compose.yml                  # postgres16-alpine, redis7-alpine, backend, frontend
├── docker-compose.override.yml         # dev: postgres 5433:5432 (Windows native PG owns 5432)
├── Dockerfile                          # Root = backend only (Railway root constraint)
├── INFRASTRUCTURE.md                   # Railway services, URLs, admin creds
├── STRATEGY.md / PRD.md / ROADMAP.md   # Product & go-to-market
└── .env.example                        # Template for .env
```

## 6. Conventions (must follow)

### Java / Spring Boot

- **Standalone, single-class-per-file** style throughout
- **Lombok** for entities (`@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder`)
- **Records** for DTOs (Java 16+)
- **ENUMs**: `@Enumerated(EnumType.STRING) @JdbcTypeCode(SqlTypes.NAMED_ENUM)` — REQUIRED for Postgres native ENUMs
- **Audit fields**: every entity has `created_at` (`@PrePersist`) + `updated_at` (`@PreUpdate`)
- **Soft delete**: NO. Hard delete + AuditLog.
- **UUIDs as PKs** everywhere via `@UuidGenerator`
- **Controllers**: thin — inject `Authentication`, call services. Services hold all logic.
- **DTOs**: always at the controller boundary; never expose entities directly.
- **Tests**: Mockito + JUnit 5. `@ExtendWith(MockitoExtension.class)`. Avoid `@SpringBootTest`; `@WebMvcTest` only if needed. **214 @Test / 22 classes currently.**

### Frontend (Angular)

- **Standalone components** (no NgModules)
- **`ChangeDetectionStrategy.OnPush`** on every component
- **Signals** (`signal`, `computed`); **`inject()`** for DI
- **Templates**: `@if`, `@for`, `@switch` (no `*ngIf`/`*ngFor`)
- **Native `<dialog>`** for modals (no Material/CDK)
- **Services in `core/api/`** one per backend module; never inject `HttpClient` in a component
- **Lazy load** routes: `loadComponent: () => import('...')`
- **CSS**: CSS variables from `src/styles.css`. No Tailwind/Material.

### Git

- **Conventional commits**: `feat(scope):`, `fix(scope):`, `docs:`, `chore:`
- One commit per logical change. Body explains WHY.
- Branch from `main`, PR into `main`.

### Receipt-Driven Development (RDD)

```bash
gentle-ai review start --projection workspace --cwd "C:\Users\Antonio\Desktop\Callsagents"
git add . && git commit -m "..." && git push origin main
```

Read `docs/rdd-workflow.md` for details. If push fails with "candidate has drifted", you modified files after `review start` — abort and re-start.

## 7. Where to add what

| You want to... | Touch this | Be careful |
|---|---|---|
| Add a new REST endpoint | `backend/.../{module}/controller/`, service, dto | `@PreAuthorize`, `@Schema`, add to the live-module table in `01-arquitectura.md` |
| Add a field to an existing entity | `backend/.../entity/Entity.java` + new `V{n}__...sql` | NEVER edit V1–V19 migrations; add a new one. Set DEFAULT for NOT NULL. |
| Add a new ENUM value | New `V{n}__...sql`: `ALTER TYPE foo ADD VALUE 'BAR';` | Postgres ENUMs immutable; restart backend after. |
| Add a new native enum to an entity | `@Enumerated(STRING) + @JdbcTypeCode(NAMED_ENUM)` | The classic "expression is of type character varying" bug otherwise. |
| Add a new nav item | `core/layout/main-layout/main-layout.component.ts` (`navItems`) | Add route in `app.routes.ts`. |
| Add a new page/feature | `features/{feature}/` | Lazy-load route. Follow `users/` or `voice-calls/` layout. |
| Add a new migration | `backend/src/main/resources/db/migration/V{n}__desc.sql` | Sequential V-number. Note **V13 is missing** — do not create a "V13" collision; numbering is by file name. |
| Change the JWT secret | `application.yml` (`app.jwt.secret`) + `.env` (`JWT_SECRET`) | ≥32 bytes / 256 bits; `openssl rand -base64 32`. |
| Add/provide a voice provider | `backend/.../voice/service/VoiceProvider.java` impl | Retell/Vapi abstraction; signature validation is fail-closed. |
| Change WhatsApp provider | `backend/.../whatsapp/` | Vonage is the only path (Twilio removed 2026-08-29). |

## 8. Tests

- **223 @Test methods across 23 test classes** currently. Run with `mvn test` from `backend/`.
- Naming: `{MethodName}_when{State}_then{Expected}`.
- Always test: happy path, validation failure, edge cases (null/blank/empty), security (forbidden).
- See recent services for the pattern: `@ExtendWith(MockitoExtension.class)` + mocks + `@MockitoSettings(strictness = Strictness.LENIENT)` where stubs may not run on every path.

## 9. State of the project (SaaS pivot)

| Area | Status |
|---|---|
| Auth (email + Google OAuth, JWT rotation, Redis revoked + reuse detection) | ✅ live |
| Business profiles + onboarding wizard | ✅ live |
| Chat widget (per-tenant prompt) | ✅ live |
| WhatsApp chatbot (Vonage + Groq) — dogfooded on Script9 | ✅ live |
| Voice web-call (WebRTC) + webhooks + provider abstraction | ✅ live |
| Leads (CRUD + CSV + sources incl. WHATSAPP/WEB_CHAT) | ✅ live |
| **Legacy outbound** (campaigns/calls/appointments/calendar-partial) | ✅ done but **deprecated** |
| Calendar sync (Google; Outlook stub throws) | ⚠️ partial |
| **Escalation Orchestrator** (WhatsApp follow-up → timeout → Retell outbound voice) | ✅ **live** (ADR-009, V17, `EscalationScheduledTask`) |
| Retell **phone** outbound live | ❌ blocked — `RETELL_FROM_NUMBER` empty; only web-call works |
| Stripe billing | 🔜 planned |
| Per-tenant trial enforcement | ❌ currently **global** (7 days / 50 leads) |
| Testcontainers / observability / prod hardening | ❌ deferred until traffic |

## 10. Known issues and sharp edges

1. **Flyway V13 gap** — there is no `V13` migration file (jumps V12 → V14). Harmless to Flyway (it sorts by version), just note it so nobody "fills the gap" colliding with real numbers.
2. **Hibernate + Postgres ENUMs**: ALWAYS `@Enumerated(STRING) + @JdbcTypeCode(NAMED_ENUM)`. Without NAMED_ENUM → "column X is of type Y but expression is of type character varying".
3. **Trial cap is GLOBAL, not per-tenant**: 7 days / 50 leads (`TRIAL_LEAD_LIMIT = 50` in `ChatService`/`LeadService`) is a single global counter, not scoped per `BusinessProfile`. Must become per-tenant before multi-tenant rollout.
4. **`V10` comment says "14-day"** but the real trial is **7 days**. Do not trust the migration comment; trust code/config.
5. **`RETELL_FROM_NUMBER` is empty** → real outbound phone calls are **blocked**. Only the WebRTC **web-call** path works today. Set this env var to enable phone outbound.
6. **VonageConfig key-prefix gotcha**: config is under `app.vonage.api.*` (not `vonage.*`) — check `application.yml` prefixes before wiring config. (Note `app.*` prefix applies to voice/vonage/groq blocks.)
7. **Calendar redirect mismatch**: the Google calendar callback/redirect URL must exactly match the registered one, or OAuth fails. Validate before changing hosts.
8. **AuthController `/me`** must use `Authentication.getName()` (principal is a String, not `UserDetails`).
9. **EncryptionService MVP-tolerant**: if `ENCRYPTION_KEY` is empty the bean still boots with `ready=false`; calendar encrypt/decrypt return 503.
10. **Console 401 noise**: `GET /api/auth/me` fires on `MainLayoutComponent` mount when logged out — cosmetic.
11. **Scheduler exists**: `EscalationScheduledTask` runs `@Scheduled(fixedDelay = 60000)` and elevates `WAITING_REPLY` escalations past their per-business timeout to Retell voice calls (in-process lock guards overlap). This is the only `@Scheduled`; no campaign/call auto-dialing exists.

## 11. Glossary

- **SaaS core**: the live product modules (auth, leads, chat, whatsapp, voice, business).
- **Legacy / LEGACY**: outbound-campaign scaffolding retained in-tree (campaigns, calls, appointments, calendar-partial, users).
- **Candidate**: set of changes frozen for review (RDD).
- **Receipt**: SHA-256 of the candidate (RDD).
- **VoiceProvider**: abstraction over Retell/Vapi for placing voice calls.
- **Refresh rotation**: `/auth/refresh` issues a new refresh AND revokes the old (Redis); reuse detection revokes the session.
- **Migrations**: numbered SQL in `db/migration/`, applied in order. NEVER edit a shipped migration; add a new one.

## 12. Roadmap (next)

1. **Escalation Orchestrator** (ADR-009) — build + test locally with Vonage sandbox + web-call. First real scheduler in the codebase.
2. **Production voice**: paid Vonage number + set `RETELL_FROM_NUMBER` (needs user action/credentials).
3. **First pilot / dogfood** — Script9 captures 5 clients.
4. **Stripe billing** + per-tenant trial enforcement.
5. **Scale**.

See `ROADMAP.md` for the full phase detail and Script9 vs Callsagents role split.

---

**If you get stuck**: read `RUNBOOK.md` (operational), this file (developer onboarding), then `docs/01-arquitectura.md` and `docs/03-adrs.md` for the real architecture and the reasoning behind it. When in doubt, extend the SaaS core (auth/leads/chat/whatsapp/voice/business) — do NOT extend the legacy outbound modules.
