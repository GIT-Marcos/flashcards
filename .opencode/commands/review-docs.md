---
description: Revisa toda la documentación del proyecto y detecta desactualizaciones o inconsistencias
---

# /review-docs

Revisa la documentación del proyecto y genera un reporte de inconsistencias, desactualizaciones y issues de seguridad.

## Flujo

Eres un revisor de documentación técnica. Tu tarea es:

### 1. Lee TODOS estos archivos de documentación

- README.md
- AGENTS.md
- CONTRIBUTING.md
- SECURITY.md
- .env.example
- .gitignore (raíz del proyecto)
- .opencode/.gitignore (reglas de exclusión de opencode)
- doc/SDD.md
- doc/csrf-security-decision.md
- Todos los archivos dentro de doc/ (se lee todo el directorio)
- pom.xml (para metadatos del proyecto)
- .opencode/opencode.jsonc (configuración de OpenCode)
- .opencode/opencode.example.jsonc (plantilla de configuración de OpenCode)
- .github/PULL_REQUEST_TEMPLATE.md

### 2. Lee los archivos fuente principales para verificar claims vs realidad

- security/SecurityConfig.java
- controller/ (todos los endpoints)
- application.yaml y los profile YAMLs
- Dockerfile y docker-compose.yml

### 3. Para cada archivo de documentación, verifica

- ¿Los endpoints documentados coinciden con la implementación real? (rutas, métodos HTTP, DTOs)
- ¿Las descripciones técnicas son precisas? (algoritmos, perfiles, dependencias)
- ¿Hay información obsoleta (versiones, fechas, screenshots)?
- ¿Hay claims no verificables (ej: "370+ tests")?
- ¿Faltan secciones importantes? (CHANGELOG, entrypoint.sh)
- ¿Hay referencias rotas a archivos que ya no existen?

> **IMPORTANTE sobre seguridad:** Si detectas variables como API keys, secretos o credenciales
> (ej: MAILEROO_API_KEY, JWT_SECRET_KEY, DB_PASSWORD) en algún archivo, primero lee los
> archivos .gitignore (.gitignore raíz y .opencode/.gitignore) y verifica si el archivo donde
> aparece está cubierto por alguna regla de exclusión. Si lo está, NO lo reportes como issue
> de seguridad crítico — menciónalo solo como nota informativa de prioridad baja. También
> distingue entre valores reales y placeholders obvios (CHANGE_ME, your-..., <YOUR_...>).

### 4. Genera un reporte con

- Para cada archivo: lista de issues encontrados
- Prioridad de cada issue (alta/media/baja)
- Acción correctiva sugerida para cada uno
- Resumen ejecutivo al inicio

Devuelve solo el reporte, sin comentarios adicionales.
