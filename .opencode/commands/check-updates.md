---
description: Analiza compatibilidad de versiones de front y back con las últimas versiones estables. NO ejecuta actualizaciones — solo reporta.
---

# /check-updates

Analiza todas las dependencias de `front/` y `api/`, busca las últimas versiones estables disponibles, verifica breaking
changes, analiza seguridad y produce un reporte de impacto por módulo.

**Uso:**

- `/check-updates` — análisis completo de ambos módulos
- `/check-updates --front-only` — analiza solo el frontend
- `/check-updates --back-only` — analiza solo el backend
- `/check-updates --security-only` — solo análisis de seguridad de ambos módulos
- `/check-updates --front-only --security-only` — seguridad solo del frontend
- `/check-updates --back-only --security-only` — seguridad solo del backend

**No ejecuta ninguna actualización.** Solo observa y reporta.

---

## FASE 0: ALCANCE

1. Parsea `$ARGUMENTS` para determinar qué módulos analizar:
    - Sin flags → analiza `front/` y `api/`.
    - `--front-only` → solo `front/`.
    - `--back-only` → solo `api/`.
    - `--security-only` → omite inventario y breaking changes; solo reporta seguridad.
2. Verifica que el directorio del módulo exista. Si falta, reporta error de inventario para ese módulo y continúa con el
   otro.
3. La fuente de verdad para versiones y configuración es el código (`package.json`, `package-lock.json`, `pom.xml`,
   configuración Maven, `.nvmrc`). La documentación se usa solo como contexto complementario.

---

## FASE 1: INVENTARIO DEL FRONTEND

Si el alcance incluye `front/`:

### 1.1 Configuración base

Lee y registra:

- `front/package.json` → dependencias y `devDependencies` con versión declarada.
- `front/package-lock.json` → versión bloqueada real de cada paquete.
- `front/.nvmrc` o `engines` en `package.json` → versión de Node requerida.
- `front/tsconfig.json`, `front/tsconfig.app.json`, `front/tsconfig.node.json` → configuración TypeScript.
- `front/vite.config.ts` → integraciones de Vite, plugins, alias, optimizaciones.
- `front/.oxlintrc.json` → configuración de linter si existe.
- `front/src/**/*.ts`, `front/src/**/*.tsx`, `front/src/**/*.js` → imports reales.

### 1.2 Dependencias principales a inventariar

Registra versión declarada y bloqueada para:

- `react` y `react-dom`
- `vite` y `@vitejs/plugin-react`
- `typescript`
- `tailwindcss` y `@tailwindcss/vite`
- `react-router-dom`
- `@tanstack/react-query`
- `axios`
- `zod`
- `i18next`, `react-i18next`, `i18next-browser-languagedetector`
- `recharts`
- `sonner`, `clsx`, `tailwind-merge`
- `@types/*`
- Cualquier otro paquete con dependencias significativas

### 1.3 Scripts disponibles

Reporta qué scripts existen y cuáles faltan:

- `lint` → puede no existir
- `typecheck` → puede no existir
- `test` → puede no existir
- `build`, `dev`, `preview`

---

## FASE 2: INVENTARIO DEL BACKEND

Si el alcance incluye `api/`:

### 2.1 Configuración base

Lee y registra:

- `api/pom.xml` → dependencias con versión explícita, parent, properties.
- `api/.mvn/` → Maven wrapper y configuración.
- `api/src/main/resources/application.yaml` → configuración Spring Boot.
- `api/src/main/resources/application-dev.yaml` y `application-prod.yaml` → perfiles.
- `api/src/main/resources/db/migration/` → migraciones Flyway.

### 2.2 Dependencias principales a inventariar

Registra versión declarada y efectiva para:

- **Java** → versión requerida
- **Spring Boot** → versión del parent/BOM
- **Spring Framework** → gestión por Spring Boot
- **Spring Security** → gestión por Spring Boot
- **Hibernate / Spring Data JPA** → gestión por Spring Boot
- **Flyway** → versión explícita o gestionada
- **PostgreSQL JDBC driver** → versión gestionada
- **JJWT** (`io.jsonwebtoken`) → versión explícita
- **Apache PDFBox** → versión explícita
- **Bucket4j** → versión explícita
- **Springdoc OpenAPI** → versión explícita
- **Maileroo SDK** → versión explícita
- **Spring Retry** → versión explícita
- **Micrometer** → gestión por Spring Boot
- **Testcontainers** → gestión por Spring Boot
- **Awaitility** → gestión por Spring Boot
- **JUnit / Mockito / AssertJ** → vía `spring-boot-starter-test`

