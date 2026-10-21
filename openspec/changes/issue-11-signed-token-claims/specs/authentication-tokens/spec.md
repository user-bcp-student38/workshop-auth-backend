# Spec Delta

## Purpose

Definir la emision de tokens autenticables y con vigencia verificable para el login del laboratorio, con identidad y cliente explicitos y sin registrar datos sensibles. Fuente de alcance: [Issue #11](https://github.com/user-bcp-student38/workshop-auth-backend/issues/11); baseline: [docs/ENDPOINTS.md](../../../../../docs/ENDPOINTS.md).

La obligatoriedad de `client` y la sustitucion del contenido reversible del token son cambios intencionales al baseline. Esta spec no elige formato, algoritmo ni libreria, ni introduce un endpoint de verificacion.

## ADDED Requirements

### Requirement: Cliente obligatorio y reflejado sin sustitucion

El servicio MUST exigir que `client` sea un string con al menos un caracter distinto de espacio en `POST /auth/login`. MUST rechazar su ausencia o valor invalido con HTTP 400, un objeto JSON con `message` explicativo y sin token. Para un valor valido, MUST conservar el valor recibido en el claim `client`, sin valor predeterminado ni lista de clientes permitidos.

#### Scenario: Cliente ausente o invalido

- **WHEN** se solicita login con credenciales validas y `client` ausente, nulo, `""`, `"   "` o de tipo JSON distinto de string
- **THEN** cada caso produce HTTP 400 con `message` que identifica el error de `client`
- **AND** no se devuelve un token

#### Scenario: Cliente recibido conservado

- **WHEN** dos solicitudes con credenciales validas envian respectivamente `client: "angular-workshop"` y `client: "otro-cliente"`
- **THEN** ambas producen HTTP 200 y cada token contiene exactamente el cliente de su solicitud

#### Scenario: Cliente no vacio con espacios exteriores

- **WHEN** un login valido envia `client: " angular-workshop "`
- **THEN** el login produce HTTP 200 y el claim `client` conserva `" angular-workshop "`

### Requirement: Respuesta exitosa compatible

El servicio SHALL mantener el login publico via JSON y HTTP 200 para credenciales y cliente validos. SHALL devolver `token` string no vacio, `expiresIn` integer igual a 3600, `user` con username y role persistidos y displayName escapado en HTML, y `debug` igual a `authenticated-with-legacy-flow`. Solo el contenido del token cambia respecto del formato legacy.

#### Scenario: Login del usuario de laboratorio

- **WHEN** se envia `POST /auth/login` sin autenticacion previa, con Content-Type `application/json`, username `student`, su password de laboratorio valida y client `angular-workshop`
- **THEN** se obtiene HTTP 200 con token no vacio, expiresIn integer 3600 y debug `authenticated-with-legacy-flow`
- **AND** user contiene username `student`, displayName `Workshop Student` y role `STUDENT`

#### Scenario: Datos persistidos y escape de HTML

- **WHEN** un usuario de prueba con role `TRAINER` y displayName persistido `<b>Trainer</b>` inicia sesion con credenciales y cliente validos
- **THEN** user.username y user.role coinciden con sus valores persistidos
- **AND** user.displayName es `&lt;b&gt;Trainer&lt;/b&gt;`

### Requirement: Claims minimos vinculados al login

Cada token emitido MUST contener `sub`, `client`, `iat` y `exp`. `sub` MUST identificar el username persistido del usuario autenticado y `client` MUST corresponder a la solicitud validada. `iat` y `exp` MUST ser instantes enteros en segundos Unix; `iat` MUST representar la emision y `exp` MUST ser posterior a `iat`. No se exige ni prohibe otro claim.

#### Scenario: Identidad y tiempos presentes

- **WHEN** un login valido emite un token con el reloj fijado en el segundo Unix T
- **THEN** al inspeccionar el token se encuentran sub igual al username autenticado y client igual al recibido
- **AND** iat y exp son enteros, iat es T y exp es mayor que T

### Requirement: Autenticidad comprobable

El token MUST estar firmado y permitir comprobar su autenticidad con el secreto configurado. La verificacion MUST rechazar tokens alterados, firmados con otro secreto o tokens legacy reversibles sin firma verificable. El secreto MUST NOT estar incluido en el contenido recuperable del token.

#### Scenario: Token emitido autentico

- **WHEN** se verifica antes de su expiracion un token recien emitido usando el mismo secreto de prueba configurado para la emision
- **THEN** la verificacion confirma su autenticidad
- **AND** el contenido recuperable no incluye el secreto de prueba

#### Scenario: Alteracion de claims

- **WHEN** se cambia sub, client, iat o exp en un token emitido sin generar una nueva firma valida
- **THEN** la verificacion rechaza cada token alterado

#### Scenario: Secreto diferente

- **WHEN** se verifica un token usando un secreto de prueba diferente al de emision
- **THEN** la verificacion lo rechaza

#### Scenario: Token legacy no autentica

- **WHEN** se verifica un token del formato Base64 concatenado descrito en el baseline, sin firma verificable
- **THEN** no se lo considera un token firmado valido

### Requirement: Coherencia temporal de expiresIn

El servicio MUST mantener `exp - iat = 3600` y `expiresIn = 3600`. Con `now` medido en segundos Unix al generar la respuesta, MUST cumplirse `abs(expiresIn - (exp - now)) <= 1`. La tolerancia maxima de un segundo cubre redondeo o cruce de segundo, no demora de red ni gracia de expiracion.

#### Scenario: Vigencia con reloj fijo

- **WHEN** se emite un token y su respuesta con reloj controlado en T
- **THEN** iat es T, exp es T + 3600 y expiresIn es el entero 3600
- **AND** abs(expiresIn - (exp - now)) es cero

#### Scenario: Limite de tolerancia documentado

- **WHEN** se comprueba una respuesta cuyo token tiene exp igual a T + 3600, expiresIn igual a 3600 y now medido al generarla igual a T + 1
- **THEN** la diferencia absoluta es un segundo y cumple el contrato
- **AND** una respuesta con diferencia de dos segundos incumple el contrato

### Requirement: Expiracion efectiva sin gracia

La verificacion MUST considerar vigente un token autentico solo mientras `now < exp`, y MUST rechazarlo cuando `now >= exp`. Las pruebas de expiracion MUST cubrir el limite exacto y un instante posterior sin depender de esperas reales. Estos resultados no definen un nuevo endpoint ni codigos HTTP para verificacion.

#### Scenario: Instante previo al vencimiento

- **WHEN** se verifica un token autentico con now igual a exp menos un segundo
- **THEN** el token no se rechaza por expiracion

#### Scenario: Vencimiento y tiempo posterior

- **WHEN** se verifica el mismo token con reloj controlado primero en exp y despues en exp mas un segundo
- **THEN** ambas verificaciones rechazan el token por expiracion

### Requirement: Errores de login existentes preservados

El servicio MUST conservar estados, cuerpos y precedencia de validacion existentes: request nulo, username invalido, password invalida y luego credenciales. Con client valido, MUST mantener HTTP 401 para credenciales incorrectas y HTTP 500 con `message` y `exception` para excepciones no controladas, sin redisenar esos contratos.

#### Scenario: Request nulo

- **WHEN** el flujo de login recibe un request nulo
- **THEN** responde HTTP 400 con `{"message":"Request is null"}` y sin token

#### Scenario: Username obligatorio

- **WHEN** username falta, es nulo, vacio o solo espacios, incluso si client tambien falta
- **THEN** responde HTTP 400 con `{"message":"Username is required"}` y sin token

#### Scenario: Password obligatoria

- **WHEN** username es valido y password falta, es nula, vacia o solo espacios, incluso si client tambien falta
- **THEN** responde HTTP 400 con `{"message":"Password is required"}` y sin token

#### Scenario: Credenciales incorrectas

- **WHEN** con client valido se usa username `student` y password incorrecta, o username inexistente `unknown` y una password no vacia
- **THEN** cada caso produce HTTP 401 sin token
- **AND** el cuerpo es respectivamente `{"message":"Invalid user student or password"}` o `{"message":"Invalid user unknown or password"}`

#### Scenario: Error no controlado del login

- **WHEN** ocurre una excepcion no controlada de tipo `java.lang.RuntimeException` sin mensaje durante un login con campos validos
- **THEN** responde HTTP 500 con `{"message":"Unknown error","exception":"java.lang.RuntimeException"}` y sin token

### Requirement: Secreto explicito fuera del sandbox

Fuera del sandbox, el servicio MUST usar `AUTH_TOKEN_SECRET` configurado explicitamente y MUST NOT emitir tokens si falta, es vacio o solo espacios. MUST NOT usar un secreto por defecto fuera del sandbox. Si existe un valor de laboratorio, MUST estar documentado como exclusivo del sandbox y no activarse implicitamente fuera de el.

#### Scenario: Entorno no sandbox sin secreto valido

- **WHEN** se intenta operar fuera del sandbox con AUTH_TOKEN_SECRET ausente, vacio o solo espacios
- **THEN** no se emiten tokens y se informa explicitamente el error de configuracion sin revelar secretos
- **AND** no se sustituye por un secreto de laboratorio

#### Scenario: Secreto configurado

- **WHEN** fuera del sandbox se configura AUTH_TOKEN_SECRET con un valor de prueba valido y se realiza un login valido
- **THEN** el token se puede autenticar usando ese valor de prueba

#### Scenario: Documentacion operativa verificable

- **WHEN** se revisa la documentacion de configuracion del cambio implementado
- **THEN** explica como proporcionar AUTH_TOKEN_SECRET, el comportamiento ante su ausencia y como distinguir sandbox de entornos no sandbox
- **AND** si existe un valor predeterminado, lo identifica y restringe al laboratorio sin incluir secretos reales

### Requirement: Logs sin datos sensibles

El servicio MUST NOT registrar tokens completos, AUTH_TOKEN_SECRET, passwords ni cuerpos de solicitud o respuesta que los expongan. La evidencia de aceptacion MUST incluir logs redactados de login exitoso, fallido, configuracion invalida y verificacion de token expirado, usando exclusivamente datos de prueba.

#### Scenario: Inspeccion de logs con valores identificables

- **WHEN** se capturan logs de logins exitosos y fallidos, configuracion invalida y verificacion de un token expirado con secretos y passwords de prueba identificables
- **THEN** los logs no contienen esos valores ni los tokens emitidos o verificados
- **AND** no aparecen cuerpos de solicitud o respuesta que los expongan

#### Scenario: Evidencia revisable

- **WHEN** se entrega evidencia de validacion del cambio implementado
- **THEN** incluye registros redactados de los flujos anteriores y resultados de escenarios de claims, firma, expiracion y errores de autenticacion
- **AND** la evidencia no incluye secretos reales ni tokens completos
