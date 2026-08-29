# Producto — Callsagents (SaaS: WhatsApp + Voz, lead capture)

> **Actualizado — el producto pivotó.** El documento original describía una plataforma de *outbound campaigns*. El producto real es un **SaaS multi-tenant que captura y convierte leads** mediante una respuesta instantánea por WhatsApp/web-chat, con **escalado a llamada de voz como fallback**. Se dogfoodea en **Script9**.

## Objetivo

Un SaaS multi-tenant que **captura y convierte leads** respondiendo al instante por **WhatsApp** y **widget web**, cualificando automáticamente, y **escalando a una llamada de voz IA (Retell)** como fallback cuando el lead no responde. Cada negocio cliente configura su propia marca, número de WhatsApp, widget y prompt vía su `BusinessProfile`. Se dogfoodea en **Script9** (www.script-9.com); toda la copia de cara al usuario dice "Script9".

## Flow del producto

```
Lead entra al website del cliente
  → chat widget captura el lead
  → chatbot cualifica (prompt por tenant)
  → WhatsApp chatbot escribe al lead
      ├─ respuesta positiva → ofrecer servicio
      └─ sin respuesta (timeout) → escalar a llamada de voz Retell (fallback)
```

## Alcance

### ✅ Live (SaaS core)

- **Auth**: registro/login, **Google OAuth**, JWT rotación (access 15 min / refresh 7 d) + revocación Redis con reuse detection.
- **Business profiles**: multi-tenancy (`BusinessProfile` 1:1 `User`, `businessId == userId`), onboarding wizard, branding (bot name, greeting, color, prompt por negocio).
- **Chat widget**: en el sitio del cliente, con mensaje inicial y prompt per-tenant.
- **WhatsApp chatbot**: Vonage + Groq (`openai/gpt-oss-20b`); intents (ventas/soporte/demo) → timing → confirmación; upsert de lead por teléfono.
- **Voz web-call** (WebRTC) + webhooks + abstracción de provider (Retell/Vapi), validación de firma fail-closed.
- **Leads**: CRUD + import CSV + filtros; fuentes `MANUAL|IMPORT|API|WHATSAPP|WEB_CHAT`; estados estándar.
- **Dashboard**: `/dashboard/summary` con métricas live.

### 🔜 En alcance (próximo)

- **Escalation Orchestrator** (ADR-009): WhatsApp follow-up → timeout (por negocio) → **llamada de voz saliente Retell**. Primer scheduler real del sistema.
- **Voz telefónica saliente** (requiere `RETELL_FROM_NUMBER` + número Vonage pagado).

### ⚠️ Parcial / bloqueado

- **Calendar sync**: sólo Google (Outlook stub tira excepción).
- **Trial real**: **7 días / 50 leads**, actualmente **GLOBAL** (no por tenant).

## No-incluye (non-goals)

- **Outbound / cold calling**: NO llamadas en frío (carga legal en España). La voz es **sólo fallback** para leads que ya contactaron / están en el funnel. Primera versión: sólo leads con consentimiento previo.
- **Motor propio de IA de voz**: se integra vía provider externo (Retell). No se construye motor.
- **Facturación (Stripe)**: hasta validar el producto con clientes reales.
- **Enforcement de trial por tenant**: actualmente el cap (50 leads / 7 días) es global; per-tenant llega con Stripe.
- **Diversificación de roles AGENT/SUPERVISOR/ADMIN**: vestigio del MVP outbound; cada tenant es admin de su profilo.

## Restricciones

- **Agnóstico de nicho**: NO es sólo para academias. El nicho a definir, pero el producto no se limita.
- **Cara de usuario "Script9"**: nunca "Callsagents" en textos de usuario.
- **Stack congelado**: Angular 18.2 + Spring Boot 3.5 + PostgreSQL 16 (ENUMs nativos + JSONB) + Redis 7 + JWT + Swagger + Docker Compose/Railway. Definido en `docs/01-arquitectura.md`.
- **IA como servicio auxiliar**: voz = provider externo; no construir motor propio.
- **Sin sobreingeniería**: cada abstracción debe tener razón de existir hoy. No generalizar sin un segundo caso de uso real.
- **MVP-origin no se extiende**: los módulos outbound (campaigns/calls/appointments/calendar) tienen origen en el MVP, pero **varios están activos hoy** en el flujo live (voz lee campaign config, `voice_calls` referencia appointments, auth usa users, dashboard es la página principal). El trabajo nuevo de producto va al SaaS core (auth, leads, chat, whatsapp, voice, business); los módulos MVP-origin **no son objetivo de borrado**. Twilio fue eliminado por completo (2026-08-29); `integration_configs` se eliminó en V20.

## Métricas de éxito

- Un lead recibe respuesta en **<1 min** (evita ghosting).
- Un lead que ghostea es **recuperado por voz** (escalation) y, si interesa, agenda cita sin intervención humana.
- Un negocio cliente puede configurar marca/número/widget/prompt y captar leads en su propio canal.
- Piloto dogfooded: **5 clientes** captados vía Script9 con metricas documentadas.

## Documentos relacionados

- `README.md` — índice + estado real · `docs/01-arquitectura.md` — arquitectura · `docs/02-modelo-de-datos.md` — schema · `docs/03-adrs.md` — decisiones · `ROADMAP.md` — fases · `STRATEGY.md` — go-to-market.
