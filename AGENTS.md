# MUSK Technologies — Primer MVP conversacional (WhatsApp + IA)

Proyecto de Script9 para MUSK Technologies (interlocutor: Nicolas Stella). Este archivo es la fuente de verdad del alcance. El detalle completo está en `docs/musk-mvp-spec.md`; léelo antes de tocar flujo, prompts del agente, estados o métricas.

Fuente: reunión del 28/09/2026 (prevalece) + contexto de la reunión del 18/09/2026.

## Objetivo

Comprobar si WhatsApp reactiva leads antiguos de UNA formación y los convierte en llamadas humanas útiles, respetando los límites de información y la cercanía de MUSK. No se busca "vender más": se busca contacto, confianza y encaje del alumno. El cierre de la venta es siempre humano.

## Alcance del MVP (dentro)

- Una sola formación (aún por elegir) y dos bolsas de leads antiguos: **ilocalizables** y **personas que no compraron**.
- Primer contacto por WhatsApp -> dudas y preguntas breves -> propuesta de llamada -> aviso al comercial con identificación del contacto -> registro del resultado.
- Pocas preguntas de cualificación (propuestas: experiencia de partida y tiempo de interés). Sin examen, sin puntuación, sin exclusión automática.
- Registro simple de conversaciones y resultados + vista de supervisión básica para administración.
- Primero simulaciones sin datos reales con Nicolas; después muestra controlada con leads reales.

## Fuera de alcance (NO implementar sin decisión expresa)

Integración completa con Clientify (CRM actual), creación/asignación automática de oportunidades, agenda automática (Calendly u otra), agente de voz, dashboard analítico avanzado, catálogo completo de cursos, automatización de matrículas, atención de leads nuevos fuera de horario o desde la web, integración con la empresa externa de llamadas, integración con el CRM propio de MUSK (en desarrollo).

## Reglas duras del agente conversacional (confirmadas por Nicolas)

1. **Nunca comunicar precios ni importes.** Ante preguntas de precio: respuesta aprobada + invitación a la llamada.
2. **Nunca comunicar temario detallado.** El contenido general, habilidades y salidas sí se pueden explicar.
3. **Financiación:** solo indicar que existen opciones y remitir a la llamada. No desarrollar condiciones.
4. Responder SOLO con información aprobada por MUSK. No inventar condiciones, promociones, garantías ni promesas de empleo.
5. Si no hay respuesta aprobada: registrar la duda y proponer contacto humano.
6. Ninguna oferta para no compradores salvo mensaje aprobado previamente por Nicolas. Pedir precio o mencionar una oferta no autoriza a dar importes.
7. Respetar el rechazo: si el lead pide parar, se detiene el seguimiento y se registra.
8. Sin presión artificial. No prometer atención inmediata: solo el compromiso de contacto que MUSK pueda atender.
9. Disparadores de derivación a humano (confirmados): el lead pide hablar con una persona, o acepta/acuerda una llamada. Otros escalados son propuestas por definir.
10. Una guía usada como fuente de conocimiento NO autoriza enviarla íntegra al lead. Si un dossier tiene temario, se excluye del contexto del agente.
11. No hay aprendizaje autónomo de políticas comerciales: las respuestas nuevas se revisan antes de incorporarse al contenido aprobado.

## Decisiones abiertas (no asumir; dejar configurable y marcar `TODO(decisión)`)

Formación elegida · tamaño de muestra y fecha de lanzamiento · cadencia de seguimiento y reglas de parada · texto del mensaje inicial · lista final de preguntas de cualificación · nombres definitivos de las etiquetas conversacionales · receptor de los avisos y su cobertura · mecanismo de llamada (solicitar contacto vs acordar momento) · configuración del canal WhatsApp · base legal / privacidad / transparencia del lote real · umbrales de éxito.

Importante: los "6 intentos" de la reunión del 18/09 NO son una regla del MVP. El ~3 % de conversión, el coste (>300 €) y los volúmenes (12-15 / 35 al mes) son contexto histórico sin denominador; no son objetivos ni parámetros.

## Convenciones técnicas

- Las etiquetas conversacionales del asistente son distintas de los estados del CRM (nuevo, pendiente, beca, perdido…). No las mezcles ni las mapees por API en esta fase.
- El aviso al comercial por WhatsApp debe incluir contacto o teléfono para que lo localice en el CRM.
- El lote de prueba debe estar aislado de otras campañas y de la empresa externa de llamadas (evitar impactos simultáneos).
- Antes de cualquier envío real: comprobar canal, mensaje inicial, seguimiento, parada, receptor del aviso y que la derivación funciona. Hasta entonces, solo simulación sin datos personales reales.
- Registrar por separado: respuesta al WhatsApp, solicitud/aceptación de llamada y llamada realizada. Siempre con denominador y periodo. Comparar ilocalizables y no compradores por separado.

## Cómo trabajar en este repo

- Si una petición choca con las reglas duras o el alcance, avisa antes de implementar.
- Si falta una decisión de las listadas arriba, propón una opción por defecto configurable y márcala `TODO(decisión)`; no la presentes como acordada.
- Mantén simple: registro básico y supervisión básica antes que automatización.
- Idioma de código y commits: seguir convención del repo. Textos de cara al lead y documentación: español de España (tuteo).
- Tests mínimos obligatorios sobre el agente: precio, temario, financiación, respuesta desconocida, petición de humano y petición de parada.
