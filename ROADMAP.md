# Hoja de Ruta: Callsagents → Primeros Clientes

## Roles claros

| Qué | Quién | Dónde |
|-----|-------|-------|
| **Script9** | Marca / empresa | www.script-9.com (ya live) |
| **Callsagents** | Producto SaaS | callsagents-frontend-production.up.railway.app |

---

## Los 3 canales que tienes (y cómo se conectan)

```
┌─────────────────────────────────────────────────────┐
│                   CLIENTE POTENCIAL                 │
│                                                     │
│  1. WhatsApp (Naiara)    ← canal CÁLIDO, boca a boca│
│  2. Demo web (Callsagents) ← canal FRÍO, prospección│
│  3. Voz (Retell)          ← ESCALADO, lead perdido  │
└───────────┬─────────────────────┬───────────────────┘
            │                     │
            ▼                     ▼
   ┌────────────────┐   ┌──────────────────────┐
   │ WhatsApp chat  │   │ Demo interactiva     │
   │ Vonage+Groq    │   │ callsagents/demo     │
   │ (ya funciona)  │   │ (ya funciona)        │
   └────────┬───────┘   └──────────┬───────────┘
            │                      │
            ▼                      ▼
   ┌────────────────────────────────────────────┐
   │        Callsagents Backend (Railway)       │
   │  - Chat REST API (POST /chat/message)      │
   │  - Lead capture                            │
   │  - Conversation history                    │
   └────────────────────────────────────────────┘
```

---

## Fase 1: Dogfooding inmediato (esta semana)

**Objetivo**: Usar Callsagents tú mismo para captar tus primeros 5 clientes.

### Qué hacer
1. **Usa tu propio WhatsApp chatbot** para responder leads reales
   - Pon el número de Vonage en la web de Script9 (sección contacto)
   - Cuando alguien escriba por WhatsApp, Naiara responde
   - Tú supervisas y saltas cuando el lead esté cualificado

2. **La demo web como herramienta de ventas**
   - Cuando hables con un prospecto, muéstrale la demo
   - "Mira, esto es lo que tus leads verían"
   - La demo es tu argumento de venta, no el producto final

3. **Flujo de ventas simple**
   ```
   Prospección → WhatsApp/demo → Cualificación → Videollamada → Piloto
   ```

### Métricas a trackear
- Leads contactados
- Respuestas obtenidas
- Demos mostradas
- Pilotos acordados

---

## Fase 2: Voz (Retell AI) — La semana que viene

**Por qué importa**: La voz es tu DIFERENCIADOR. Nadie más ofrece chat + voz automática.

### Estado actual
- Agente Retell creado: `agent_9fda91a4d3ddaa0f8c8cbfa7c9`
- Falta: integrar la llamada automática cuando el chat no funciona

### Plan
1. Conectar Retell con el backend (webhook de llamadas salientes)
2. Cuando un lead no responda al chat → llamada automática
3. El agente de voz cualifica por teléfono
4. Si hay interés → agenda cita directamente

### Resultado
- Chat responde en <1 min
- Si no responden → llamada automática
- Cita agendada sin intervención humana

---

## Fase 3: Primer piloto (2-3 semanas)

**Objetivo**: 1 cliente real usando Callsagents.

### Cómo conseguirlo
1. **Identifica 10 prospectos** en tu red (empresas que tengan leads)
2. **Ofrece piloto gratis**: "Te configuro Callsagents 14 días. Si no te funciona, no pagas."
3. **Tú haces el setup**: Configura el chatbot con su prompt, conecta su calendario
4. **Mide resultados**: Leads capturados, tiempo de respuesta, citas agendadas

### El pitch
> "Tus leads escriben por WhatsApp o web. Callsagents les responde en menos de 1 minuto, cualifica y agenda la cita. Si no responden, les llama automáticamente. ¿Quieres probarlo 14 días gratis?"

---

## Fase 4: Escalar (mes 2-3)

Una vez tengas 1 caso de éxito:
1. Documenta el caso (métricas antes/después)
2. Pide testimonio al cliente
3. Usa eso para vender a 5 clientes más
4. Implementa facturación (Stripe)

---

## Resumen visual

```
SEMANA 1:  Dogfooding (WhatsApp + demo)
SEMANA 2:  Integrar voz (Retell)
SEMANA 3:  Primer piloto (1 cliente)
MES 2:     5 clientes
MES 3:     Facturación + escalar
```

---

## Lo que YA funciona (no tocar)

- ✅ WhatsApp chatbot (Naiara) — Vonage + Groq
- ✅ Demo web — callsagents/demo
- ✅ Backend — Railway (chat API, leads, auth)
- ✅ Registro + trial 14 días
- ✅ Script9 web — marca live

## Lo que FALTA

- 🔲 Integrar voz (Retell) con el chat
- 🔲 Conectar WhatsApp de Vonage con la web de Script9
- 🔲 1 caso de éxito documentado
- 🔲 Sistema de facturación (Stripe)

---

*Última actualización: 24 agosto 2026*
