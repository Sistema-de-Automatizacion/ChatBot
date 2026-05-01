# Motos del Caribe — ChatBot

Servicio Spring Boot que va a contestar **automáticamente** los mensajes de WhatsApp de los clientes de Motos del Caribe Renting S.A.S. cuando pregunten cosas como _"¿cuánto debo?"_, _"¿cuándo es mi próxima cuota?"_ o _"¿tengo mora?"_. Hoy esas consultas las atiende una persona; con el bot el cliente recibe respuesta al instante, las 24 horas.

> **Estado:** lógica completa, pendiente de la aprobación del Business Manager de Meta para WhatsApp Business y de la conexión final con n8n. El bot ya se puede ejercitar end-to-end vía Postman/curl simulando a n8n (ver sección 5).

---

## 1. ¿Qué hace este servicio?

1. Llega un mensaje de WhatsApp de un cliente.
2. El bot identifica al cliente por su número de teléfono (matchea contra `TELULT` en la BD de cartera).
3. Si no es un número autorizado responde _"No tienes acceso desde este número"_ y termina.
4. Si es un cliente conocido lo saluda por su nombre y le pide la placa de su moto.
5. Con la placa confirmada, le ofrece un menú de 3 opciones (saldo, próxima cuota, mora).
6. El cliente elige y el bot devuelve la información concreta. Fin de la conversación.

El bot recuerda en qué punto va cada conversación durante 30 minutos; si el cliente abandona y vuelve después, empieza desde cero.

---

## 2. Arquitectura: cómo se relaciona con los otros 2 repos

