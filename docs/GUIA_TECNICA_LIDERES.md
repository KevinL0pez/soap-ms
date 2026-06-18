# Guía técnica para líderes — Parameta RRHH

Documento de alto nivel para presentar la solución a arquitectos, tech leads y stakeholders técnicos. Describe **por qué** se tomaron ciertas decisiones y **cómo** fluyen JWT, validación Groovy e integración SOAP.

---

## 1. Visión general

La solución responde a una prueba técnica de registro de empleados con tres piezas desacopladas:

| Componente | Puerto | Rol |
|------------|--------|-----|
| **employee-ms** | 8090 | Fachada REST: seguridad JWT, validaciones de negocio, cliente SOAP |
| **soap-ms** | 8091 | Contrato SOAP legacy, persistencia JPA + MySQL |
| **MySQL** | 3306 | Almacenamiento transaccional |

```
Cliente (Postman / frontend)
        │
        │  HTTPS + JWT Bearer
        ▼
┌───────────────────┐     validación      ┌─────────────────────┐
│   employee-ms       │ ─── Groovy Shell ──►│  Reglas de negocio  │
│   (REST + JWT)      │                     │  (script externo)   │
└─────────┬─────────┘                     └─────────────────────┘
          │
          │  SOAP/XML (red interna Docker)
          ▼
┌───────────────────┐     JPA + Flyway    ┌───────────────────┐
│     soap-ms       │ ──────────────────► │      MySQL        │
│  saveEmployee     │                     │   tabla employee  │
└───────────────────┘                     └───────────────────┘
```

**Principio de diseño:** la capa REST no persiste directamente en base de datos. Respeta el contrato SOAP exigido y mantiene `soap-ms` como único dueño de la persistencia.

---

## 2. Flujo JWT (seguridad en employee-ms)

### 2.1 Objetivo

Proteger `GET /employee/validate` para que solo personal de RRHH consuma el API. El servicio es **stateless**: no hay sesiones en servidor; el cliente porta el token.

### 2.2 Dos modos de despliegue

| Modo | Variable | Uso | Emisor del token |
|------|----------|-----|------------------|
| **Local / Docker** | `JWT_MODE=local` | Desarrollo y demo | `employee-ms` (`POST /auth/login`) |
| **AWS** | `JWT_MODE=cognito` + perfil `aws` | Producción | Amazon Cognito |

En local, `employee-ms` firma tokens **HS256** con `JWT_SECRET`. En AWS, valida tokens **RS256** de Cognito vía JWKS (`JWT_ISSUER_URI`). El mismo filtro Spring Security atiende ambos escenarios gracias a `JwtAuthorityConverter`.

### 2.3 Flujo local (login + consumo)

```
1. Cliente ──POST /auth/login──► AuthController
                                    │
                                    ▼
                              LocalAuthService
                                    │
                    AuthenticationManager valida APP_USER / APP_PASSWORD
                                    │
                                    ▼
                              JwtEncoder (HS256)
                                    │
2. Cliente ◄── { accessToken, tokenType, expiresIn } ──┘

3. Cliente ──GET /employee/validate + Authorization: Bearer <token>──►
                                    │
                                    ▼
                         OAuth2 Resource Server
                         JwtDecoder verifica firma y exp
                                    │
                                    ▼
                         JwtAuthorityConverter → ROLE_RRHH
                                    │
                                    ▼
                         EmployeeController (200 + JSON)
```

**Puntos clave para líderes técnicos:**

- El token **no se guarda** en `employee-ms`; Postman o el frontend lo almacenan.
- `soap-ms` **no valida JWT**; vive en red interna y solo acepta SOAP desde `employee-ms`.
- Errores de seguridad devuelven JSON (`401` / `403`), no HTML, vía `SecurityProblemSupport`.
- Migración a AWS no requiere cambiar controladores: solo variables de entorno y perfil Spring.

### 2.4 Clases involucradas

| Clase | Responsabilidad |
|-------|-----------------|
| `SecurityConfig` | Reglas de URL, CORS, resource server |
| `LocalJwtConfig` | Beans `JwtEncoder` / `JwtDecoder` (solo local) |
| `LocalAuthService` | Emisión del token tras login |
| `AuthController` | Endpoint `POST /auth/login` |
| `JwtAuthorityConverter` | Mapeo `roles` o `cognito:groups` → `ROLE_*` |
| `SecurityProperties` | Configuración tipada y validación al arranque |

---

## 3. Flujo Groovy Shell (validaciones de negocio)

### 3.1 Objetivo