### 2.3 Plugins Maven

Registra versión de cada plugin declarado en el `pom.xml`:

- `maven-compiler-plugin`
- `spring-boot-maven-plugin`
- Cualquier otro plugin declarado

### 2.4 Árbol de dependencias

Ejecuta desde `api/`:

```powershell
.\mvnw.cmd dependency:tree
.\mvnw.cmd dependency:tree -Dscope=runtime
.\mvnw.cmd dependency:tree -Dscope=test
```

Para cada dependencia transitiva relevante, registra:

- groupId:artifactId
- versión efectiva resuelta
- scope
- si viene gestionada por el BOM de Spring Boot

### 2.5 Effective POM

Ejecuta desde `api/`:

```powershell
.\mvnw.cmd help:effective-pom
```

Identifica:

- Propiedades heredadas del parent
- Versiones resueltas de dependencias gestionadas
- Plugins heredados

---

## FASE 3: SEGURIDAD

### Frontend

Ejecuta desde `front/`:

```powershell
npm audit --json 2>&1
```

Parsea el output:

- Cuenta vulnerabilidades por severidad: `critical`, `high`, `moderate`, `low`
- **critical** o **high** → bandera roja, reporta como hallazgo prioritario
- **moderate** o **low** → reporta como nota informativa
- Sin vulnerabilidades → reporta como "limpio"
- Errores de red o JSON inválido → reporta como "no determinable"

### Backend