El sistema completo son **3 repos separados** bajo la organización [Sistema-de-Automatizacion](https://github.com/Sistema-de-Automatizacion):

| Repo | Rol | Dirección del flujo |
|---|---|---|
| [Backend](https://github.com/Sistema-de-Automatizacion/Backend) | **Outbound**: empuja avisos de cobro a clientes morosos / próximos a vencer | El sistema avisa al cliente |
| [Fronted](https://github.com/Sistema-de-Automatizacion/Fronted) | **Visualización**: panel operativo del equipo interno (KPIs, listados, historial) | Solo lectura para el equipo |
| **ChatBot** (este) | **Inbound**: responde cuando el cliente escribe primero | El cliente le pregunta al sistema |

Los tres comparten la misma BD MySQL en Azure pero con responsabilidades opuestas. **n8n** es el orquestador que conecta los servicios con la API de Meta WhatsApp Cloud, así un cambio en Meta (token, firma) se resuelve en un solo lugar.

```
┌─────────────────────────────────────────────────────────┐
│  Cliente final (WhatsApp)                                │
└────────────┬─────────────────────────────────▲──────────┘
             │ entrante                         │ saliente
             ▼                                  │
┌─────────────────────────────────────────────────────────┐
│  Meta WhatsApp Cloud API                                 │
└────────────┬─────────────────────────────────▲──────────┘
             ▼                                  │
┌─────────────────────────────────────────────────────────┐
│  n8n  (tokens, firma X-Hub-Signature, ruteo)            │
└────┬───────────────────────────────────▲────────────────┘
     │ POST /api/conversation             │ GET /contracts/...
     ▼ (próximo PR)                       │
┌──────────────────────┐         ┌───────────────────────┐
│  ChatBot (este repo) │         │  Backend de cartera   │
│  Spring Boot · Java  │         │  Spring Boot · Java   │
└─────────┬────────────┘         └────────────┬──────────┘
          │                                   │
          └─────────────┬─────────────────────┘
                        ▼
          ┌───────────────────────────────┐
          │  MySQL · Azure                │
          │                               │
          │  vw_sv_all_motos_semanal      │  ← lectura compartida
          │  chat_session  (este repo)    │  ← lectura/escritura propia
          └───────────────┬───────────────┘
                          │ feed (vía Backend de cartera)
                          ▼
          ┌───────────────────────────────┐
          │  Fronted  (dashboard interno) │
          └───────────────────────────────┘
```

### Quién toca qué en la BD

- **`vw_sv_all_motos_semanal`** (vista existente, read-only): trae datos del cliente (nombre, placa, cuota, deuda, estado, teléfono). La leen **el Backend de cartera y el ChatBot**.
- **`chat_session`** (tabla nueva, read/write): memoria conversacional del bot. **Solo el ChatBot la usa.**

---

## 3. Stack

| Componente | Versión |
|---|---|
| Java | 21 |
| Spring Boot | 4.0.6 |
| Build | Maven (con `mvnw` wrapper) |
| Persistencia | Spring Data JPA + Hibernate 7 |
| BD | MySQL 8 (Azure Database for MySQL Flexible Server) |
| Boilerplate | Lombok |
| Documentación API | SpringDoc OpenAPI / Swagger UI |
| Tests | JUnit 5 + Spring Boot Test (suite todavía vacía) |

Mismo stack que el Backend de cartera, intencionalmente: una persona puede saltar entre los dos repos sin reaprender convenciones.

---

## 4. Estado actual (✅ hecho · ⏳ pendiente)

### ✅ Fase 1 — Bootstrap de MySQL · `feat/db-bootstrap` (mergeada)

El scaffold inicial **no compilaba** y no se conectaba a la BD. Ahora:

- Compila limpio (`./mvnw clean compile` → BUILD SUCCESS).
- Arranca conectándose a Azure MySQL.
- Las credenciales viven en `.env` (no se suben a git, mismo patrón que el Backend).
- Puerto por defecto `8081` para no chocar con el Backend de cartera (`8080`) en local; en Render/Azure la plataforma sobrescribe vía `PORT`.
- `actuator/health` expuesto.

### ✅ Fase 1.5 — Mapeo de `Client` a la vista de cartera · `feat/map-client-to-view` (mergeada)

`Client` es un proyección read-only sobre `vw_sv_all_motos_semanal`. La vista guarda **N filas por contrato** (una por semana), por eso la entidad usa **`@Subselect` con dedup pre-bakeado** que selecciona la última semana por contrato. Resultado: cualquier `findById`, `findByPhone`, etc., automáticamente recibe el snapshot semanal más reciente — nadie puede olvidar deduplicar.

| Campo Java | Columna SQL | Tipo | Sentido |
|---|---|---|---|
| `contrato` (PK) | `contrato` | `char(30)` | Número de contrato |
| `numPlate` | `placa` | `char(30)` | Placa de la moto |
| `name` | `arrendador` | `varchar(200)` | Nombre del cliente |
| `balance` | `saldo` | `double(10,3)` | Saldo |
| `payment` | `cuota` | `double(20,3)` | Cuota semanal |
| `acummulatedDebet` | `deuda_cli` | `double(20,3)` | Deuda acumulada |
| `phone` | `TELULT` | `varchar(10)` | Teléfono (10 dígitos, sin código de país) |
| `paymentState` | `estado_semana` | `varchar(50)` | Estado de pago de la semana |
| `paymentDay` | `dia_canon` | `char(5)` | Día de pago (Martes, Jueves...) |

Repository:

```java
public interface ClientRepository extends JpaRepository<Client, String> {
    List<Client> findByPhone(String phone);
    Optional<Client> findByPhoneAndNumPlate(String phone, String numPlate);
}
```

### ✅ Fase 2 — Memoria conversacional · `feat/chat-session` (mergeada)

Tabla `chat_session(phone, step, contract_id, last_seen_at)` con repository y un componente programado que cada 5 minutos borra sesiones inactivas hace más de 30 minutos.

Decisión: **MySQL en vez de Redis**. A esta escala (cientos de clientes, conversaciones de 1-3 min) sumar Redis es over-engineering; reusar la conexión existente simplifica la operación y los backups.

> **Nota:** la creación física de la tabla la maneja Hibernate vía `spring.jpa.hibernate.ddl-auto=update` — ver sección 7 de Decisiones de diseño. Las entidades son la fuente de verdad del schema.

### ✅ Fase 3 — Lógica conversacional · `feat/conversation-service` (mergeada)

`ConversationService` resuelve `(phone, texto) → (respuesta, próximo estado)` aplicando estas transiciones:

```
IDLE             + cualquier texto  →  TELULT no matchea: "No tienes acceso..."  →  sigue IDLE
                                       TELULT matchea:    "Hola X, dime tu placa"  →  AWAITING_PLATE
AWAITING_PLATE   + placa correcta   →  menú 1/2/3 con contractId guardado          →  AWAITING_OPTION
                 + placa incorrecta →  "Esa placa no coincide..."                  →  sigue AWAITING_PLATE
AWAITING_OPTION  + "1"              →  "Tu saldo pendiente es $X."                 →  IDLE (sesión borrada)
                 + "2"              →  "Tu próxima cuota es $X el día Y."          →  IDLE
                 + "3"              →  "Tienes una mora de $X." (o "estás al día") →  IDLE
                 + otro             →  "Opción inválida..."                        →  sigue AWAITING_OPTION
```

Reglas de oro:
- **El bot nunca calcula plata**: los importes salen tal cual de la fila de `Client`. Si más adelante se agrega NLU (ver sección 8 de Próximas extensiones), el LLM solo clasifica intención.
- **Match por últimos 10 dígitos** del teléfono entrante (helper `util/PhoneNormalizer`): Meta entrega `573041234567`, `TELULT` guarda `3041234567`.
- **Sin pedir cédula**: la vista no la tiene; el teléfono ya identifica al cliente.

### ✅ Fase 4 — Endpoint para n8n · `feat/n8n-controller` (mergeada)

`POST /api/conversation` recibe `{ from, text }` y devuelve `{ to, text }`. n8n hace el verifyToken / firma `X-Hub-Signature-256` de Meta; el bot solo procesa.

**Auth simple por header compartido** (`security/ApiKeyFilter`): cada request a `/api/**` debe traer `X-API-Key` con el mismo valor que la propiedad `app.api-key`. `/actuator/health` y Swagger quedan abiertos para que las plataformas (Render/Azure) puedan probar liveness sin la key. No se usa Spring Security porque solo hace falta validar un header — sumarlo agregaría 2 jars y un layer extra.

Validación del request via Jakarta Validation:
- `from`: requerido, 10-15 dígitos (E.164 sin `+`).
- `text`: requerido, máximo 500 chars.

### ✅ Fase 5 — Tests · `feat/cleanup-test-and-docs` (este PR)

Cobertura final con **mocks** (sin tocar la BD para que el suite corra rápido en CI):

| Suite | Casos | Qué cubre |
|---|---|---|
| `PhoneNormalizerTest` | 6 | null, basura sin dígitos, E.164 con `+57`, separadores, ya normalizado, sub-10 dígitos |
| `ConversationServiceTest` | 10 | toda la matriz de transiciones del state machine |
| `ChatSessionCleanupTest` | 3 | threshold respeta el TTL, llamadas sucesivas usan thresholds actualizados, sin borrables no llama otros métodos |
| `ApiKeyFilterTest` | 6 | rutas públicas pasan, `/api/*` rechaza missing/invalid, header válido pasa |
| `ConversationControllerTest` | 6 | happy path, missing/invalid api key (401), JSON malformado, validación de campos |
| `MotosdelcaribeApplicationTests` | 1 | el contexto de Spring Boot carga correctamente |

**Total: 32 unit tests verdes.** Las suites con `@WebMvcTest` o `@SpringBootTest` levantan solo el slice necesario (sin scheduler real, sin tráfico HTTP).

### ⏳ Aprobación de Meta

Independiente del código: el equipo está esperando aprobación del Business Manager de WhatsApp. Mientras tanto, el bot se puede probar end-to-end con un Postman simulando a n8n (ver sección 5).

---

## 5. Setup local

Si recién clonaste el repo y querés levantar el bot apuntando a la BD de prueba:

```bash
git clone https://github.com/Sistema-de-Automatizacion/ChatBot.git
cd ChatBot
cp .env.example .env
# Editar .env con las credenciales reales (pídelas por el canal interno).
./mvnw spring-boot:run
```

Luego:

```bash
curl http://localhost:8081/actuator/health
# {"status":"UP"}
```

### Probar el endpoint de conversación (simulando a n8n)

```bash
curl -X POST http://localhost:8081/api/conversation \
  -H "Content-Type: application/json" \
  -H "X-API-Key: local-dev-key-1234" \
  -d '{"from":"573014888226","text":"Hola"}'
# {"to":"573014888226","text":"Hola Sebastian, soy el asistente de Motos del Caribe..."}
```

Sin el header `X-API-Key` (o con valor incorrecto) la respuesta es `401`. Las rutas `/actuator/health` y `/swagger-ui.html` quedan abiertas para que la plataforma de hosting pueda probar liveness sin la key.

### Correr con Docker

El repo incluye un `Dockerfile` multi-stage (build con JDK 21, run con JRE 21 + usuario no-root). Imagen final ~200MB.

```bash
# Construir la imagen
docker build -t motosdelcaribe-chatbot .

# Correrla pasándole las credenciales como variables de entorno
docker run -p 8081:8081 \
  -e DB_URL="jdbc:mysql://HOST:3306/DB?useSSL=true&serverTimezone=UTC" \
  -e DB_USERNAME=usuario \
  -e DB_PASSWORD=clave \
  -e APP_API_KEY="local-dev-key-1234" \
  motosdelcaribe-chatbot
```

Esto es lo que la mayoría de plataformas modernas (Render con `runtime: docker`, Azure App Service en modo Container, Fly.io) van a usar para desplegar. Para Render con `runtime: java` o Azure Web App nativo de Java, NO hace falta Docker — esas plataformas detectan Maven y compilan el `.jar` por su cuenta.

### Variables de entorno

| Variable | Para qué |
|---|---|
| `DB_URL` | JDBC URL de MySQL (incluye `?useSSL=true&...`) |
| `DB_USERNAME` | Usuario de la BD |
| `DB_PASSWORD` | Contraseña de la BD |
| `APP_API_KEY` | Token compartido con n8n para `/api/**` (`X-API-Key`). Default local: `local-dev-key-1234`. En producción debe ser un string random ≥ 32 chars. |
| `PORT` | _(opcional)_ Puerto HTTP. Default `8081` en dev. Render/Azure lo setean automáticamente. |

`.env` está en el `.gitignore` — nunca subas credenciales al repo.

---

## 6. Estructura del proyecto

```
ChatBot/
├── Dockerfile                                    # Multi-stage build (JDK→JRE), usuario non-root
├── .dockerignore
├── pom.xml
├── mvnw, mvnw.cmd                                # Maven wrapper
├── .env.example                                  # Plantilla — copiar a .env
├── src/main/
│   ├── java/com/chatbot/motosdelcaribe/
│   │   ├── MotosdelcaribeApplication.java        # Main + @EnableScheduling
│   │   ├── controller/
│   │   │   ├── ConversationController.java      # POST /api/conversation
│   │   │   └── ErrorController.java             # POST /api/errors
│   │   ├── dto/
│   │   │   ├── IncomingMessage.java              # { from, text } + validación
│   │   │   ├── OutgoingMessage.java              # { to, text }
│   │   │   └── ErrorReport.java                  # { phone?, context, errorMessage }
│   │   ├── model/
│   │   │   ├── Client.java                       # @Subselect sobre vw_sv_all_motos_semanal
│   │   │   ├── ChatSession.java                  # Estado conversacional + enum Step
│   │   │   └── ChatError.java                    # Log append-only de fallas async
│   │   ├── respository/                          # (sí, con typo, así está en master por ahora)
│   │   │   ├── ClientRepository.java
│   │   │   ├── ChatSessionRepository.java
│   │   │   └── ChatErrorRepository.java
│   │   ├── security/
│   │   │   └── ApiKeyFilter.java                 # OncePerRequestFilter, gate /api/**
│   │   ├── service/
│   │   │   ├── ChatSessionCleanup.java           # @Scheduled cada 5 min, TTL 30 min
│   │   │   └── ConversationService.java          # Máquina de estados del bot
│   │   └── util/
│   │       └── PhoneNormalizer.java              # Últimos 10 dígitos
│   └── resources/
│       └── application.properties
└── src/test/java/com/chatbot/motosdelcaribe/
    ├── MotosdelcaribeApplicationTests.java       # Carga del contexto Spring
    ├── controller/
    │   ├── ConversationControllerTest.java
    │   └── ErrorControllerTest.java
    ├── security/ApiKeyFilterTest.java
    ├── service/
    │   ├── ChatSessionCleanupTest.java
    │   └── ConversationServiceTest.java
    └── util/PhoneNormalizerTest.java
```

---

## 7. Decisiones de diseño relevantes

| Decisión | Alternativa considerada | Por qué |
|---|---|---|
| **n8n como orquestador**, no Spring directo a Meta | ChatBot expone webhook de Meta | Centraliza tokens y firma; el equipo ya usa n8n para el outbound |
| **MySQL para sesiones**, no Redis | Redis con TTL nativo | Escala no lo justifica; un servicio menos que pagar y monitorear |
| **`@Subselect` con dedup**, no `@Table` simple | Composite key con `@IdClass` | Imposible de olvidar el dedup; ya pasó en el Backend de cartera que un JOIN sin dedup infló datos 8x |
| **Sin pedir cédula** en el flow | Pedir cédula como en el drawio inicial | La vista no tiene esa columna; el teléfono ya identifica al cliente, mejor UX en WhatsApp |
| **Match por últimos 10 dígitos del teléfono** | Reformatear TELULT a E.164 | Meta entrega `573041234567`, TELULT guarda `3041234567` — comparar últimos 10 es robusto y portable |
| **`spring.jpa.hibernate.ddl-auto=update`** (entidades = source of truth del schema) | `schema.sql` explícito o Flyway desde día 1 | Solo 2 tablas propias y deploy a entornos limpios. `update` crea tablas/columnas/índices faltantes y nunca dropea nada. Si más adelante hace falta cambiar tipos o droppear cosas, se introduce Flyway en una migración explícita. |
| **Stack idéntico al Backend de cartera** | Otro lenguaje (Node, Python) | Una persona puede saltar entre repos sin reaprender; reusa convenciones |

---

## 8. Próximas extensiones (no en el scope actual)

Ideas que tienen sentido sumar después de v1, pero que no son parte del flow MVP:

- **NLU local con Ollama** para que el cliente pueda escribir _"necesito saber mi deuda"_ en vez de mandar `1`. Un modelo pequeño (Phi-3 mini, Llama 3.1 8B Q4) clasificaría el texto libre en una de las 3 intenciones del menú; el endpoint de respuesta seguiría siendo el mismo. Costo $0 por mensaje y soberanía de datos vs. requerir una VM con RAM/GPU. Regla que NO se rompe: el LLM jamás genera o calcula montos; solo clasifica intención.
- **Comandos de control** tipo `/menu`, `/cancelar`, `/reiniciar` para que el cliente pueda salir de un paso atascado sin esperar el TTL.
- **Múltiples contratos por teléfono**: si un cliente tiene varias motos, listarlas y dejarlo elegir antes de pedir la placa específica.
- **Migrar de `schema.sql` a Flyway** cuando haya 3+ tablas propias.

## 9. Cómo contribuir

Convención de ramas: `feat/...`, `fix/...`, `docs/...`. Pull requests contra `master`. Antes de pushear:

```bash
./mvnw clean test
```

Toda PR debe llegar con la suite verde.

---

## 10. Contacto

Issues y discusiones en https://github.com/Sistema-de-Automatizacion/ChatBot/issues.
