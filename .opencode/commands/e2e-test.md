---
description: Ejecuta pruebas E2E completas -> levanta Docker + API, prueba endpoints con curls, y genera informe
---

# /e2e-test

Ejecuta un suite completo de pruebas End-to-End contra la API usando Docker Compose (PostgreSQL real) y curl.

**Uso:**
- `/e2e-test` — ejecuta el flujo completo
- `/e2e-test --skip-build` — salta la compilación del JAR (si ya existe)

## Prerrequisitos

Antes de ejecutar, verifica que:
1. Docker Desktop está corriendo (`docker info`)
2. El archivo `.env` existe en la raíz del proyecto
3. El puerto 8080 está libre (la API lo necesita)
4. El puerto 5433 está libre (PostgreSQL lo necesita)

Para verificar puertos libres en PowerShell:
```powershell
$port8080 = Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
$port5433 = Get-NetTCPConnection -LocalPort 5433 -ErrorAction SilentlyContinue
if ($port8080) { Write-Host "ERROR: Puerto 8080 ya está en uso" }
if ($port5433) { Write-Host "ERROR: Puerto 5433 ya está en uso" }
if ($port8080 -or $port5433) { exit 1 }
```

Si falta alguno, informa al usuario y detente.

## Flujo

### Fase 1: Levantar Docker Compose

1. Ejecuta desde `api/`:
   ```powershell
   Set-Location C:\Users\usuario\IdeaProjects\cards\api
   docker-compose up -d --env-file ../.env
   if ($LASTEXITCODE -ne 0) {
       Write-Host "ERROR: Docker Compose falló al iniciar PostgreSQL"
       Write-Host "Posibles causas: puerto 5433 en uso, imagen postgres:16-alpine no disponible"
       exit 1
   }
   ```

2. Espera a que PostgreSQL esté listo (polling cada 2 segundos, máximo 30 segundos):
   ```powershell
   docker exec flashcards_db pg_isready -U postgres
   ```

3. Si PostgreSQL no responde en 30 segundos, aborta todo el flujo y muestra error al desarrollador.

### Fase 2: Compilar y Arrancar la API

1. Si `--skip-build` NO está presente, compila:
   ```powershell
   cd api
   .\mvnw clean package -DskipTests
   ```

2. Carga todas las variables del .env en la sesión de PowerShell:
   ```powershell
   Get-Content ../.env | ForEach-Object {
       $line = $_.Trim()
       if ($line -and -not $line.StartsWith('#') -and $line -match '^([^#=]+)=(.*)$') {
           $key = $matches[1].Trim()
           $value = $matches[2].Trim()
           [Environment]::SetEnvironmentVariable($key, $value, "Process")
       }
   }
   ```

3. Ejecuta la API en background, capturando el PID para limpieza posterior:
   ```powershell
   $jar = (Get-ChildItem target\flashcards-*.jar).FullName
   $apiProcess = Start-Process -FilePath "java" -ArgumentList "-jar", $jar -NoNewWindow -PassThru
   $apiPid = $apiProcess.Id
   ```

4. Espera a que la API esté lista (polling cada 3 segundos, máximo 120 segundos):
   ```powershell
   $maxRetries = 40
   $retryCount = 0
   $ready = $false

   while (-not $ready -and $retryCount -lt $maxRetries) {
       $healthStatus = curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/actuator/health
       if ($healthStatus -eq "200") {
           $ready = $true
       } else {
           Start-Sleep -Seconds 3
           $retryCount++
       }
   }

   if (-not $ready) {
       Write-Host "ERROR: API no respondió en 120 segundos"
       Stop-Process -Id $apiPid -Force -ErrorAction SilentlyContinue
       Set-Location api; docker-compose down -v --env-file ../.env
       exit 1
   }
   ```

5. Si la API no responde en 120 segundos, muestra error, limpia Docker, y aborta.

### Fase 3: Ejecutar Tests