1. Revisa las versiones inventariadas en la Fase 2 contra fuentes oficiales:
    - GitHub Advisories (https://github.com/advisories)
    - NVD (https://nvd.nist.gov/)
    - Advisory del proveedor del paquete
2. Distingue vulnerabilidades directas de transitivas.
3. Para cada hallazgo registra: paquete, versión afectada, severidad, CVE si está disponible.
4. No ejecutes plugins OWASP ni crees reportes en disco a menos que ya estén configurados y el usuario lo solicite
   explícitamente.

### Compatibilidad de runtime

Para cada dependencia con actualización de **major** o **minor** significativo:

- Frontend: busca `<paquete> minimum node version` y compara con `.nvmrc` / `engines`.
- Backend: busca `<paquete> minimum java version` o `<paquete> minimum spring boot version` y compara con lo declarado
  en `pom.xml`.
- Si hay conflicto → reporta como hallazgo de compatibilidad.

**Restricción**: NO ejecutes `npm audit fix`, `npm install`, `npm update`, ni ningún comando que modifique el proyecto.
Solo lees y analizas.

---

## FASE 4: DESCUBRIMIENTO DE VERSIONES

### 4.1 Frontend

Para cada dependencia de `front/package.json`:

1. Usa `websearch` para buscar: `"<paquete>" latest stable version <año-actual>`
2. Usa `context7` o documentación oficial del paquete para confirmar la última versión estable.
3. Registra: versión declarada → versión bloqueada → última estable disponible.
4. Clasifica la diferencia:
    - **Patch** (x.y.Z): raramente tiene breaking changes
    - **Minor** (x.Y.0): puede tener features nuevos
    - **Major** (X.0.0): casi siempre tiene breaking changes

### 4.2 Backend

Para cada dependencia directa de `api/pom.xml`:

1. Usa `websearch` para buscar: `"<paquete>" latest stable version <año-actual>`
2. Usa `context7` o documentación oficial del paquete/proveedor para confirmar.
3. Registra: versión declarada → versión efectiva (si difiere de la declarada) → última estable disponible.
4. Indica la fuente de la versión actual:
    - `pom.xml` explícita
    - Heredada del parent Spring Boot
    - Heredada de properties
    - Resuelta transitivamente
5. Clasifica la diferencia igual que el frontend.

**Restricción**: este comando es un análisis de consultoría. Usa fuentes oficiales y herramientas de consulta, pero no
modifiques archivos del proyecto.

---

## FASE 5: ANÁLISIS DE COMPATIBILIDAD Y BREAKING CHANGES

### 5.1 Frontend

Para cada dependencia con actualización de **minor** o **major**:

1. Busca changelog o release notes en GitHub/npm:
   `websearch: "<paquete> <versión-actual> to <versión-última> migration guide breaking changes"`
2. Verifica si los breaking changes impactan APIs que el proyecto usa realmente.
3. Revisa compatibilidad entre dependencias acopladas:
    - React ↔ React DOM (deben ser la misma versión)
    - Vite ↔ @vitejs/plugin-react
    - Vite ↔ TypeScript
    - Vite ↔ Tailwind
    - React Router y APIs usadas
4. Para cada breaking change, usa `grep` o `glob` para buscar en `front/src` si el proyecto usa la API afectada.
5. Clasifica impacto:
    - **Afectado directamente** — el proyecto usa la API que cambia
    - **Afectado potencialmente** — el proyecto podría estar en una ruta que cambia
    - **No afectado** — el cambio no aplica

### 5.2 Backend

Para cada dependencia directa con actualización de **minor** o **major**:

1. Busca changelog o release notes oficiales.
2. Verifica si los breaking changes impactan configuración, APIs o patrones que el proyecto usa.
3. Analiza especialmente:
    - Spring Boot ↔ Spring Framework
    - Spring Boot ↔ Spring Security
    - Spring Boot ↔ Hibernate/JPA
    - Spring Boot ↔ Flyway
    - Java y requisitos de nuevas versiones
    - Springdoc y compatibilidad con Spring Boot
    - JJWT y APIs JWT
    - PDFBox y procesamiento de archivos
    - Bucket4j y filtros de rate limiting
    - Maileroo SDK y configuración de correo
    - Testcontainers y PostgreSQL
    - Actuator, métricas y seguridad
4. Para cada breaking change, usa `grep` o `glob` para buscar en `api/src` si el proyecto usa la API, configuración o
   anotación afectada.
5. Clasifica impacto igual que el frontend.

### 5.3 Cada breaking change reportado debe incluir:

```
#### [paquete] X.Y.Z → A.B.C

**Breaking change:** [descripción concisa]

**Impacto en este proyecto:**
- [Afectado/No afectado] — [razón específica]
- Archivos afectados: [lista si aplica]

**Acción requerida:** [nada / actualizar código / configuración / dependencias]

**Riesgo de no actualizar:** [descripción]
```

---

## FASE 6: DEPENDENCIAS HUÉRFANAS

### Frontend

1. Usa `grep` o `glob` para extraer todos los imports de `front/src/**/*.ts`, `front/src/**/*.tsx`, `front/src/**/*.js`.
2. Registra los nombres de paquete raíz de cada import.
3. Cruza la lista con las `dependencies` de `front/package.json`.
4. Excepciones (no se consideran huérfanas):
    - Dependencias de build/dev que Vite carga automáticamente desde configuración.
    - Plugins configurados en `vite.config.ts` o `tsconfig.json`.
    - Paquetes usados indirectamente por Tailwind u otras dependencias.
5. Para cada huérfana: indica que no se encontró uso y sugiere verificar si se puede eliminar.

### Backend

1. Cruza las dependencias de `api/pom.xml` con:
    - Imports Java en `api/src/main/java` y `api/src/test/java`
    - Anotaciones usadas
    - Plugins configurados en el `pom.xml`
    - Recursos y configuración
    - Migraciones Flyway
2. Para cada huérfana: indica que no se encontró uso, explica posibles falsos positivos (dependencias de runtime,
   anotaciones, beans auto-configurados).

**Restricción**: esta fase es de observación únicamente. No modifiques archivos ni ejecutes desinstalaciones.

---

## FASE 7: FEATURES NUEVAS DISPONIBLES

Para cada módulo, identifica features o mejoras relevantes que la última versión estable ofrece y que el proyecto podría
aprovechar:

- Feature: [descripción]
- Relevancia: [alta/media/baja] para este proyecto
- Esfuerzo de adopción: [nulo/bajo/medio/alto]

Prioriza: mejoras de rendimiento, nuevas APIs, mejoras de tipado, soporte de seguridad, DX improvements.

---

## FASE 8: REPORTE

Produce un reporte con este formato:

```markdown
# Check de actualizaciones — Flashcards Monorepo

## Alcance analizado

| Módulo | Incluido |
|--------|----------|
| front/ | sí/no |
| api/   | sí/no |
| Seguridad | sí/no |

## Resumen ejecutivo

[Resumen de 3-5 líneas con los hallazgos más importantes]

---

## Frontend (front/)

### Inventario de versiones

| Paquete | Actual declarada | Bloqueada | Última estable | Diferencia | Riesgo | Fuente |
|---------|-----------------|-----------|----------------|------------|--------|--------|
| react | 19.x.x | 19.x.x | x.x.x | minor | bajo | package.json + lock |

### Estado de seguridad

```

Vulnerabilidades: [limpio / N moderate / N high / N critical]
Node.js compat: [compatible / conflicto detectado / no determinable]

```

Si hay vulnerabilidades, enuméralas:
- [paquete]@[versión]: [severidad] — [descripción breve del CVE]
  Acción: [esperar fix / actualizar paquete / sin acción inmediata]

### Breaking Changes Detectados

[Repetir por cada paquete con breaking changes]

### Impacto en el código

[Archivos y APIs afectadas por cada breaking change]

### Dependencias huérfanas

- [paquete] — no se detectó uso en front/src/. Candidata a eliminación.
- [paquete] — herramienta de build/Vite, carga automática. No requiere acción.

Si no hay huérfanas: "No se detectaron dependencias huérfanas."

### Features nuevas disponibles

- Feature: [descripción]
- Relevancia: [alta/media/baja]
- Esfuerzo: [nulo/bajo/medio/alto]

### Recomendaciones

- **Actualizar ahora** — [paquete]: sin riesgo, beneficios claros
- **Esperar** — [paquete]: esperar patch de fix
- **Revisar manualmente** — [paquete]: hay breaking changes que requieren evaluación
- **No actualizar** — [paquete]: versión actual suficiente

---

## Backend (api/)

### Inventario de versiones

| Paquete | Actual declarada | Efectiva | Última estable | Diferencia | Riesgo | Fuente |
|---------|-----------------|----------|----------------|------------|--------|--------|
| Spring Boot | 4.1.1 | 4.1.1 | x.x.x | minor | bajo | pom.xml parent |
| JJWT | 0.13.0 | 0.13.0 | x.x.x | minor | bajo | pom.xml explícita |

### Estado de seguridad

```

Vulnerabilidades: [limpio / N moderate / N high / N critical]
Java compat: [compatible / conflicto detectado / no determinable]
Spring Boot compat: [compatible / conflicto detectado]

```

Si hay vulnerabilidades, enuméralas igual que en el frontend.

### Breaking Changes Detectados

[Repetir por cada paquete con breaking changes]

### Impacto en el código

[Archivos, configuraciones, anotaciones y migraciones afectadas]

### Dependencias huérfanas

- [paquete] — no se detectó uso en api/src/. Candidata a verificación.
- [paquete] — anotación o bean auto-configurado, no requiere import directo. No es huérfana.

### Features nuevas disponibles

- Feature: [descripción]
- Relevancia: [alta/media/baja]
- Esfuerzo: [nulo/bajo/medio/alto]

### Recomendaciones

Igual formato que el frontend.

---

## Contradicciones detectadas

[Lista de inconsistencias entre documentación y código, si las hay]

## Priorización final

Lista ordenada por prioridad de todas las acciones sugeridas:

1. [Acción] — [módulo] — [paquete] — [urgencia]
```

---

## RESTRICCIONES

1. **NUNCA** ejecutes `npm install`, `npm update`, `npm audit fix`, `npm uninstall`, ni ningún comando que instale,
   actualice o desinstale paquetes.
2. **NUNCA** ejecutes `mvn clean`, `mvn package`, `mvn install`, `mvn deploy`, `mvn versions:update-properties`,
   `mvn versions:use-latest-versions`, ni ningún comando que modifique archivos o genere artefactos.
3. **NUNCA** modifiques archivos del proyecto.
4. Si no puedes determinar la última versión estable de un paquete, indícalo como "no determinable" en el reporte y
   continúa.
5. Si un paquete tiene más de 10 dependencias transitivas relevantes, menciona solo las más impactantes.
6. El reporte debe ser comprensible para un desarrollador no experto — explica conceptos técnicos cuando sea necesario.
7. Si el usuario pide profundizar en algún punto específico, usa las herramientas disponibles (websearch, MCP, grep)
   para obtener más detalles.
8. En la fase de seguridad, **nunca** ejecutes `npm audit fix` — solo reporta.
9. En la fase de limpieza, **nunca** ejecutes `npm uninstall` — solo reporta.
10. Distingue siempre entre versión declarada, versión efectiva/bloqueada y última estable.
11. La fuente de verdad es el código, no la documentación. La documentación se usa solo como contexto.
12. Ejecuta comandos de npm desde `front/` y comandos de Maven desde `api/`.
