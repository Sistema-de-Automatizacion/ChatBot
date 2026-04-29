# Motos del Caribe — ChatBot

Servicio Spring Boot que va a contestar **automáticamente** los mensajes de WhatsApp de los clientes de Motos del Caribe Renting S.A.S. cuando pregunten cosas como _"¿cuánto debo?"_, _"¿cuándo es mi próxima cuota?"_ o _"¿tengo mora?"_. Hoy esas consultas las atiende una persona; con el bot el cliente recibe respuesta al instante, las 24 horas.

> **Estado:** en construcción. La aprobación del Business Manager de Meta para WhatsApp Business todavía está pendiente; mientras tanto se construye y prueba la lógica contra la BD.

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

Tabla `chat_session(phone, step, contract_id, last_seen_at)` con migración explícita en `schema.sql` (idempotente vía `CREATE ... IF NOT EXISTS`), repository y un componente programado que cada 5 minutos borra sesiones inactivas hace más de 30 minutos.

Decisión: **MySQL en vez de Redis**. A esta escala (cientos de clientes, conversaciones de 1-3 min) sumar Redis es over-engineering; reusar la conexión existente simplifica la operación y los backups.

### ⏳ Fase 3 — Lógica conversacional (próxima)

Implementar `ConversationService` como una función pura `(phone, mensaje) → (respuesta, próximoEstado)` que aplique las reglas del flow:

```
IDLE             + "Hola"        →  "Bienvenido X, dime tu placa"     →  AWAITING_PLATE
AWAITING_PLATE   + "ABC123"      →  "Hola X. Elige: 1.Saldo 2.Cuota 3.Mora"  →  AWAITING_OPTION
AWAITING_OPTION  + "1"           →  "Tu saldo pendiente es $..."      →  IDLE (sesión borrada)
```

### ⏳ Fase 4 — Endpoint para n8n

`POST /api/conversation` que recibe `{ from, text }` y devuelve `{ to, text }`. n8n se ocupa del verifyToken / firma de Meta; el ChatBot solo procesa.

### ⏳ Fase 5 — Tests

Unit tests del `ConversationService` (lógica pura, fácil de testear) + tests de integración del controller con `@SpringBootTest` y MockMvc.

### ⏳ Aprobación de Meta

Independiente del código: el equipo está esperando aprobación del Business Manager de WhatsApp. Mientras tanto, el bot se puede probar end-to-end con mocks o con un postman simulando a n8n.

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

### Variables de entorno

| Variable | Para qué |
|---|---|
| `DB_URL` | JDBC URL de MySQL (incluye `?useSSL=true&...`) |
| `DB_USERNAME` | Usuario de la BD |
| `DB_PASSWORD` | Contraseña de la BD |
| `PORT` | _(opcional)_ Puerto HTTP. Default `8081` en dev. Render/Azure lo setean automáticamente. |

`.env` está en el `.gitignore` — nunca subas credenciales al repo.

---

## 6. Estructura del proyecto

```
ChatBot/
├── pom.xml
├── mvnw, mvnw.cmd          # Maven wrapper
├── .env.example            # Plantilla — copiar a .env
├── src/main/
│   ├── java/com/chatbot/motosdelcaribe/
│   │   ├── MotosdelcaribeApplication.java   # Main + @EnableScheduling
│   │   ├── model/
│   │   │   ├── Client.java                  # @Subselect sobre vw_sv_all_motos_semanal
│   │   │   └── ChatSession.java             # Estado conversacional
│   │   ├── respository/                     # (sí, con typo, así está en master por ahora)
│   │   │   ├── ClientRepository.java
│   │   │   └── ChatSessionRepository.java
│   │   └── service/
│   │       └── ChatSessionCleanup.java      # @Scheduled cada 5 min, TTL 30 min
│   └── resources/
│       ├── application.properties
│       └── schema.sql                       # CREATE TABLE IF NOT EXISTS chat_session
└── src/test/java/...                        # Suite vacía por ahora (Fase 5)
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
| **`schema.sql` con `IF NOT EXISTS`** | Flyway/Liquibase desde día 1 | Una sola tabla; sumar Flyway agrega ceremonia. Migrar después es trivial |
| **Stack idéntico al Backend de cartera** | Otro lenguaje (Node, Python) | Una persona puede saltar entre repos sin reaprender; reusa convenciones |

---

## 8. Cómo contribuir

Convención de ramas: `feat/...`, `fix/...`, `docs/...`. Pull requests contra `master`. Verificar siempre `./mvnw clean compile` antes de pushear.

---

## 9. Contacto

Issues y discusiones en https://github.com/Sistema-de-Automatizacion/ChatBot/issues.