**Captura de token:** Después del Test 3 (Login), extrae el `accessToken` del JSON de respuesta y guárdalo en una variable `$TOKEN` para usarlo en todos los tests siguientes. Ejemplo:
```powershell
$loginBody = Get-Content response.json | ConvertFrom-Json
$TOKEN = $loginBody.accessToken
```
Usa `$TOKEN` en el header `Authorization: Bearer $TOKEN` de cada test.

#### Test 1: Health Check
```powershell
curl -s -o response.json -w "%{http_code}" http://localhost:8080/actuator/health
```
- **Esperado:** 200, body contiene `"status":"UP"`

#### Test 2: Signup
```powershell
curl -s -o response.json -w "%{http_code}" -X POST http://localhost:8080/auth/signup `
  -H "Content-Type: application/json" `
  -d '{"username":"e2e_test_user","password":"Test1234!","timeZone":"America/Buenos_Aires"}'
```
- **Esperado:** 202
- **Alternativa:** Si retorna 409 (usuario ya existe), no es error — continuar con login.

#### Test 3: Login
```powershell
curl -s -o response.json -w "%{http_code}" -X POST http://localhost:8080/auth/login `
  -H "Content-Type: application/json" `
  -d '{"username":"e2e_test_user","password":"Test1234!"}'
```
- **Esperado:** 200
- **Captura:** Guardar `accessToken` del body para usar en tests siguientes.
- **Rate limit:** Si retorna 429, esperar 10 segundos y reintentar (máx 3 veces).

#### Test 4: Get Profile
```powershell
curl -s -o response.json -w "%{http_code}" http://localhost:8080/users/me `
  -H "Authorization: Bearer $TOKEN"
```
- **Esperado:** 200, body contiene `username` y `email`

#### Test 5: Create Deck
```powershell
curl -s -o response.json -w "%{http_code}" -X POST http://localhost:8080/decks `
  -H "Authorization: Bearer $TOKEN" `
  -H "Content-Type: application/json" `
  -d '{"name":"E2E Test Deck"}'
```
- **Esperado:** 201
- **Captura:** Guardar `deckId` del body.

#### Test 6: List Decks
```powershell
curl -s -o response.json -w "%{http_code}" http://localhost:8080/decks `
  -H "Authorization: Bearer $TOKEN"
```
- **Esperado:** 200, body es array con al menos 1 deck

#### Test 7: Create Card
```powershell
curl -s -o response.json -w "%{http_code}" -X POST http://localhost:8080/cards/deck/$DECK_ID `
  -H "Authorization: Bearer $TOKEN" `
  -H "Content-Type: application/json" `
  -d '{"front":"E2E test front","back":"E2E test back"}'
```
- **Esperado:** 201
- **Captura:** Guardar `cardId` del body.

#### Test 8: List Cards
```powershell
curl -s -o response.json -w "%{http_code}" http://localhost:8080/cards/deck/$DECK_ID `
  -H "Authorization: Bearer $TOKEN"
```
- **Esperado:** 200, body es array con al menos 1 card

#### Test 9: Review Card
```powershell
curl -s -o response.json -w "%{http_code}" -X POST http://localhost:8080/reviews/card/$CARD_ID `
  -H "Authorization: Bearer $TOKEN" `
  -H "Content-Type: application/json" `
  -d '{"quality":4}'
```
- **Esperado:** 200, body contiene `nextReviewDate` (SM-2 actualizado)
- **Alternativa:** Si retorna 409 (optimistic locking), reintentar 1 vez.

#### Test 10: Get Stats
```powershell
curl -s -o response.json -w "%{http_code}" http://localhost:8080/sessions/stats `
  -H "Authorization: Bearer $TOKEN"
```
- **Esperado:** 200, body contiene `totalReviews` y `accuracyRate`

#### Test 11: Delete Card
```powershell
curl -s -o response.json -w "%{http_code}" -X DELETE http://localhost:8080/cards/$CARD_ID `
  -H "Authorization: Bearer $TOKEN"
