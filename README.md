# Parameta - Prueba técnica RRHH

Solución con dos microservicios Java y persistencia en MySQL para el registro y validación de empleados.

| Servicio | Puerto | Responsabilidad |
|----------|--------|-----------------|
| `employee-ms` | 8090 | API REST (GET), validaciones, cliente SOAP |
| `soap-ms` | 8091 | Servicio SOAP, persistencia MySQL |
| MySQL (Docker) | 3306 | Base de datos `rrhh` |

## Requisitos

- Java 21 (solo si ejecutas los microservicios en local sin Docker)
- Maven 3.9+ o `mvnw` incluido en cada módulo
- Docker Desktop

---

## Arquitectura

La solución se compone de tres componentes desplegables:

| Componente | Rol |
|------------|-----|
| **Cliente** | Postman, `curl` o cualquier consumidor HTTP |
| **employee-ms** | API REST, validaciones y enriquecimiento de la respuesta |
| **soap-ms** | Endpoint SOAP y persistencia con JPA |
| **MySQL** | Almacenamiento de empleados (tabla `employee`) |

### Vista general

```
  Cliente (Postman / curl)
           |
           |  GET /employee/validate  (query params)
           v
  +---------------------------+
  |       employee-ms         |  :8090
  |  - Bean Validation        |
  |  - Reglas de negocio      |
  |  - Cliente SOAP           |
  |  - Cálculo edad / tiempo  |
  +---------------------------+
           |
           |  SOAP saveEmployee
           v
  +---------------------------+
  |         soap-ms           |  :8091
  |  - EmployeeEndpoint       |
  |  - JPA + Flyway           |
  +---------------------------+
           |
           |  INSERT
           v
  +---------------------------+
  |   MySQL (rrhh.employee)   |  :3306
  +---------------------------+
```

### Flujo de una petición exitosa

1. El cliente invoca `GET /employee/validate` con los datos del empleado como parámetros de consulta.
2. `employee-ms` valida campos obligatorios, formato de fechas y reglas de negocio (mayoría de edad, fechas coherentes, salario positivo).
3. Si las validaciones pasan, se invoca el servicio SOAP `saveEmployee` en `soap-ms`.
4. `soap-ms` persiste el empleado en MySQL (Flyway + JPA) y devuelve el identificador de registro.
5. `employee-ms` calcula la edad actual y el tiempo de vinculación a la compañía y responde JSON con esos campos adicionales.

### Mapeo enunciado → API

| Enunciado (español) | Parámetro REST / campo JSON |
|---------------------|-----------------------------|
| Nombres | `names` |
| Apellidos | `lastNames` |
| Tipo de Documento | `typeDocument` |
| Número de Documento | `documentNumber` |
| Fecha de Nacimiento | `dateOfBirth` (`yyyy-MM-dd`) |
| Fecha de Vinculación a la Compañía | `dateAffiliationCompany` (`yyyy-MM-dd`) |
| Cargo | `position` |
| Salario | `salary` |
| Edad actual | `currentAge` → `{ years, months, days }` |
| Tiempo de vinculación | `affiliationTime` → `{ years, months, days }` |

Los contratos internos (SOAP, base de datos y código) usan inglés por convención técnica; el dominio corresponde 1:1 al enunciado de la prueba.

---

## Decisiones de diseño

### Dos microservicios (REST + SOAP)

- **REST (`employee-ms`)**: expone el contrato exigido por la prueba (GET con parámetros) y concentra validaciones y enriquecimiento de la respuesta.
- **SOAP (`soap-ms`)**: encapsula la persistencia y expone un WSDL reutilizable, alineado con el requisito de invocar un servicio web SOAP antes de guardar en MySQL.

Esta separación permite escalar, desplegar y sustentar cada responsabilidad de forma independiente.

### Validación en dos capas

