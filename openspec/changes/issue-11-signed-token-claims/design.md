# Design

## Context

Motivacion y alcance aprobados: [proposal.md](proposal.md), con origen en [Issue #11](https://github.com/user-bcp-student38/workshop-auth-backend/issues/11). Los requisitos normativos son [authentication-tokens](specs/authentication-tokens/spec.md); este documento concreta su implementacion futura sin modificarlos. La exclusion de diseno en la propuesta correspondia a la entrega anterior; este paso autoriza solo este documento, no implementacion ni tareas.

Estado observado en `feature/HU-1`:

- Java 21, Quarkus 3.27.4, CDI, REST Jackson, SmallRye Health y SQLite. No existe dependencia JWT declarada en [pom.xml](../../../pom.xml).
- [AuthResource](../../../src/main/java/com/bcp/workshop/auth/AuthResource.java) valida request, username y password, consulta credenciales, genera token y construye respuesta dentro de un catch generico.
- [LoginRequest](../../../src/main/java/com/bcp/workshop/auth/LoginRequest.java) declara tres strings; `client` no se usa. La conversion JSON puede aceptar escalares como string y no basta para exigir el tipo original de `client`.
- [LegacyTokenUtil](../../../src/main/java/com/bcp/workshop/auth/LegacyTokenUtil.java) concatena username, role, expiracion y un secreto embebido, y aplica Base64. No hay firma ni verificacion ni lectura de `AUTH_TOKEN_SECRET`; la referencia a HMAC existente en #11 no describe esta rama.
- [LegacyDatabase](../../../src/main/java/com/bcp/workshop/auth/LegacyDatabase.java) inicializa usuarios y consulta SQLite directamente; imprime excepciones. Un error SQL puede incluir credenciales en su mensaje, lo que afecta la privacidad de logs del flujo comprometido.
- [AuthResourceTest](../../../src/test/java/com/bcp/workshop/auth/AuthResourceTest.java) solo comprueba HTTP 200, username y presencia del token. No caracteriza errores, tipos JSON, reloj ni escape HTML.
- [application.properties](../../../src/main/resources/application.properties) mantiene CORS permisivo e ignora propiedades JSON desconocidas. No se cambia esa configuracion global.

Aplican [REGLAS.md](../../../REGLAS.md) y [GOOD_PRACTICES.md](../../../docs/GOOD_PRACTICES.md). La convergencia a capas se limita al flujo tocado. El contrato aprobado de errores prevalece aqui sobre la recomendacion general de redisenarlos.

## Goals / Non-Goals

**Goals:**

- Encapsular emision y verificacion criptografica detras de un componente de seguridad pequeno, independiente de HTTP y de SQLite.
- Separar orquestacion del login y adaptacion HTTP sin introducir un framework de arquitectura ni nuevas capas de persistencia.
- Hacer deterministas el reloj, la configuracion del secreto y las pruebas de limites.
- Usar una implementacion JWT mantenida, no serializacion ni criptografia caseras, con verificacion restrictiva.
- Producir cambios futuros pequenos y revisables, empezando por caracterizacion ejecutable.

**Non-Goals:**

- Migrar SQLite, cambiar consultas, incorporar ORM, hash de contrasenas o un nuevo modelo de usuarios.
- Agregar filtros Bearer, roles globales, endpoints de verificacion, renovacion o revocacion.
- Redisenar errores, CORS, health, todas las dependencias o el resto de la aplicacion.
- Leer, modificar o certificar compatibilidad de consumidores en otros repositorios.
- Implementar en este paso, crear `tasks.md` o reescribir propuesta y specs aprobadas.

## Contratos protegidos

Baseline: [ENDPOINTS.md](../../../docs/ENDPOINTS.md). Los escenarios completos se mantienen en la spec, no se sustituyen por este resumen.

| Superficie | Invariante / cambio autorizado |
| --- | --- |
| Login publico | `POST /auth/login`, JSON y sin autenticacion previa |
| Exito | HTTP 200; `token` string; `expiresIn` integer 3600; `user.username`, displayName escapado y role persistido; `debug` literal existente |
| Validacion existente | Request nulo, username y password conservan precedencia, HTTP 400 y mensajes exactos |
| Credenciales | Con client valido, HTTP 401 y mensaje existente para usuario desconocido o password incorrecta |
| Error inesperado | HTTP 500, `message` y `exception`; excepcion sin mensaje conserva `Unknown error` |
| Cliente | Cambio aprobado: string obligatorio no blanco; error HTTP 400 con `message` sobre client; conservar valor exacto aceptado |
| Token | Cambio aprobado: JWT firmado en lugar del Base64 legacy; claims minimos y vigencia conforme a la spec |
| Health y runtime REST | Sin autenticacion nueva ni health checks nuevos; no fijar un cuerpo de checks ni resultados de JSON malformado no garantizados por el baseline |

No se elimina `debug` aunque su texto deje de describir la emision. No se expande la compatibilidad prometida al formato interno legacy del token.

## Componentes afectados

Los nombres nuevos son responsabilidades propuestas, no codigo ya existente.

| Componente | Cambio acotado previsto |
| --- | --- |
| `AuthResource` | Adaptacion HTTP, validaciones de entrada y mapeo de resultados; preservar cuerpos y catch de frontera para errores inesperados |
| `LoginRequest` | Preservar username/password; representar client con tipo JSON original para validacion estricta localizada |
| `AuthenticationService` nuevo | Consultar `LegacyDatabase`, diferenciar credenciales incorrectas y solicitar emision; sin respuestas HTTP |
| `TokenService` nuevo | Emitir y verificar JWT, poseer politica de claims, duracion y algoritmos; devolver resultado tipado sin secretos |
| Configuracion de tokens | Leer y validar `AUTH_TOKEN_SECRET` al iniciar; proporcionar clave y `Clock` al componente de seguridad |
| `LegacyTokenUtil` | Retirar su uso y eliminar emision legacy tras caracterizarla; no mantener un fallback |
| `LegacyDatabase` | Mantener API y comportamiento de consulta; solo sanear diagnosticos que puedan filtrar credenciales en este flujo |
| Maven y pruebas | Dependencia JOSE/JWT, tests unitarios y caracterizacion REST; conservar JaCoCo y controles existentes |
| Documentacion operativa | En implementacion futura, documentar secreto y transicion contractual sin borrar la distincion entre baseline y nuevo comportamiento |

## Decisions

### 1. JWT compacto con HS256 mediante Nimbus JOSE + JWT

Adoptar `com.nimbusds:nimbus-jose-jwt` para construir, parsear y verificar JWS compacto. Usar exclusivamente HS256 con clave simetrica obtenida del secreto configurado. Fijar una version concreta compatible con Java 21 durante la incorporacion de la dependencia, previa revision de vulnerabilidades; no usar rangos, `LATEST` ni actualizar Quarkus para este cambio.

La eleccion combina HMAC y libreria JWT: no son alternativas excluyentes. `sub`, `client`, `iat` y `exp` tendran los tipos de la spec; conservar tambien `role` como claim informativo para no perder datos disponibles en el token anterior, sin introducir autorizacion. El payload no incluye secreto ni password. JWT firmado no significa cifrado: sus claims siguen siendo legibles.

Verificar formato y algoritmo permitido antes de aceptar el token; rechazar `none`, algoritmos distintos, estructuras malformadas, cabeceras criticas no soportadas y cualquier token legacy. Usar la verificacion de firma de la libreria, sin implementar comparacion criptografica manual ni aceptar claves suministradas por cabeceras del token. Solo despues de verificar firma, validar claims requeridos y sus tipos, coherencia temporal y expiracion. El resultado sera tipado: valido o rechazo por formato/firma/claims/expiracion; no devolver un usuario por defecto ni un booleano que oculte la causa.

**Alternativas consideradas:**

- HMAC manual con JDK y Jackson: evita una dependencia, pero obliga a mantener formato, validacion y restricciones criptograficas propias. Descartado.
- Extensiones Quarkus de autenticacion JWT global: buena integracion para endpoints protegidos, pero agregan una superficie y configuracion de autenticacion que este Issue no pide. Descartadas para mantener login y health publicos.
- Firma asimetrica: reduce distribucion de secretos entre verificadores, pero requiere gestion de claves fuera del alcance de `AUTH_TOKEN_SECRET`. Descartada.
- Conservar Base64 y agregar campos: no permite probar autenticidad. Descartado.

### 2. Configuracion validada al inicio, sin secreto predeterminado

Leer `AUTH_TOKEN_SECRET` por la configuracion de Quarkus y validar anticipadamente mediante un bean de inicio, antes de servir solicitudes. Rechazar ausencia, valor vacio o blanco y claves con menos de 32 bytes UTF-8, minimo de HS256. Usar los bytes del valor exacto: no recortar, completar, truncar ni transformar un secreto debil para aparentar longitud valida. Documentar que se debe generar con entropia suficiente; la longitud por si sola no garantiza fortaleza.

No habra valor por defecto en ningun perfil. `dev` y `test` son entornos sandbox de laboratorio; `prod` y cualquier perfil no identificado explicitamente como sandbox se tratan como no sandbox. Todos requieren secreto suministrado explicitamente, por lo que una clasificacion equivocada nunca activa una clave embebida. Los tests proporcionan claves sinteticas aisladas desde su configuracion de prueba, no desde una ruta de fallback productiva.

Fallar el arranque con un diagnostico saneado que nombre la variable y la restriccion incumplida, sin valor ni longitud real del secreto. Esto satisface el fallo explicito sin inventar un nuevo contrato HTTP de configuracion. La clave se conserva solo en memoria del componente que la necesita y nunca en `toString`, resultados o excepciones.

**Alternativas:** fallback en dev/test complica el aislamiento y puede activarse accidentalmente; validar en cada login pospone errores operativos y mezcla configuracion con HTTP. Ambas se descartan.

### 3. Reloj explicito y una sola politica de vigencia

Inyectar `java.time.Clock`, UTC en ejecucion y fijo/controlable en tests. Una constante con nombre define 3600 segundos. El resultado de emision contiene token, instante de emision y duracion; el recurso no mantiene otro literal independiente para `expiresIn`.

Consultar credenciales y preparar los datos del usuario antes de emitir. Tomar `iat` en segundos Unix al ensamblar la respuesta; calcular `exp = iat + 3600` y `expiresIn` desde esa misma duracion. Comprobar `now` en el servidor al construir la respuesta: si la distancia excede un segundo, no entregar una respuesta 200 incoherente; informar un fallo explicito en la frontera existente. No medir la tolerancia con la hora de recepcion del cliente.

La verificacion recibe el mismo tipo de reloj, compara `now >= exp` y no aplica gracia ni tolerancia de JWT. Validar `exp - iat = 3600` para los tokens propios. Las validaciones temporales se realizan explicitamente sobre claims ya autenticados, sin depender de un reloj interno de la libreria o de una gracia predeterminada.

**Alternativas:** llamadas independientes a `Instant.now()` dificultan probar limites y generan divergencias; tolerancia de expiracion para absorber demoras cambia la spec. Se descartan.

### 4. Validacion estricta de client solo en este DTO

Representar el campo `client` de entrada como nodo JSON de Jackson; validar despues de request, username y password: ausente/nulo, tipo no textual o contenido blanco producen HTTP 400. Para string invalido usar `message: "Client is required"`; para tipo no textual, `message: "Client must be a string"`. Extraer el texto solo tras validar y pasarlo sin trim al servicio y al token.

Mantener el orden del flujo: request, username, password, client, consulta de credenciales y emision. Las pruebas de precedencia existentes se mantienen; con client invalido no se consulta persistencia ni se emite token. Los campos JSON desconocidos siguen ignorandose.

**Alternativas:** string Java sin control adicional puede aceptar coercion de numeros o booleanos; endurecer el ObjectMapper global afectaria entradas ajenas; una deserializacion que falle antes del recurso complicaria la precedencia de errores. Se descartan.

### 5. Separacion minima y errores compatibles

Extraer solo la orquestacion tocada a `AuthenticationService`, conservando `LegacyDatabase` como colaborador existente. Usar resultados pequenos y tipados para usuario autenticado y token emitido, no un mapa de datos internos ni una nueva jerarquia de repositorios. El recurso conserva adaptacion HTTP y escape HTML; la seguridad no conoce HTTP ni construye respuestas.

El servicio distingue credenciales invalidas de errores de emision. No capturar errores de firma para entregar un token legacy o una respuesta de exito. La verificacion es una operacion interna comprobable en tests, sin endpoint ni filtro HTTP. Un token expirado no se presenta como error de login: el login emite tokens nuevos.

**Alternativas:** modificar solo el metodo largo mantiene responsabilidades mezcladas; reestructurar toda la aplicacion excede este cambio. Se elige el punto intermedio exigido por las reglas locales.

### 6. Diagnosticos seguros sin redisenar respuestas

Emitir eventos acotados con resultado/categoria y, si se necesita, clase de error; no serializar DTOs, mapas de respuesta, claves ni objetos de excepcion con contenido no controlado. No registrar bodies ni activar trazas HTTP de entrada/salida. Los rechazos de verificacion indican categoria, nunca el token.

Sustituir los `printStackTrace` del flujo SQLite por diagnosticos saneados que preserven visibilidad de la operacion fallida sin mensaje SQL, parametros ni throwable completo. Es una correccion directamente necesaria para el requisito de logs, no una refactorizacion de consultas ni cambio de sus retornos actuales.

Mantener el body HTTP 500 aprobado, incluido `exception`; la deuda de exposicion de errores al cliente permanece fuera de alcance. En los componentes nuevos, crear excepciones con mensajes constantes sin valores sensibles para que ese contrato no los exponga.

**Alternativas:** silenciar errores incumple las reglas; registrar stacktraces completos puede incluir passwords en SQL o valores de configuracion; cambiar todos los errores publicos contradice el baseline. Se descartan.

## Pruebas de caracterizacion y validacion

### Antes de modificar token, validacion u orquestacion

Ampliar primero la caracterizacion con el comportamiento actual. No tratar estos casos como pruebas ya ejecutadas:

- REST con RestAssured: exito completo para student/trainer, tipos de los campos, `expiresIn` y `debug`; campos extra ignorados; errores 400 y 401 con cuerpos exactos y ausencia de token.
- Invocacion directa del recurso para request Java nulo, precedencia de username/password y excepcion no controlada sin mensaje. No confundir un request Java nulo con el comportamiento de JSON malformado del runtime.
- Datos de usuario y escape HTML usando un doble de `LegacyDatabase` que devuelva un `UserRecord` sintetico; no alterar usuarios de la base real.
- Caracterizar que client ausente se acepta actualmente y que el token legacy contiene username, role y expiracion. Marcar estos casos como comportamiento a reemplazar, no como invariantes tras activar el cambio. No imprimir el token ni el contenido que incluye el secreto.
- Health sin autenticacion: comprobar estado y estructura de checks disponible, sin fijar checks vacios como garantia universal.

Usar una SQLite temporal por ejecucion mediante `AUTH_DB_URL`, definida antes de inicializar `LegacyDatabase`; no modificar `data/workshop.db`. Los tests directos pueden usar dobles concretos pequenos sin agregar un framework de mocks. Separar proceso/configuracion para pruebas de arranque fallido.

### Al integrar el comportamiento aprobado

| Area | Evidencia exigida |
| --- | --- |
| Client | Ausente, null, vacio, blanco, numero, booleano, objeto y array dan 400; valores nuevos y espacios exteriores se conservan; username/password mantienen precedencia |
| JWT | Claims y tipos exactos; firma valida; clave diferente; alteracion individual de sub/client/iat/exp; rechazo legacy, `none`, algoritmo no permitido, claims incompletos y formato incorrecto |
| Tiempo | Reloj fijo T: iat T, exp T+3600; expiracion en exp-1, exp y exp+1; tolerancia de respuesta 0/1 segundos y rechazo de diferencia 2; sin sleeps |
| Configuracion | Arranque con clave valida; rechazo de ausente, vacia, blanca o corta en prod y perfiles custom; mismo requisito sin fallback en dev/test |
| Privacidad | Captura de logs de exito, credenciales invalidas, error de configuracion, fallo SQLite y token expirado; buscar marcadores sinteticos de password, secreto y token sin incluirlos en evidencia |
| Regresion | Reejecutar cuerpos y estados protegidos; mantener debug y escape; confirmar login/health publicos sin instalar autenticacion global |

En pruebas criptograficas, contrastar la firma con el verificador de la libreria usando la clave sintetica; no limitarse a decodificar ni a probar emisor y verificador propios contra el mismo error. No comparar tokens completos como snapshots.

Comandos previstos para implementacion: `mvn "-Dtest=AuthResourceTest" test` para caracterizacion y selectores conjuntos de tests de tokens/configuracion al incorporarlos; finalmente `mvn verify` para suite e informe JaCoCo. Medir cobertura del codigo modificado >=85% segun las reglas; el POM actual genera informe pero no impone ese umbral. No desactivar CodeQL, Dependency Review ni JaCoCo. Esta entrega documental solo valida OpenSpec.

## Risks / Trade-offs

- [Dependencia criptografica nueva] -> Fijar version concreta y revisar dependencia/transitivas antes de incorporar; el diseno no afirma certificacion de una version no instalada.
- [Clientes que omiten client o decodifican el token legacy] -> Cambio incompatible aprobado; comunicar el nuevo contrato antes de activar, sin inspeccionar ni modificar otros repositorios.
- [HMAC comparte capacidad de firmar con todo poseedor de la clave] -> Restringir distribucion del secreto; gestion asimetrica y rotacion automatica requieren otro alcance.
- [JWT legible] -> No incluir secreto/password; no prometer confidencialidad de username, client o role.
- [Tiempo del host incorrecto o pausa larga al responder] -> Reloj explicito, limites deterministas y fallo explicito si se excede coherencia; sincronizacion del host es responsabilidad operativa.
- [Sin secreto el servicio no inicia, incluido health] -> Validar configuracion antes de despliegue. No es un cambio al contrato de health de un servicio arrancado, sino un pre-requisito operativo.
- [Diagnosticos saneados con menor detalle] -> Conservar operacion/categoria para investigar sin exponer SQL o credenciales; evidencia sintetica prueba la ausencia de filtraciones.
- [SQL concatenado, passwords en claro y HTTP 500 detallado persisten] -> No declararlos corregidos por emitir JWT; mantener uso en laboratorio aislado y abordar esas deudas en cambios separados.
- [Client autodeclarado] -> El claim prueba integridad del valor recibido, no identidad ni autorizacion del cliente; no agregar una whitelist implicita.

## Migration Plan

Estrategia incremental para una futura implementacion, no lista de tareas ejecutada:

1. Fijar primero la caracterizacion del contrato actual con datos aislados y registrar la linea base.
2. Extraer la orquestacion minima conservando resultados HTTP; mantener la caracterizacion verde antes de sustituir el token.
3. Incorporar configuracion, reloj y componente JWT con pruebas unitarias independientes, sin fallback ni cambio parcial del endpoint desplegado.
4. Integrar conjuntamente client obligatorio y emision firmada. Sustituir solo las caracterizaciones de client opcional y formato legacy por los escenarios aprobados; conservar el resto. Sanear los logs del flujo afectado.
5. Completar pruebas de arranque, REST, tiempo y privacidad; verificar suite, cobertura y dependencia. Documentar configuracion y diferencias con el baseline.
6. Activar en laboratorio aislado con secreto explicito, tras comunicar la ruptura contractual. Validar login y health sin autenticar y sin imprimir tokens.

No hay migracion de esquema ni datos. No se entrega compatibilidad dual, flag que acepte tokens legacy ni periodo de gracia. El verificador interno solo acepta el nuevo formato; no se cambia un verificador de otro proyecto.

### Rollback

- Antes de activacion: retirar el candidato y conservar el artefacto previo; no hay datos que restaurar.
- Tras activacion: ante fallo de configuracion, corregir o restaurar la configuracion valida del mismo candidato; no agregar una clave de emergencia ni habilitar fallback.
- Ante una regresion funcional, detener o aislar el servicio y restaurar un artefacto conocido solo dentro del laboratorio. Revertir a la emision Base64 reintroduce la vulnerabilidad y deja de cumplir #11: requiere aceptacion explicita del responsable, no se presenta como rollback seguro.
- No registrar ni restaurar tokens o secretos desde evidencia. Un cambio de clave invalida los tokens anteriores y exige nuevo login; no hay almacenamiento de sesiones que recuperar.

## Open Questions

No quedan decisiones de arquitectura necesarias para este alcance. La version patch concreta de Nimbus y los identificadores de los eventos de diagnostico se fijaran al implementar, sin cambiar formato, algoritmo, contratos o estrategia; la revision de dependencias es una condicion previa a su incorporacion, no una validacion ya realizada.