```
- **Esperado:** 204

#### Test 12: Delete Deck
```powershell
curl -s -o response.json -w "%{http_code}" -X DELETE http://localhost:8080/decks/$DECK_ID `
  -H "Authorization: Bearer $TOKEN"
```
- **Esperado:** 204

#### Test 13: Logout
```powershell
curl -s -o response.json -w "%{http_code}" -X POST http://localhost:8080/auth/logout `
  -H "Authorization: Bearer $TOKEN"
```
- **Esperado:** 204

### Fase 4: Limpiar

**Siempre ejecutar esta fase**, incluso si hubo errores en tests anteriores.

1. Matar solo el proceso de la API (usando el PID capturado en Fase 2):
   ```powershell
   Stop-Process -Id $apiPid -Force -ErrorAction SilentlyContinue
   ```

2. Bajar Docker Compose con volumen:
   ```powershell
   Set-Location C:\Users\usuario\IdeaProjects\cards\api
   docker-compose down -v --env-file ../.env
   ```

3. Verificar que el contenedor se eliminó:
   ```powershell
   docker ps | Select-String "flashcards_db"
   ```

4. Si sigue apareciendo, forzar:
   ```powershell
   docker rm -f flashcards_db
   ```

### Fase 5: Generar Informe

Genera un reporte formateado con los resultados de cada test:

```markdown
# E2E Test Report — Flashcards API

**Fecha:** [fecha y hora]
**Entorno:** Docker Compose (PostgreSQL 16) + Spring Boot dev profile

## Infrastructure

| Component | Status | Details |
|-----------|--------|---------|
| Docker | [✅/❌] | [detalles] |
| PostgreSQL | [✅/❌] | Puerto 5433 |
| API | [✅/❌] | Puerto 8080, profile dev |

## Test Results

| # | Test | Method | Endpoint | Status | Response | Time |
|---|------|--------|----------|--------|----------|------|
| 1 | Health Check | GET | /actuator/health | [✅/❌] | [resultado] | [tiempo] |
| 2 | Signup | POST | /auth/signup | [✅/⚠️/❌] | [resultado] | [tiempo] |
| 3 | Login | POST | /auth/login | [✅/❌] | [resultado] | [tiempo] |
| 4 | Get Profile | GET | /users/me | [✅/❌] | [resultado] | [tiempo] |
| 5 | Create Deck | POST | /decks | [✅/❌] | [resultado] | [tiempo] |
| 6 | List Decks | GET | /decks | [✅/❌] | [resultado] | [tiempo] |
| 7 | Create Card | POST | /cards/deck/{id} | [✅/❌] | [resultado] | [tiempo] |
| 8 | List Cards | GET | /cards/deck/{id} | [✅/❌] | [resultado] | [tiempo] |
| 9 | Review Card | POST | /reviews/card/{id} | [✅/❌] | [resultado] | [tiempo] |
| 10 | Get Stats | GET | /sessions/stats | [✅/❌] | [resultado] | [tiempo] |
| 11 | Delete Card | DELETE | /cards/{id} | [✅/❌] | [resultado] | [tiempo] |
| 12 | Delete Deck | DELETE | /decks/{id} | [✅/❌] | [resultado] | [tiempo] |
| 13 | Logout | POST | /auth/logout | [✅/❌] | [resultado] | [tiempo] |

## Summary

- **Total:** 13 tests
- **Passed:** [n] ([%])
- **Warnings:** [n] ([descripción])
- **Failed:** [n] ([descripción])
- **Total Time:** [tiempo]

## Observations

- [Observaciones relevantes]
```

## Notas Importantes

- **Rate limiting:** Login tiene límite de 5 requests. Si el script falla y reintenta, puede bloquearse. Respeta los retries.
- **Tokens JWT:** Expiran en 15 minutos. El suite completo debería durar menos de 1 minuto.
- **Datos de prueba:** V6 crea `test_user` con 7 decks y ~83 cards. Si signup retorna 409, es comportamiento esperado.
- **Cleanup:** Siempre limpiar al final, incluso si el usuario cancela (Ctrl+C).