1. **Bean Validation** (`@NotBlank` en `EmployeeRequestDTO`): rechaza parámetros ausentes o vacíos antes de entrar al servicio.
2. **Groovy Shell** (`validation/employee-validation.groovy`): aplica reglas de negocio (formato estricto de fechas, mayoría de edad, fechas no futuras, vinculación posterior al nacimiento, salario > 0).

Las reglas de negocio viven en un script Groovy externo ejecutado por `GroovyEmployeeValidationEngine`. El script se compila una vez y se reutiliza en memoria. Para cambiar reglas sin recompilar Java, edita el archivo y reinicia el servicio.

Ruta configurable en `application.yaml`:

```yaml
validation:
  groovy:
    script: classpath:validation/employee-validation.groovy
```

### Persistencia con Flyway y `ddl-auto: validate`

- El esquema de MySQL se versiona con Flyway (`V1__create_employee_table.sql`).
- Hibernate solo valida el modelo contra la BD; no crea tablas en runtime, lo que es más seguro en entornos productivos.

### Manejo de errores HTTP

| Código | Situación |
|--------|-----------|
| 400 | Validación de entrada o reglas de negocio |
| 409 | Documento duplicado (SOAP Fault) |
| 502 | Servicio SOAP no disponible o error de integración |
| 500 | Error interno no controlado |

### GET para registro

El enunciado solicita explícitamente **GET**. En producción se usaría `POST` para no exponer datos sensibles en URL ni logs de proxy; aquí se prioriza el cumplimiento del requisito.

### Variables de entorno (`.env`)

Toda la configuración de infraestructura (puertos, credenciales MySQL, URL SOAP) está centralizada en `.env` en la raíz del proyecto. Docker Compose lo carga automáticamente.

```bash
cp .env.example .env   # solo la primera vez
```

| Variable | Descripción |
|----------|-------------|
| `MYSQL_*` | Credenciales y puerto de MySQL |
| `EMPLOYEE_MS_PORT` | Puerto del API REST (default `8090`) |
| `SOAP_MS_PORT` | Puerto del servicio SOAP (default `8091`) |
| `SPRING_DATASOURCE_*` | Conexión JDBC para `soap-ms` |
| `SOAP_SERVICE_URL` | URL del cliente SOAP en `employee-ms` |
| `JWT_*` / `APP_*` | Seguridad JWT de `employee-ms` |

Para desarrollo local con `mvnw spring-boot:run`, `application.yaml` usa `localhost` por defecto. Si necesitas otros valores, exporta las variables desde `.env` o edítalas en tu IDE.

---

## Arranque rápido con Docker (recomendado)

Copia el archivo de entorno (si aún no existe) y levanta los tres servicios:

```bash
cp .env.example .env
docker compose up -d --build
```

La primera ejecución puede tardar varios minutos (compilación Maven dentro de las imágenes).

Verificar estado:

```bash
docker compose ps
```

Las credenciales y puertos se configuran en `.env` (valores por defecto: BD `rrhh`, usuario/contraseña `rrhh`, puerto MySQL `3306`).

Detener y eliminar contenedores:

```bash
docker compose down
```

Eliminar también los datos de MySQL:

```bash
docker compose down -v
```

---

## Arranque en local (desarrollo)

### 1. MySQL

```bash
docker compose up -d mysql
```

### 2. Servicio SOAP

```bash
cd soap-ms
./mvnw spring-boot:run
```

En Windows (PowerShell):

```powershell
cd soap-ms
.\mvnw.cmd spring-boot:run
```

### 3. API REST

```bash
cd employee-ms
./mvnw spring-boot:run
```

En Windows:

```powershell
cd employee-ms
.\mvnw.cmd spring-boot:run
```

---

## Probar el API REST

### 1. Obtener token JWT (modo local)

```bash
curl -X POST http://localhost:8090/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"rrhh","password":"rrhh"}'
```

PowerShell:

