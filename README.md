# Flashcards

![Java](https://img.shields.io/badge/Java-21-blue)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0-brightgreen)
![React](https://img.shields.io/badge/React-19-61DAFB)
![TypeScript](https://img.shields.io/badge/TypeScript-6.0-blue)
![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)

Es una aplicación que busca optimizar el estudio mediante la repetición espaciada. Permite crear mazos (decks) y
tarjetas (cards) de estudio que el usuario debe repasar en el momento que el algoritmo (SM-2) ha calculado. Este cálculo
sale de qué tan bien el usuario recordó la respuesta de la tarjeta (0 – 5) en la revisión anterior, y notifica por email
si existen tarjetas pendientes de revisar; cuanto mejor sea la respuesta, más lejos en el tiempo deberá ser revisada la 
tarjeta nuevamente.

---

## Cómo usar

1. **Crear cuenta** → registrarse y confirmar el email
2. **Crear un mazo** → sobre un tema que interese estudiar
3. **Agregar tarjetas** → escribir pregunta (frente) y respuesta (dorso)
4. **Estudiar** → el sistema muestra las tarjetas pendientes según el algoritmo SM-2
5. **Calificar** → después de cada repaso, indicar qué tan fácil fue recordar (0-5)
6. **Recibir recordatorios** → emails cuando hay tarjetas vencidas

---

## Características

- **Algoritmo SM-2** → intervalos de repaso que se ajustan según tu desempeño
- **Generación con IA** → crear mazos y tarjetas desde archivos (.txt, .pdf) o descripciones de temas
- **5 proveedores de IA** → OpenAI, Anthropic, Google, Mistral, OpenRouter
- **Sesiones de estudio** → agrupación automática de repasos con métricas de precisión
- **Notificaciones** → recordatorios por email en tu zona horaria
- **Paginación por cursor** → listas eficientes sin importar la cantidad de datos

---

## Stack tecnológico

| Capa      | Tecnologías                                                         |
|-----------|---------------------------------------------------------------------|
| Backend   | Java 21, Spring Boot 4.1.0, PostgreSQL, Flyway                      |
| Frontend  | React 19, TypeScript 6.0, Vite, Tailwind CSS                        |
| Seguridad | JWT (access + refresh token), Rate Limiting (Bucket4j), AES-256-GCM |
| Email     | Maileroo REST API, Thymeleaf                                        |
| IA        | OpenAI, Anthropic, Google, Mistral, OpenRouter (REST)               |
| Testing   | JUnit 5, TestContainers, MockMvc, AssertJ                           |
| Deploy    | Render, Docker                                                      |

---

## Requisitos para el desarrollo

- Java 21
- Node.js 20+
- Docker (para PostgreSQL)
- Cuenta en [Maileroo](https://www.maileroo.com/) (para notificaciones por email)

---

## Inicio rápido

### Backend (API)

```bash
# 1. Configurar variables de entorno
cp .env.example .env
# Editar .env con tus valores

# 2. Levantar PostgreSQL
docker-compose up -d

# 3. Ejecutar la API
cd api
./mvnw spring-boot:run
```

La API estará disponible en `http://localhost:8080`

### Frontend

```bash
cd front
npm install
npm run dev
```

El frontend estará disponible en `http://localhost:5173`

---

## Documentación

- **API Docs**: [Swagger UI](http://localhost:8080/swagger-ui.html) (solo en perfil dev)
- **Diseño completo**: `doc/api/SDD.md`
- **Variables de entorno**: `.env.example`

---

## Licencia

MIT License - ver [LICENSE](LICENSE)
