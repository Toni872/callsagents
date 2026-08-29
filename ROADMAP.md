# Hoja de Ruta: Callsagents → Primeros Clientes

> **Estado actualizado**: el pivot SaaS está implementado (chat WhatsApp + widget web + voz web-call). Esta hoja refleja lo que está **LIVE**, lo que está **bloqueado** y lo que sigue.

## Roles claros

| Qué | Quién | Dónde |
|-----|-------|-------|
| **Script9** | Marca / empresa (customer #1 / dogfood) | www.script-9.com (live) |
| **Callsagents** | Producto SaaS | callsagents-frontend-production.up.railway.app |

Toda la copia de cara al usuario dice **"Script9"**, nunca "Callsagents" (decisión de marca).

---

## Estado real del producto

### ✅ LIVE (ya funciona, no tocar salvo para mantener)

- **Web chat widget** + captura de lead (`source = WEB_CHAT`) — prompt por tenant (`BusinessPromptComposer`).
- **WhatsApp chatbot** (Vonage + Groq `openai/gpt-oss-20b`): intents `intent_ventas|soporte|demo` → timing → confirm; upsert de lead por teléfono (`source = WHATSAPP`).
- **Onboarding wizard** + multi-tenancy `BusinessProfile` (1:1 con `User`, `businessId == userId`).
- **Auth** (email + Google OAuth, JWT rotación + revocación Redis, reuse detection).
- **Voz web-call** (WebRTC) + webhooks + abstracción de provider (`VoiceProvider` Retell/Vapi).
- Backend en Railway (API) + frontend en Railway (SPA).
- Dogfooding en Script9 (www.script-9.com).

### ⚠️ BLOQUEADO / PARCIAL

- **Llamada telefónica saliente Retell NO está live**: `RETELL_FROM_NUMBER` está **vacío** → sólo funciona web-call (WebRTC). Para habilitar teléfono hay que setear la variable (acción del usuario + número Vonage pagado).
- **Calendar sync**: sólo Google (Outlook stub tira excepción).
- **Trial real**: **7 días / 50 leads**, GLOBAL (no por tenant). La migración V10 dice "14 days" por error — el comportamiento real es 7 días.

### ❌ ELIMINADO

- La demo `callsagents/demo` **ya no existe** (fue quitada). No referenciarla.
- El MVP original "outbound campaigns" está en el repo — origen de los módulos que hoy son **activos pero secundarios** (el SaaS chat+voice es el producto). No es el foco de trabajo nuevo; consultar `docs/01-arquitectura.md §8` antes de tocar esos módulos.

---

## Roadmap por fases

### Fase 1 — Escalation Orchestrator (AHORA; build + test local con sandbox)

**Objetivo**: conectar el eslabón que falta: cuando un lead no responde al WhatsApp/chat, escalar automáticamente a una **llamada de voz Retell** (fallback sólo). Diseño aprobado en ADR-009.

1. Implementar el orquestador: WhatsApp follow-up → timeout (configurable por negocio) → Retell outbound.
2. Persistir el estado de escalado (nueva tabla `escalations` + 4 columnas en `business_profiles` — ver `docs/02-modelo-de-datos.md`).
3. **Primer scheduler real del código** (hoy NO existe `@Scheduled`/`@EnableScheduling`).
4. Probar **localmente con sandbox de Vonage + web-call** (no requiere número pagado).

**Salida**: lead que ghostea recibe follow-up y, si no responde, una llamada de voz que re-cualifica y (si hay interés) agenda.

### Fase 2 — Producción de voz (necesita acción del usuario)

**Objetivo**: habilitar llamadas telefónicas reales salientes.

- Número **Vonage pagado** (el sandbox no puede contactar números reales).
- Setear **`RETELL_FROM_NUMBER`** en Railway (actualmente vacío → bloquea teléfono).
- Verificar end-to-end: lead → chat → timeout → llamada real → booking sin intervención humana.

Simplemente activar esto es un **gating item**: hasta que no está, la voz es una demo web y no el diferenciador completo.

### Fase 3 — Primer piloto / dogfood (concurren a la Fase 2)

**Objetivo**: 1 cliente real usando Callsagents.

1. Identifica ~10 prospectos con dolor de leads (no limitarse a academias — el producto es **agnóstico de nicho**).
2. Ofrece piloto gratis: "Te configuro Callsagents 7 días / 50 leads. Si no te funciona, no pagas."
3. Tú haces el setup: prompt, número, widget.
4. **Mide**: leads capturados, tiempo de respuesta, citas agendadas, tasa de recuperación por voz.

El pitch: *"Tus leads escriben por WhatsApp o web. Callsagents les responde en <1 min, cualifica y agenda la cita. Si no responden, les llama."*

### Fase 4 — Stripe billing + trial por tenant

**Objetivo**: facturación y cortar el trial por tenant.

- Hoy el trial (7 días / 50 leads) es **GLOBAL**, no por tenant → antes de vender a varios clientes hay que hacerlo **per-tenant** (ADR-007 caveat).
- Integrar **Stripe** para cobrar clientes reales.

### Fase 5 — Escalar

1. Documenta el caso de éxito (métricas antes/después).
2. Pide testimonio.
3. Vende a 5 clientes más.
4. Escala infraestructura (observabilidad, hardening, Secret Manager).

---

## Métricas a trackear (objetivo: captar 5 clientes)

| Métrica | Dónde |
|---|---|
| Leads contactados | Dashboard / leads |
| Respuestas obtenidas (tasa de respuesta) | Dashboard |
| Tiempo de primera respuesta | Chat |
| Leads recuperados por voz (escalation) | `voice_calls` / escalations |
| Citas agendadas | appointments |
| Pilotos acordados | Manual (CRM) |

---

## Resumen visual

```
FASE 1:  Escalation Orchestrator (AHORA — test local con sandbox)
FASE 2:  Producción de voz (número Vonage + RETELL_FROM_NUMBER)   ← gating
FASE 3:  Primer piloto / dogfood (1 cliente real)
FASE 4:  Stripe billing + trial por tenant
FASE 5:  5 clientes y escalar
```

---

## Lo que YA funciona (no tocar)

- ✅ WhatsApp chatbot (Script9) — Vonage + Groq
- ✅ Web chat widget + captura de leads
- ✅ Onboarding + BusinessProfile multi-tenant
- ✅ Auth + Google OAuth
- ✅ Voz web-call (WebRTC)
- ✅ Backend + Frontend en Railway

## Lo que FALTA / BLOQUEADO

- 🔲 Escalation Orchestrator (Fase 1 — próximo)
- ⏸ Llamadas telefónicas Retell (`RETELL_FROM_NUMBER` vacío)
- 🔲 Número Vonage pagado (necesario para voz telefónica real)
- 🔲 1 caso de éxito documentado
- 🔲 Stripe billing + trial por tenant

---

*Última actualización: 27 agosto 2026*