```powershell
curl.exe -X POST http://localhost:8090/auth/login -H "Content-Type: application/json" -d "{\"username\":\"rrhh\",\"password\":\"rrhh\"}"
```

Respuesta:

```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIs...",
  "tokenType": "Bearer",
  "expiresIn": 3600
}
```

### 2. Registrar empleado (con Bearer token)

Endpoint:

```
GET http://localhost:8090/employee/validate
Header: Authorization: Bearer <accessToken>
```

Ejemplo con `curl` (sustituye `TOKEN`):

```bash
curl "http://localhost:8090/employee/validate?names=Juan&lastNames=Perez&typeDocument=CC&documentNumber=123456789&dateOfBirth=1990-05-15&dateAffiliationCompany=2020-01-10&position=Developer&salary=5000000" \
  -H "Authorization: Bearer TOKEN"
```

En Windows PowerShell:

```powershell
curl.exe "http://localhost:8090/employee/validate?names=Juan&lastNames=Perez&typeDocument=CC&documentNumber=123456789&dateOfBirth=1990-05-15&dateAffiliationCompany=2020-01-10&position=Developer&salary=5000000" -H "Authorization: Bearer TOKEN"
```

Respuesta esperada (campos principales):

```json
{
  "names": "Juan",
  "lastNames": "Perez",
  "documentNumber": "123456789",
  "currentAge": { "years": 36, "months": 1, "days": 2 },
  "affiliationTime": { "years": 6, "months": 5, "days": 7 },
  "registrationId": 1,
  "message": "Employee successfully registered"
}
```

> Usa un `documentNumber` distinto en cada prueba exitosa; el documento tiene restricción única en base de datos.

---

## Seguridad JWT

`employee-ms` protege el API REST con **OAuth2 Resource Server** y tokens **JWT** sin estado (stateless).

### Variables JWT (`.env`)

| Variable | Descripción |
|----------|-------------|
| `JWT_SECRET` | Clave HMAC ≥ 32 caracteres |
| `JWT_ISSUER` | Emisor del token |
| `JWT_EXPIRATION_MINUTES` | Vigencia del token |
| `APP_USER` / `APP_PASSWORD` | Credenciales de `POST /auth/login` |
| `CORS_ALLOWED_ORIGINS` | Orígenes permitidos para frontend |

### Flujo de autenticación

1. `POST /auth/login` con `APP_USER` y `APP_PASSWORD` → token JWT.
2. Enviar `Authorization: Bearer <token>` en `GET /employee/validate`.

### Rutas y roles

| Ruta | Acceso |
|------|--------|
| `POST /auth/login` | Público |
| `GET /employee/**` | `ROLE_RRHH` |
| `/actuator/health` | Público (healthchecks Docker) |
| `/swagger-ui/**` | Público |

### Buenas prácticas aplicadas

- Sesión stateless (sin cookies de sesión)
- BCrypt para contraseñas
- Errores 401/403 en JSON estandarizado
- CORS configurable por entorno
- Claim `roles` mapeado a `ROLE_RRHH`
- Actuator con probes para orquestadores

---

```
http://localhost:8091/ws/employees.wsdl
```

---

## Validaciones implementadas

- Campos obligatorios no vacíos
- Fechas en formato `yyyy-MM-dd`
- Empleado mayor de edad (18 años o más)
- Fecha de vinculación posterior a la fecha de nacimiento y no futura
- Salario mayor a cero

---

## Tests y cobertura

```bash
cd employee-ms && ./mvnw test
cd ../soap-ms && ./mvnw test
```

Reportes JaCoCo:

- `employee-ms/target/site/jacoco/index.html`
- `soap-ms/target/site/jacoco/index.html`

---

## SonarQube — análisis de calidad

SonarQube corre en Docker desde la **raíz del monorepo** (`Parameta/`). Este módulo se publica como proyecto **`parameta-soap-ms`**.

### Paso 1 — Variables en `.env` (raíz del monorepo)

En `Parameta/.env` (plantilla: `.env.example`):