Separar las **reglas de negocio** del código Java compilado. El enunciado exige validaciones (mayoría de edad, fechas coherentes, salario positivo, campos obligatorios) que pueden cambiar con frecuencia en un contexto real de RRHH.

### 3.2 Por qué Groovy y no solo Bean Validation

| Enfoque | Ventaja | Limitación |
|---------|---------|------------|
| Anotaciones Jakarta (`@NotNull`, etc.) | Rápido para reglas simples | Reglas cruzadas (edad + fechas) quedan dispersas en Java |
| **Groovy Shell + script externo** | Reglas legibles, modificables sin redeploy completo | Requiere disciplina en el contrato de retorno del script |

La implementación usa un **motor Java** (`GroovyEmployeeValidationEngine`) que carga el script una vez y lo ejecuta por petición. El servicio `EmployeeValidationServiceImpl` actúa como fachada para no acoplar el resto de la aplicación a Groovy.

### 3.3 Flujo de ejecución

```
EmployeeController
        │
        ▼
EmployeeServiceImpl.registerEmployee()
        │
        ▼
EmployeeValidationServiceImpl.validate()
        │
        ▼
GroovyEmployeeValidationEngine.validate()
        │
        ├─ Binding: request (DTO), today (Clock inyectable)
        ├─ Compila/cacha employee-validation.groovy
        └─ Ejecuta script
                │
        ┌───────┴───────┐
        ▼               ▼
   [errors: ...]   Map con campos tipados
        │               │
        ▼               ▼
 ValidationException   ValidatedEmployee
   (HTTP 400)          (continúa a SOAP)
```

### 3.4 Contrato del script

Archivo: `employee-ms/src/main/resources/validation/employee-validation.groovy`

**Entradas inyectadas:**

- `request` — parámetros REST sin normalizar
- `today` — fecha de referencia (permite tests deterministas con `Clock` fijo)

**Salidas:**

- Error: `[errors: ["mensaje 1", "mensaje 2"]]`
- Éxito: mapa con `names`, `lastNames`, `typeDocument`, `documentNumber`, `dateOfBirth`, `dateAffiliationCompany`, `position`, `salary`

**Reglas implementadas hoy:**

1. Campos obligatorios no vacíos
2. Fechas en formato `yyyy-MM-dd` (ISO estricto)
3. Salario numérico y mayor que cero
4. Fechas no futuras
5. Fecha de afiliación posterior a nacimiento
6. Mayoría de edad (≥ 18 años)

### 3.5 Implicaciones operativas

- Cambiar reglas = editar el `.groovy` y reiniciar el servicio (o hot-reload si se implementara en el futuro).
- Los tests unitarios usan `Clock` fijo y el mismo script del classpath.
- El motor valida que el script devuelva la estructura esperada; fallos de infraestructura → `500`, fallos de negocio → `400` con lista de errores.

---

## 4. Flujo SOAP (integración employee-ms ↔ soap-ms)

### 4.1 Objetivo

Cumplir el requisito de un **servicio SOAP** para persistencia, manteniendo al cliente final en REST/JSON. `employee-ms` actúa como **anti-corruption layer** entre HTTP y XML.

### 4.2 Contrato

- **Namespace y operación:** `saveEmployee` definidos en `employee.xsd`
- **WSDL público (soap-ms):** `http://soap-ms:8091/ws/employees.wsdl`
- **Payload:** `SaveEmployeeRequest` con `EmployeeType` (nombres, documento, fechas como string ISO, cargo, salario)

### 4.3 Flujo end-to-end

```
1. REST validado (ValidatedEmployee)
        │
        ▼
2. EmployeeMapper.toSoapEmployee()
   Java record → EmployeeType (JAXB)
        │
        ▼
3. SoapEmployeeClient.saveEmployee()
   WebServiceTemplate.marshalSendAndReceive()
        │
        │  HTTP POST + SOAP Envelope (XML)
        ▼
4. soap-ms / EmployeeEndpoint.saveEmployee()
        │
        ▼
5. EmployeePersistenceService.save()
   - Verifica duplicado por documentNumber
   - Mapea a EmployeeEntity
   - INSERT vía JPA
        │
        ▼
6. SaveEmployeeResponse (registrationId, message)
        │
        ▼
7. employee-ms enriquece respuesta REST:
   - currentAge (PeriodCalculatorUtil)
   - affiliationTime (PeriodCalculatorUtil)
   - registrationId del SOAP
```

### 4.4 Manejo de errores

| Situación | Origen | HTTP al cliente |
|-----------|--------|-----------------|
| Empleado duplicado | `DuplicateEmployeeException` en soap-ms | `409 Conflict` |
| SOAP fault / regla de negocio | Fault XML | `502` o mensaje del fault |
| soap-ms caído | `WebServiceIOException` | `502` con mensaje orientativo |
| Validación REST | Groovy script | `400` con lista de errores |

