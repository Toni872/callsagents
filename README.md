# Callsagents

**Callsagents is a multi-tenant SaaS that captures and converts website leads through an instant WhatsApp chatbot and web chat widget, with an AI voice call as a fallback escalation when a lead doesn't respond.** It is currently **dogfooded on Script9** (www.script-9.com) — every user-facing message says "Script9", never "Callsagents". Client businesses set up their own chatbot branding, WhatsApp number, widget, and prompt through their `BusinessProfile`.

```
Lead enters client's website
        ↓
chat widget captures lead
        ↓
chatbot qualifies (prompt per business)
        ↓
WhatsApp chatbot writes to lead
        ↓
positive response? ──yes──→ offer service
        ↓ no (timeout)
Retell AI voice call (fallback only)
```

---

## Stack

| Layer | Technology | Version |
|---|---|---|
| Frontend | Angular (standalone, signals, `inject()`, native `<dialog>`) | 18.2 |
| Backend | Spring Boot / Java, Maven, `@ConfigurationPropertiesScan`, Flyway | 3.5.16 / 21 |
| Database | PostgreSQL with native ENUMs (`NAMED_ENUM`) + JSONB (`hypersistence-utils`) | 16-alpine |
| Cache / Auth state | Redis (refresh-token revocation: `refresh:` / `revoked:` keys) | 7-alpine |
| Auth | Nimbus JWT MAC HS256 (access 15 min / refresh 7 d rotating), `BCryptPasswordEncoder(10)`, Redis revocation + reuse detection | nimbus-jose-jwt 10.0.2 |
| Chatbot LLM | Groq (`openai/gpt-oss-20b`) | n/a |
| WhatsApp | Vonage (sandbox for dev, paid number for prod) | n/a |
| Voice | Retell AI (`retellai.com`) via `VoiceProvider` abstraction; Vapi present as alternative | n/a |
| Migrations | Flyway | V1–V16 (V13 missing, V2 dev-only under `db/migration/dev`) |
| API docs | springdoc-openapi (Swagger UI) | 2.9 |
| `@Scheduled`/`@EnableScheduling` | **None anywhere in the codebase** | n/a |

---

## Where to go for what

| You want to... | Start here |
|---|---|
| Run it locally (start/stop/troubleshoot) | [`RUNBOOK.md`](RUNBOOK.md) |
| Deploy to Railway | [`RUNBOOK.md` § Deploy to Railway](RUNBOOK.md), [`docs/16-railway-deploy.md`](docs/16-railway-deploy.md), [`INFRASTRUCTURE.md`](INFRASTRUCTURE.md) |
| Onboard as a developer | [`docs/00-handoff.md`](docs/00-handoff.md) |
| Understand the architecture | [`docs/01-arquitectura.md`](docs/01-arquitectura.md) |
| Understand the data model | [`docs/02-modelo-de-datos.md`](docs/02-modelo-de-datos.md) |
| Why we made each decision | [`docs/03-adrs.md`](docs/03-adrs.md) |
| Product requirements | [`PRD.md`](PRD.md) |
| Go-to-market strategy (Script9) | [`STRATEGY.md`](STRATEGY.md) |
| Product roadmap / phases | [`ROADMAP.md`](ROADMAP.md) |

---

## Current state

| Area | Status |
|---|---|
| **SaaS core (LIVE)** | ✅ Auth (email + Google OAuth), `BusinessProfile` multi-tenancy + onboarding, chat widget, WhatsApp chatbot (Vonage + Groq), voice web-call (WebRTC), leads, per-tenant prompt composer |
| **Legacy outbound (kept in-tree)** | ✅ Campaigns / Calls / Appointments — **deprecated**, no scheduling, recording tables only |
| **Calendar sync** | ⚠️ Partial — Google only, Outlook stub throws |
| **Escalation Orchestrator** | 🔜 **Next** — WhatsApp follow-up → timeout → Retell outbound voice (designed in ADR-009, not yet implemented) |
| **Retell phone outbound** | ⚠️ Blocked — `RETELL_FROM_NUMBER` currently empty; only WebRTC web-call works |
| **Stripe billing** | 🔜 Planned |

> **Legacy vs live:** the outbound campaign/calls/appointment modules are scaffolding kept in-tree for reference (Twilio was removed 2026-08-29). They are **not** the product. The product is the SaaS chat + voice lead-capture flow described above. Grep for `@Scheduled` — there is none; nothing auto-dials.