```env
SONAR_PORT=9000
SONAR_HOST_URL=http://localhost:9000
SONAR_DB_USER=sonar
SONAR_DB_PASSWORD=sonar
SONAR_DB_NAME=sonar
SONAR_TOKEN=tu-token-generado-en-sonarqube
```

> No commitees el token. El script de análisis solo lee variables `SONAR_*` del `.env`.

### Paso 2 — Levantar SonarQube

Desde la carpeta `Parameta/` (un nivel arriba de este repo):

```bash
docker compose -f docker-compose.sonar.yml up -d
```

Espera 2–3 minutos hasta que el contenedor `parameta-sonarqube` esté **healthy**.

| Recurso | URL |
|---------|-----|
| UI SonarQube | http://localhost:9000 |
| Login inicial | `admin` / `admin` (cambiar contraseña en el primer acceso) |

### Paso 3 — Generar token (solo la primera vez)

1. Entra a http://localhost:9000
2. **My Account** → **Security** → **Generate Token**
3. Copia el token en `SONAR_TOKEN` del `.env`

No es necesario crear el proyecto manualmente: se registra en el primer análisis.

### Paso 4 — MySQL para los tests (requisito de este módulo)

Los tests de `soap-ms` conectan a MySQL en **`localhost:3306`**. Antes del análisis, levanta al menos la base de datos:

```bash
# Desde Parameta/
docker compose up -d mysql
```

### Paso 5 — Ejecutar análisis de este módulo

**Opción A — script (ambos microservicios):**

```bash
# Desde Parameta/
.\scripts\sonar-analyze.ps1          # Windows
./scripts/sonar-analyze.sh           # Linux / macOS / Git Bash
```

**Opción B — solo `soap-ms`:**

```bash
cd soap-ms
./mvnw clean verify sonar:sonar -Dsonar.token=TU_TOKEN
```

En PowerShell:

```powershell
$env:SONAR_TOKEN = "tu-token"
.\mvnw.cmd clean verify sonar:sonar "-Dsonar.token=$env:SONAR_TOKEN"
```

> **Importante:** no exportes todo el `.env` de Docker antes de `mvnw` (la URL `jdbc:mysql://mysql:...` rompe los tests locales). Usa el script o pasa solo `SONAR_TOKEN`.

### Paso 6 — Ver resultados

http://localhost:9000/dashboard?id=parameta-soap-ms

Guía ampliada: `docs/07-sonarqube.md` (en la raíz del monorepo).

---

## Estructura del repositorio

### Módulo `soap-ms`

```
soap-ms/
├── Dockerfile
├── pom.xml
├── mvnw / mvnw.cmd
├── README.md
└── src/
    ├── main/
    │   ├── java/com/parameta/rrhh/soap/
    │   │   ├── endpoint/            # EmployeeEndpoint (saveEmployee)
    │   │   ├── service/               # EmployeePersistenceService
    │   │   ├── repository/          # Spring Data JPA
    │   │   ├── entity/                # EmployeeEntity
    │   │   ├── exception/             # DuplicateEmployeeException
    │   │   ├── config/                # WSDL, XSD, servlet SOAP
    │   │   └── soap/                  # Contrato JAXB (request/response)
    │   └── resources/
    │       ├── application.yaml
    │       ├── employee.xsd           # Esquema del contrato SOAP
    │       └── db/migration/
    │           └── V1__create_employee_table.sql
    └── test/java/                     # Tests de servicio y endpoint
```

| Paquete / carpeta | Responsabilidad |
|-------------------|-----------------|
| `endpoint` | Operación SOAP `saveEmployee` |
| `service` | Lógica de persistencia y reglas de duplicado |
| `repository` + `entity` | Acceso a MySQL (tabla `employee`) |
| `config` | Publicación WSDL en `/ws/employees.wsdl` |
| `db/migration` | Versionado de esquema con Flyway |

---