### 4.5 Decisiones de arquitectura

- **JAXB** en ambos lados para tipado fuerte y alineación con el XSD.
- **Flyway** en `soap-ms` para versionar el esquema (`V1__create_employee_table.sql`).
- **soap-ms sin JWT:** asume red de confianza (Docker network / VPC privada). En producción real se recomienda mTLS o API Gateway interno.
- **Healthcheck Docker:** soap-ms expone WSDL; employee-ms expone `/actuator/health`.

### 4.6 Clases involucradas

**employee-ms (cliente):**

| Clase | Rol |
|-------|-----|
| `SoapClientConfig` | `Jaxb2Marshaller` + `WebServiceTemplate` |
| `SoapEmployeeClient` | Invocación y traducción de excepciones |
| `EmployeeMapper` | Dominio ↔ SOAP ↔ DTO REST |

**soap-ms (servidor):**

| Clase | Rol |
|-------|-----|
| `WebServiceConfig` | Servlet SOAP, WSDL, XSD |
| `EmployeeEndpoint` | `@Endpoint` Spring WS |
| `EmployeePersistenceService` | Transacción y reglas de duplicado |
| `EmployeeRepository` | Spring Data JPA |

---

## 5. Flujo completo de una petición (síntesis)

Secuencia típica con Docker Compose:

1. **Autenticación:** `POST :8090/auth/login` → JWT.
2. **Registro:** `GET :8090/employee/validate?names=...&...` + header Bearer.
3. **Seguridad:** Spring valida JWT y rol `RRHH`.
4. **Validación:** Groovy Shell → `ValidatedEmployee` o `400`.
5. **Integración:** SOAP a `:8091/ws` → persistencia MySQL.
6. **Respuesta:** JSON con datos del empleado, `currentAge`, `affiliationTime` y `registrationId`.

Tiempo crítico: la validación Groovy y la llamada SOAP son **síncronas** en el mismo hilo de la petición HTTP. No hay colas ni eventos en esta versión.

---

## 6. Despliegue y configuración relevante

| Variable | Efecto |
|----------|--------|
| `JWT_MODE` | `local` vs `cognito` |
| `JWT_SECRET` | Firma HS256 (local, ≥ 32 caracteres) |
| `APP_USER` / `APP_PASSWORD` | Credenciales de login local |
| `SOAP_SERVICE_URL` | URL del endpoint SOAP (`http://soap-ms:8091/ws` en Docker) |
| `SPRING_DATASOURCE_*` | Conexión MySQL en soap-ms |

Orquestación: `docker compose up --build` en la raíz del monorepo levanta MySQL → soap-ms → employee-ms con healthchecks encadenados.

---

## 7. Calidad y extensibilidad

| Área | Estado actual | Evolución natural |
|------|---------------|-------------------|
| Tests | Unitarios + integración JWT en employee-ms; endpoint y servicio en soap-ms | Contract tests SOAP (WireMock / Testcontainers) |
| Cobertura | JaCoCo en ambos módulos | Umbral mínimo en CI |
| Observabilidad | Logs estructurados, actuator health | Trazas distribuidas (OpenTelemetry) |
| Seguridad | JWT en REST; SOAP interno | mTLS entre microservicios en AWS |
| Reglas de negocio | Script Groovy versionado en repo | Catálogo de scripts o motor de reglas (Drools) si la complejidad crece |

---

## 8. Mensajes clave para una presentación ejecutiva técnica

1. **Separación de responsabilidades:** REST para el consumidor, SOAP para legacy/persistencia, MySQL como sistema de registro.
2. **Seguridad pragmática:** JWT stateless con camino claro a Cognito sin reescribir la API.
3. **Reglas externalizadas:** Groovy Shell reduce acoplamiento entre políticas de RRHH y despliegues Java.
4. **Operable desde día uno:** Docker Compose, Postman, healthchecks y documentación OpenAPI.
5. **Deuda consciente:** SOAP sin autenticación propia (aceptable en red privada; documentada para hardening en AWS).

---

## Referencias en el código

- JWT: `employee-ms/.../security/`
- Groovy: `employee-ms/.../validation/groovy/` y `resources/validation/employee-validation.groovy`
- SOAP cliente: `employee-ms/.../client/SoapEmployeeClient.java`
- SOAP servidor: `soap-ms/.../endpoint/EmployeeEndpoint.java`
- Orquestación: `employee-ms/.../service/impl/EmployeeServiceImpl.java`
