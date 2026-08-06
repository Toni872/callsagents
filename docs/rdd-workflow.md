# RDD — Receipt-Driven Development Workflow

Este documento explica cómo se usa RDD (Receipt-Driven Development) en este proyecto. RDD garantiza que lo que se commitea es exactamente lo que se revisó.

---

## ¿Qué es RDD?

RDD es un mecanismo del ecosistema **gentle-ai** que:

1. **Congela** un *candidate* (el conjunto de cambios que estás por commitear)
2. Genera un **receipt** (hash inmutable) sobre ese candidate
3. Valida que el commit real = el candidate que se revisó
4. **Detecta modificaciones posteriores** al receipt (cambios no autorizados en archivos ya revisados)

Si alguien (o vos mismo) modifica archivos DESPUÉS del receipt sin un nuevo `review start`, el **delivery gate** falla: no se puede commitear/pushear hasta regenerar el receipt.

---

## Estado actual en este clone

```
$ gentle-ai review mode status --cwd .
receipt-driven development: on (decided by default)
  global:      unset
  clone-local: unset
```

Significa: **RDD activo globalmente**, no se desactivó en este clone.

Para apagarlo (kill switch del usuario):
```bash
gentle-ai review mode disable --cwd .
```

Para reactivarlo:
```bash
gentle-ai review mode enable --scope clone --cwd .
```

---

## Flujo de trabajo por cambio

### 1. Hacer cambios en código

Editá archivos normalmente. NO commitear todavía.

### 2. Generar el review (congelar candidate)

Desde la raíz del repo:

```bash
gentle-ai review start \
  --consent granted \
  --projection workspace \
  --cwd .
```

Flags explicadas:

| Flag | Valor | Por qué |
|---|---|---|
| `--consent granted` | `granted` | Aceptamos el consent prompt sin preguntar (estamos en CLI/no interactivo) |
| `--projection workspace` | `workspace` o `staged` | `workspace` toma los cambios sin staging; `staged` toma solo lo que esté en `git add` |
| `--cwd .` | path al repo | Necesario si no estás en la raíz |

Si NO estás seguro si hay cambios sin stage, usá `git status` para verificar.

### 3. Verificar el receipt generado

```bash
gentle-ai review status --cwd .
```

Te muestra el receipt activo: scope, archivos congelados, y la línea base (commit SHA al que se compara).

### 4. Commitear

Ahora sí, el commit refleja EXACTAMENTE el candidate congelado:

```bash
git add .
git commit -m "feat(scope): descripción conventional"
```

### 5. Push

```bash
git push origin main
```

---

## Ejemplo completo

```bash
# 1. Editás archivos (ej: backend/src/main/java/.../Lead.java)

# 2. Verificás que hay cambios
git status --short
# M  backend/src/main/java/com/callsagents/backend/leads/entity/Lead.java

# 3. Generás review
gentle-ai review start --consent granted --projection workspace --cwd .

# 4. Verificás el receipt
gentle-ai review status --cwd .
# → Output con scope, lineage, frozen info

# 5. Commiteás (lo que esté staged se respeta exactamente)
git add backend/src/main/java/com/callsagents/backend/leads/entity/Lead.java
git commit -m "feat(leads): agregar campo custom_fields en entity"

# 6. Pusheás
git push origin main
```

---

## Helpers rápidos

### ¿Hay un review activo?

```bash
gentle-ai review status --cwd .
```

Si NO hay receipt activo, podés commitear SIN generar uno (pero esto va contra el espíritu de RDD).

### Abandonar un review en curso (empezar de nuevo)

```bash
gentle-ai review abandon --cwd .
```

Útil si te equivocaste en cambios y querés regenerar el receipt.

### Validar el receipt antes de commitear (revisión)

```bash
gentle-ai review validate --cwd .
```

Confirma que el working tree todavía matchea el candidate congelado. Si hubieron cambios desde el `start`, este comando falla.

---

## Errores comunes y soluciones

### "candidate has drifted since review start"

**Causa**: Modificaste archivos después de hacer `review start` sin un nuevo `start`.
**Fix**:
```bash
gentle-ai review abandon --cwd .
gentle-ai review start --consent granted --projection workspace --cwd .
```

### "no review active"

**Causa**: No has hecho `review start` en esta sesión, o ya finalizaste/abandonaste el último.
**Fix**: hacer `gentle-ai review start --consent granted --projection workspace --cwd .`.

### El `start` se queda colgado preguntando consent

**Causa**: Falta `--consent granted` o `--consent declined`.
**Fix**: agregar la flag explícita.

---

## Filosofía: por qué RDD y no solo confianza

| Sin RDD | Con RDD |
|---|---|
| Commiteo lo que recuerdo | Commiteo EXACTAMENTE lo que revisé |
| Modifico un archivo después de "revisar mentalmente" — pasa inadvertido | Modifico un archivo después del receipt — delivery gate falla |
| Múltiples devs editan el mismo archivo entre reviews — caos | Cada cambio intencional genera su propio receipt |
| "Yo no toqué eso" no se puede verificar | El receipt es la prueba |

Para un proyecto que va a producción y maneja datos sensibles (leads, llamadas), **RDD no es paranoia — es disciplina**.

---

## Cuándo NO usar RDD

- Cambios puramente cosméticos en docs (README, typos): podés saltarte el review
- Hotfix urgente que rompe producción: usá RDD rápido, no lo saltes
- Cualquier cambio de código: **siempre** RDD

PERO para docs puras (`.md`, comentarios), también conviene hacer review porque asegura que el cambio es exactamente lo que querías.

---

## Comandos de referencia rápida

```bash
# Estado
gentle-ai review mode status --cwd .
gentle-ai review status --cwd .

# Activar / desactivar kill switch
gentle-ai review mode enable --scope clone --cwd .
gentle-ai review mode disable --cwd .

# Flujo de trabajo
gentle-ai review start --consent granted --projection workspace --cwd .
gentle-ai review validate --cwd .
gentle-ai review abandon --cwd .
```

---

## Próximos pasos

Una vez que RDD está documentado y activo, los próximos commits del proyecto deben seguir este workflow. Si querés automatizar:

```bash
# Wrapper git-style que llama RDD antes de commitear
git() {
    if [[ "$1" == "commit" ]]; then
        gentle-ai review start --consent granted --projection workspace --cwd "$(git rev-parse --show-toplevel)"
    fi
    command git "$@"
}
```

(Opcional, agregar a `~/.bashrc` o equivalente.)
