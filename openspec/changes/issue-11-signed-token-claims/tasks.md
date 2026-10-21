## 1. Caracterizar el comportamiento actual

- [ ] Configurar los tests REST para usar una base SQLite temporal mediante `AUTH_DB_URL`, establecida antes de inicializar `LegacyDatabase`; comprobar que no modifican `data/workshop.db`.
- [ ] Ampliar la caracterización REST del login exitoso para student y trainer: estado, tipos y valores protegidos de la respuesta, `expiresIn`, `debug` y campos JSON desconocidos.
- [ ] Caracterizar los errores HTTP 400 y 401 existentes, sus cuerpos exactos, la ausencia de token en errores y la precedencia actual de validaciones.
- [ ] Añadir pruebas directas de `AuthResource` para request Java nulo y excepción no controlada sin mensaje; distinguir estos casos del JSON malformado procesado por REST.
- [ ] Probar los datos del usuario y el escape HTML con un doble pequeño de `LegacyDatabase` y un `UserRecord` sintético, sin alterar datos persistidos.
- [ ] Caracterizar que `client` ausente se acepta actualmente y documentar en la prueba que ese comportamiento será reemplazado; comprobar la estructura legacy del token sin imprimir ni incluir su contenido sensible en fallos o logs.
- [ ] Comprobar que `/q/health` sigue siendo público, verificando el estado y la estructura de checks disponible sin fijar checks vacíos como garantía.
- [ ] Ejecutar `mvn "-Dtest=AuthResourceTest" test` y dejar verde la caracterización antes de cambiar validación, token u orquestación.

## 2. Preparar componentes internos de autenticación

- [ ] Extraer la orquestación del login a `AuthenticationService`, manteniendo `LegacyDatabase`, resultados tipados y el contrato HTTP actual; confirmar con la caracterización que no cambió el comportamiento.
- [ ] Incorporar una versión concreta y compatible con Java 21 de Nimbus JOSE + JWT, tras revisar la dependencia y sus transitivas; no actualizar Quarkus ni usar versiones dinámicas.
- [ ] Implementar la configuración de `AUTH_TOKEN_SECRET` validada al inicio: exigir valor explícito de al menos 32 bytes UTF-8, sin valor predeterminado ni transformación, y fallar con diagnóstico saneado.
- [ ] Proveer un `Clock` UTC en ejecución y controlable en tests; definir una única duración de 3600 segundos para emisión y respuesta.
- [ ] Implementar `TokenService` con JWT compacto HS256 y claims tipados `sub`, `client`, `iat`, `exp` y `role`; excluir secreto y password del payload.
- [ ] Implementar la verificación interna del token con resultado tipado; validar algoritmo, formato, firma y claims en ese orden, rechazar tokens legacy y no agregar endpoint ni filtro HTTP.
- [ ] Añadir pruebas unitarias del token para claims y tipos, firma con clave sintética, clave incorrecta, alteraciones de claims, formato/algoritmo no permitido, `none`, claims incompletos y rechazo del formato legacy.
- [ ] Añadir pruebas con reloj controlado para `iat`, `exp`, duración de 3600 segundos, expiración en `exp - 1`, `exp` y `exp + 1`, y coherencia de `expiresIn` con tolerancia máxima de un segundo, sin `sleep`.

## 3. Integrar el contrato aprobado

- [ ] Validar `client` estrictamente en el DTO y en el orden request, username, password y client: rechazar ausente, nulo, blanco y tipos no textuales con los mensajes aprobados; conservar sin trim los valores válidos y evitar consulta de credenciales ante client inválido.
- [ ] Integrar conjuntamente `client` obligatorio y emisión firmada en el flujo del login; preservar HTTP 200, la estructura de usuario, escape HTML, `debug` y los errores HTTP existentes, y retirar el uso y la emisión de `LegacyTokenUtil` sin fallback legacy.
- [ ] Mantener la coherencia temporal de la respuesta a partir del resultado de emisión; ante diferencia superior a un segundo, no devolver éxito incoherente y usar la frontera existente de error.
- [ ] Sanear los diagnósticos SQLite del flujo afectado sin registrar SQL, parámetros ni excepciones completas; emitir solo eventos acotados que no incluyan token, secreto, password ni bodies.
- [ ] Documentar cómo generar y configurar `AUTH_TOKEN_SECRET`, los perfiles sandbox, los requisitos de longitud/entropía y el cambio incompatible de `client` y formato del token, sin prometer confidencialidad del payload JWT.

## 4. Validar regresiones y privacidad

- [ ] Probar por REST los casos de `client` ausente, null, vacío, blanco, número, booleano, objeto y array; comprobar también preservación del valor válido exacto y precedencia de username/password.
- [ ] Probar el contrato de login para credenciales válidas e inválidas y confirmar que no se expone token en las respuestas de error ni cambia el acceso público a login y health.
- [ ] Probar el arranque con secreto válido y el rechazo de secreto ausente, vacío, blanco o corto en producción y perfiles no sandbox; confirmar que dev/test requieren secreto explícito y no activan un fallback.
- [ ] Capturar logs de éxito, credenciales inválidas, error de configuración, fallo SQLite y rechazo de token; comprobar con marcadores sintéticos que no contienen token, secreto ni password, sin incluir esos valores en la evidencia.
- [ ] Ejecutar `mvn verify`, revisar el informe JaCoCo y confirmar cobertura de al menos 85% del código modificado, además de conservar los controles existentes.
