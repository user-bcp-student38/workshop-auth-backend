# Proposal

## Why

El [Issue #11: HU-01 - Token firmado con expiracion y claims minimos](https://github.com/user-bcp-student38/workshop-auth-backend/issues/11) compromete tokens verificables con identidad, cliente y vigencia, configuracion explicita del secreto y ausencia de datos sensibles en logs. Importa porque el token actual es reversible, contiene el secreto y no permite comprobar autenticidad; ademas, `client` se acepta pero se ignora.

## What Changes

### Alcance comprometido

- Emitir tokens firmados con `sub`, `client`, `iat` y `exp`; vincular `sub` al usuario autenticado y `client` al valor recibido y validado.
- **BREAKING**: hacer `client` obligatorio y rechazar con HTTP 400 su ausencia, valor nulo, vacio o compuesto solo por espacios. Decision confirmada con el solicitante: el Issue exige validarlo y reflejarlo, en contraste con su caracter opcional en el baseline. No se agrega una lista de clientes permitidos ni un valor predeterminado.
- **BREAKING**: sustituir el contenido reversible del token documentado por uno firmado. No comprometer compatibilidad del formato legacy ni aceptar ese formato como prueba de autenticidad.
- Conservar una vigencia de 3600 segundos y comprobar `expiresIn` frente a `exp - now` con tolerancia maxima de un segundo, limitada al redondeo temporal. Esa tolerancia no concede gracia a tokens expirados.
- Configurar el secreto mediante `AUTH_TOKEN_SECRET`; impedir emision sin secreto explicito fuera del sandbox. Cualquier valor de laboratorio debe estar identificado y restringido al sandbox.
- No registrar tokens, secretos ni valores sensibles. Exigir evidencia de claims, expiracion, credenciales invalidas y logs redactados, ademas de documentacion de la configuracion.

### Comportamiento que debe preservarse

Baseline: [docs/ENDPOINTS.md](../../../docs/ENDPOINTS.md). Las unicas excepciones intencionales son la obligatoriedad de `client`, su incorporacion al token y la sustitucion de la representacion legacy del token.

- `POST /auth/login` sigue siendo publico, recibe JSON y mantiene los campos `username` y `password` y sus validaciones.
- Con credenciales validas y `client` valido, mantiene HTTP 200, `token` string, `expiresIn` integer igual a 3600, `user.username`, `user.displayName` escapado en HTML, `user.role` persistido y `debug: "authenticated-with-legacy-flow"`. La marca se conserva por compatibilidad, aunque cambie la emision.
- Conserva HTTP 400 y los mensajes documentados para request nulo, username invalido y password invalida; HTTP 401 y su mensaje para credenciales invalidas; y el contrato HTTP 500 documentado para excepciones no controladas del login. No se aprovecha este cambio para redisenar esos errores.
- `GET /q/health` sigue sin autenticacion, sin cambios en sus estados ni en su respuesta dependiente del runtime.
- JSON malformado, Content-Type incorrecto y contenido exacto de health checks permanecen sujetos a las limitaciones del baseline; no se inventan garantias nuevas.

### Fuera de alcance

- Diseno tecnico, eleccion de HMAC frente a una libreria JWT, algoritmos, librerias, estructura interna y pasos de implementacion. La decision exigida por el Issue queda pendiente de una fase posterior, no resuelta aqui.
- `design.md`, `tasks.md`, implementacion, ejecucion de pruebas funcionales o cambios del contrato baseline en esta entrega.
- Nuevos endpoints de validacion o renovacion, logout, revocacion, autorizacion por rol y cambios a consumidores de otros repositorios.
- Refactorizacion general, persistencia, contrasenas, CORS, dependencias y correcciones de seguridad ajenas a lo comprometido en #11.

## Capabilities

### New Capabilities

- `authentication-tokens`: contrato observable de emision y verificacion de tokens firmados, claims, vigencia, cliente obligatorio, secreto y privacidad de logs, preservando la respuesta de login.

### Modified Capabilities

Ninguna: el inventario actual de `openspec/specs/` no contiene capacidades. La nueva spec formaliza tambien las invariantes existentes necesarias para este cambio; no implica que el login sea nuevo.

## Impact

- Superficie afectada: emision de `token` en `POST /auth/login`, validacion de `client`, configuracion y documentacion de `AUTH_TOKEN_SECRET`, pruebas y evidencia de logs.
- Consumidores: deben enviar `client` valido y no depender del formato reversible. No se leen ni modifican otros repositorios.
- Discrepancia de origen: #11 afirma que ya existen HMAC y lectura de `AUTH_TOKEN_SECRET`; en esta rama se observa Base64 con secreto embebido, coherente con el baseline. Se toma el Issue como objetivo comprometido, no como evidencia de una implementacion existente.
- Riesgo de compatibilidad: solicitudes que omiten `client` dejaran de funcionar y los lectores del token legacy necesitaran adaptacion fuera de esta entrega. Se conserva el envoltorio de respuesta, no la codificacion interna.
- Validacion futura exigida: escenarios de la spec con reloj controlado, verificacion de firma y expiracion sin agregar endpoints, comprobacion del contrato HTTP y revision de logs con valores de prueba identificables. La tolerancia temporal de un segundo es una precision de aceptacion de esta propuesta, no un valor impuesto por el Issue.
- Esta entrega materializa solo propuesta y specs bajo el schema estandar `spec-driven`; el diseno y las tareas permanecen pendientes por solicitud expresa.
