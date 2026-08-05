# Producto — Callsagents

## Objetivo

Plataforma web para que un equipo comercial ejecute **campañas outbound** de manera sistemática: cargar leads, lanzar campañas, registrar resultados de llamadas, hacer handoff a humano y agendar citas.

## Alcance del MVP

Incluye:
- Gestión de leads (CRUD, importación masiva, filtros, segmentación).
- Gestión de campañas (crear, lanzar, pausar, monitorear).
- Registro de llamadas y resultados (manual; integración con provider de voz externo como servicio auxiliar).
- Asignación de leads a operadores.
- Agendamiento de citas.
- Autenticación con JWT (access + refresh rotable) y tres roles: ADMIN, SUPERVISOR, AGENT.
- Panel web en Angular con servicios de API tipados.

No incluye (todavía):
- Motor propio de IA de voz. El MVP valida flujo de negocio, no calidad máxima del modelo.
- Marcación predictiva.
- Analytics avanzados / dashboards ejecutivos.
- Multi-tenant.
- Facturación.

## Restricciones

- **Stack cerrado**: Angular + Spring Boot + PostgreSQL + Redis + JWT + Swagger + Docker Compose + CI/CD. Definido en `docs/01-arquitectura.md`.
- **IA como servicio auxiliar**: la IA de voz se integra vía provider externo (Vapi / Retell / Bland) cuando haya caso de uso confirmado, no se construye motor propio en el MVP.
- **Sin sobreingeniería**: cada abstracción, clase o servicio debe tener una razón de existir hoy. No se generaliza sin un segundo caso de uso real.
- **Fases secuenciales**: cada fase del playbook debe estar cerrada y verificada antes de avanzar a la siguiente.

## Métricas de éxito (placeholder)

A definir cuando el MVP esté operativo. Criterios preliminares:
- Un operador puede autenticar, ver sus leads asignados y registrar resultados de llamadas sin fricción.
- Una campaña se lanza, ejecuta y registra resultados de manera verificable.
- Toda la API está documentada en Swagger y un dev externo puede probar el flujo completo desde ahí.