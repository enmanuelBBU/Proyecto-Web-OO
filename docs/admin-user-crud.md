# CRUD de Usuarios (Admin)

## Introducción

Este documento resume la implementación de la característica **CRUD de Usuarios (Admin)** — el segundo sub-proyecto entregado del backend de Proyecto Minecraft, construido directamente sobre la entrega anterior (Autenticación + Perfil + Items CRUD + Búsqueda). Este cambio aborda dos brechas críticas en el sistema de autenticación y autorización del proyecto:

1. **Sin mecanismo para crear administradores**: no había forma de que un usuario ordinario se convirtiera en administrador, ni forma automatizada ni manual. La auto-registración siempre establecía `rol = "USUARIO"`, y las restricciones de seguridad (implementadas en la entrega anterior) impedían que un usuario se auto-escalara a través de la API o formulario web.

2. **Sin superficies administrativas**: no había verificación de autorización más allá de "¿hay una sesión?" (`ApiAuthInterceptor`), y nada en el sistema distinguía entre una sesión `ADMIN` y una sesión `USUARIO`. No había forma de que un administrador gestionara a otros usuarios.

Esta entrega implementa un mecanismo de bootstrap para administradores basado en configuración (`admin.emails`), rutas de autorización protegidas para administradores, endpoints REST completos para CRUD de usuarios, una interfaz web administrativa, y guardias de auto-protección que impiden que un administrador se bloquee a sí mismo modificando su propia cuenta a través del panel administrativo.

## Qué Cambió

### Bootstrap de Administrador

**Mecanismo**: Nueva propiedad `admin.emails` en `application.properties` (valor separado por comas, vacío por defecto — la característica está inactiva hasta ser configurada). El componente `AdminEmails` (`src/main/java/cl/grupo5/proyectominecraft/config/AdminEmails.java`) expone el método `boolean isAdmin(String email)`, que realiza una verificación de membresía case-insensitive y con espacios recortados.

**Integración en autenticación**:
- `WebAuthController.doRegister()`: después de construir el nuevo `UserProfile`, si el email está en la lista de administradores, se establece `rol = "ADMIN"` directamente (en lugar del default `"USUARIO"`).
- `WebAuthController.doLogin()`: después de un sign-in exitoso, se obtiene el perfil existente. Si existe, aún no es `ADMIN`, y el email está en la lista de administradores, se promociona a `rol = "ADMIN"` y se guarda. En ambos casos, se cachea `session.setAttribute("rol", ...)`, de modo que las verificaciones de autorización posteriores no necesiten una lectura de Firestore por solicitud.

**Ubicación del código**: `src/main/java/cl/grupo5/proyectominecraft/auth/WebAuthController.java` (modificado), `src/main/java/cl/grupo5/proyectominecraft/config/AdminEmails.java` (nuevo).

### Autorización

**Nuevo interceptor**: `AdminAuthInterceptor` (`src/main/java/cl/grupo5/proyectominecraft/config/AdminAuthInterceptor.java`) protege todos los endpoints bajo `/api/admin/**`. Retorna 403 a menos que el atributo de sesión cacheado `rol` sea exactamente `"ADMIN"`. Se registra en `CorsConfig` después del `ApiAuthInterceptor` existente, de modo que una solicitud sin sesión recibe 401 (del primer interceptor, que verifica la existencia de sesión), y una solicitud autenticada pero no-admin recibe 403 (del segundo interceptor).

**Rutas web administrativas**: no utilizan interceptor (manteniendo la convención existente de guardias manuales por método, como en `WebItemController` y `WebProfileController`). Verificación: sin sesión → redirigir a `/login`; con sesión pero no admin → redirigir a `/items`.

**Ubicación del código**: `src/main/java/cl/grupo5/proyectominecraft/config/AdminAuthInterceptor.java` (nuevo), `src/main/java/cl/grupo5/proyectominecraft/config/CorsConfig.java` (modificado).

### Modelo de Datos

**Campo nuevo en `UserProfile`**: La clase `UserProfile` (`src/main/java/cl/grupo5/proyectominecraft/perfil/UserProfile.java`) ganó un campo `uid` (String). No es `@NotBlank` ni requerido — puede estar ausente al construir un `UserProfile` nuevo (por ejemplo en los flujos de creación/auto-guardado) — y se rellena por `UserProfileService` al leer, espejando cómo `Item.id` ya funcionaba. Nótese que el serializador POJO de Firestore sí escribe el getter `getUid()` como cualquier otro campo, por lo que `uid` termina persistido en el documento (de forma redundante, ya que siempre coincide con el id del propio documento) — la propiedad relevante es que no es obligatorio, no que esté ausente del documento.

**Nuevo método de servicio**: `UserProfileService` (`src/main/java/cl/grupo5/proyectominecraft/perfil/UserProfileService.java`) ganó:
- `list()`: retorna cada perfil almacenado con `uid` rellenado.
- `get(uid)`: ahora también rellena `uid` en el valor retornado (anteriormente no lo hacía).

**Ubicación del código**: `src/main/java/cl/grupo5/proyectominecraft/perfil/UserProfile.java` (modificado), `src/main/java/cl/grupo5/proyectominecraft/perfil/UserProfileService.java` (modificado).

### API REST de Administrador

**Nuevo controlador**: `AdminUserController` (`src/main/java/cl/grupo5/proyectominecraft/admin/AdminUserController.java`), montado en `/api/admin/usuarios`:

- **`GET /api/admin/usuarios`** — Lista todos los usuarios.
- **`GET /api/admin/usuarios/{uid}`** — Obtiene un usuario; retorna 404 si es desconocido.
- **`PUT /api/admin/usuarios/{uid}`** — Actualiza `nombre`, `email`, `rol`. Rechaza con 400 si `{uid}` coincide con el uid de la sesión del admin (guardia de auto-protección — verificado PRIMERO, antes de nada más). Rechaza con 400 si `rol` no es exactamente `"USUARIO"` o `"ADMIN"` (incluyendo un `null` JSON explícito, que fue un bug real encontrado durante la revisión — `Set.of(...).contains(null)` lanzaría una `NullPointerException` que de otro modo surfacía como un 500 raw; fijo con una verificación null antes de la verificación de membresía de conjunto).
- **`DELETE /api/admin/usuarios/{uid}`** — Misma verificación de auto-protección primero, luego elimina la cuenta de Firebase Auth y el perfil de Firestore, en ese orden (Auth primero). Este orden es importante: si la eliminación de Firestore falla después de que Auth tenga éxito, la cuenta ya es inalcanzable (no se puede iniciar sesión), por lo que el documento Firestore huérfano es inofensivo; el orden inverso dejaría una cuenta viva e iniciable con ningún perfil.

**Ubicación del código**: `src/main/java/cl/grupo5/proyectominecraft/admin/AdminUserController.java` (nuevo).

### Panel Web Administrativo

**Nuevos controladores y rutas**: `WebAdminUserController` (`src/main/java/cl/grupo5/proyectominecraft/admin/WebAdminUserController.java`), montado en `/admin/usuarios`. Nuevas plantillas Thymeleaf: `admin-usuarios.html` y `admin-usuario-edit.html`.

- **`GET /admin/usuarios`** — Tabla de todos los usuarios (nombre, email, rol badge, acciones editar/eliminar). Requiere sesión admin.
- **`GET /admin/usuarios/{uid}/edit`** — Formulario de edición (rol como `<select>` con opciones `USUARIO`/`ADMIN`). Si `{uid}` es la cuenta del propio admin, redirige a `/admin/usuarios` sin renderizar.
- **`POST /admin/usuarios/{uid}/update`** — Misma verificación de auto-protección; si `rol` es inválido, rerenderiza el formulario de edición con un error en español ("Rol inválido.") en lugar de guardar.
- **`POST /admin/usuarios/{uid}/delete`** — Misma verificación de auto-protección, luego elimina (Auth primero, luego Firestore), protegido por un diálogo `confirm()` en el navegador.

Nota: editar el `email` de un usuario desde el panel solo actualiza el perfil en Firestore, no la cuenta de Firebase Auth — el usuario seguirá iniciando sesión con su email original hasta que lo cambie él mismo (si existe esa función) o un admin lo actualice también en Firebase Auth por otro medio.

**Ubicación del código**: `src/main/java/cl/grupo5/proyectominecraft/admin/WebAdminUserController.java` (nuevo), `src/main/resources/templates/admin-usuarios.html` (nuevo), `src/main/resources/templates/admin-usuario-edit.html` (nuevo).

### Navegación

**Links de navegación administrativos**: Los templates `items.html` y `perfil.html` ganaron un link condicional `<a th:if="${isAdmin}" href="/admin/usuarios">Admin</a>`, impulsado por un nuevo atributo de modelo `isAdmin` (`"ADMIN".equals(session.getAttribute("rol"))`).

**Ubicación del código**: `src/main/java/cl/grupo5/proyectominecraft/items/WebItemController.java` (modificado — método `list()`), `src/main/java/cl/grupo5/proyectominecraft/perfil/WebProfileController.java` (modificado — método `show()`), `src/main/resources/templates/items.html` (modificado), `src/main/resources/templates/perfil.html` (modificado).

## Mapeo de Rutas

### API REST Administrativo

| Método | Ruta | Descripción | Autorización |
|---|---|---|---|
| GET | `/api/admin/usuarios` | Lista todos los usuarios | Sesión admin requerida (403 si no) |
| GET | `/api/admin/usuarios/{uid}` | Obtiene un usuario específico | Sesión admin requerida (403 si no) |
| PUT | `/api/admin/usuarios/{uid}` | Actualiza nombre/email/rol de un usuario | Sesión admin requerida (403 si no); 400 si self-targeting; 400 si rol inválido |
| DELETE | `/api/admin/usuarios/{uid}` | Elimina un usuario (Auth + Firestore) | Sesión admin requerida (403 si no); 400 si self-targeting |

### Web Administrativo

| Método | Ruta | Descripción | Autorización |
|---|---|---|---|
| GET | `/admin/usuarios` | Lista de usuarios (tabla HTML) | Sesión admin requerida; redirige a `/items` si no |
| GET | `/admin/usuarios/{uid}/edit` | Formulario de edición de usuario | Sesión admin requerida; redirige si self-targeting |
| POST | `/admin/usuarios/{uid}/update` | Procesa actualización de usuario | Sesión admin requerida; redirige a `/admin/usuarios` si self-targeting; rerenderiza si nombre/email/rol inválido |
| POST | `/admin/usuarios/{uid}/delete` | Procesa eliminación de usuario | Sesión admin requerida; redirige a `/admin/usuarios` si self-targeting |

## Notas de Seguridad

### Guardia de Auto-Protección

Un administrador **no puede editar ni eliminar su propia cuenta** a través del panel administrativo. Esto está verificado como la primera comprobación en `PUT`/`DELETE` (REST) y `update`/`delete` (web), antes de cualquier otra lógica. Estos métodos retienen una ruta separada (`/perfil`) para modificar la cuenta propia, evitando confusión y previniendo un auto-bloqueo accidental. La *condición* del verificador es idéntica en `AdminUserController` (API REST) y `WebAdminUserController` (web) — `uid.equals(sessionUid)` — pero la respuesta difiere: el controlador REST retorna HTTP 400, mientras que el controlador web redirige a `/admin/usuarios`. Misma regla subyacente, distinta forma de respuesta según la superficie (API vs. formulario web).

### Bug Encontrado y Fijo: Null Rol en JSON

Inicialmente, la validación de `rol` en `PUT /api/admin/usuarios/{uid}` no verificaba `null` explícitamente. Un cliente podía enviar `"rol": null` en el cuerpo JSON, lo que causaría que `Set.of("USUARIO", "ADMIN").contains(null)` lanzara una `NullPointerException`. Esto surfacía como un error 500 raw. Se añadió un guardia `if (rol == null)` antes de la verificación de membresía de conjunto, retornando 400 en su lugar.

### Orden de Eliminación: Auth Primero, Luego Firestore

Cuando un administrador elimina a otro usuario (o un usuario se auto-elimina), el flujo es:
1. Eliminar la cuenta de Firebase Auth (hace que la cuenta sea inalcanzable)
2. Eliminar el documento de perfil de Firestore

Si el paso 1 tiene éxito pero el paso 2 falla, la cuenta está ya fuera de alcance (no se puede iniciar sesión), y el documento Firestore huérfano es un artefacto inofensivo que podría limpiarse manualmente o en una futura tarea de limpieza. El orden inverso sería peor: si Auth falla después de que Firestore tenga éxito, dejaría una **cuenta viva e iniciable con ningún perfil**, una inconsistencia mucho más rota. Este orden es coherente con el flujo de auto-eliminación de la entrega anterior.

### Atributo de Sesión Cacheado

El `rol` se cachea como un atributo de sesión (`session.setAttribute("rol", ...)`) en el momento del login/registro. Esto significa que si un administrador cambia el `rol` de otro usuario mientras ese usuario tiene una sesión activa, ese usuario verá su rol antiguo hasta que su sesión expire o se cierre manualmente. Esta es una brecha de consistencia aceptada para el alcance de este proyecto — el patrón existente ya cachea `uid` y `email` de la misma manera, y no hay verificación de cambios de rol por solicitud.

### Riesgo Conocido: Sin Protección CSRF

Ninguna ruta de la aplicación (incluyendo las nuevas rutas admin) tiene protección CSRF — no hay Spring Security ni tokens en los formularios. Esto ya era cierto antes de este cambio (`/logout`, `/perfil/eliminar`, `/items/{id}/delete` tienen la misma exposición), pero este cambio es el primero en poner la eliminación irreversible de la cuenta de OTRO usuario detrás de un formulario sin protección: un admin autenticado que visite una página maliciosa podría ser inducido a eliminar la cuenta de cualquier usuario sin darse cuenta. Se deja registrado como riesgo conocido en lugar de implementarse aquí, ya que una solución real (tokens CSRF, o adoptar Spring Security) es un cambio transversal a toda la aplicación, no específico de este sub-proyecto.

## Pruebas

**Cobertura de pruebas**: 67 pruebas en total a través de 12 clases de prueba, todas pasando.

### Clases de Prueba y Cobertura

| Clase de Prueba | Pruebas | Cobertura |
|---|---|---|
| `ProyectominecraftApplicationTests` | 1 | Context load (Spring Boot smoke test) |
| `AdminUserControllerTest` | 12 | Listar usuarios (incluye `uid` en la respuesta JSON), obtener uno (404 si el uid es desconocido), actualizar (happy path que cambia el rol del target, 400 si self-targeting, 400 si rol inválido), eliminar (happy path Auth+Firestore en orden seguro, 400 si self-targeting) |
| `WebAdminUserControllerTest` | 11 | Listar usuarios, formulario de edición (redirect si self-targeting), actualización (happy path que persiste nombre/email/rol, redirige a `/admin/usuarios` si self-targeting, rerenderiza si nombre en blanco/email inválido/rol inválido), eliminación (happy path Auth+Firestore en orden seguro, redirige a `/admin/usuarios` si self-targeting) |
| `WebAuthControllerTest` | 6 | Bootstrap: registrar con email admin crea perfil ADMIN y setea `rol=ADMIN` en sesión; login con email admin promueve USUARIO a ADMIN; login con email no-admin mantiene USUARIO sin cambios |
| `AdminAuthInterceptorTest` | 3 | 403 sin sesión en absoluto; 403 con sesión pero rol=USUARIO; acceso permitido con rol=ADMIN |
| `AdminEmailsTest` | 4 | Verificación case-insensitive de email, manejo de espacios, lista vacía (siempre false), parsing de CSV |
| `ItemApiAuthTest` | 5 | 401 sin sesión en GET/POST/PUT/DELETE; 200 con sesión en GET |
| `ItemControllerTest` | 3 | 400 en nombre en blanco; 404 en PUT/DELETE de id desconocido |
| `ItemServiceTest` | 4 | Lógica pura de filtro (búsqueda de texto a través de nombre/categoria, bandera esMateriaPrima, paso de sin-criterios); defaults de Item |
| `WebItemControllerTest` | 7 | Validación de nombre en blanco en crear y actualizar (incluye que `isAdmin` se preserve en el re-render de `create()`); redirigir a `/items` al editar/actualizar un item eliminado; nav isAdmin visible cuando rol=ADMIN |
| `UserProfileControllerTest` | 8 | 401 sin sesión en GET/PUT/DELETE; 200 con sesión en GET retornando el perfil almacenado; preservación de rol; eliminación de cuenta |
| `WebProfileControllerTest` | 3 | Ruta happy path de eliminación de cuenta; redirigir cuando no autenticado; nav isAdmin visible cuando rol=ADMIN |

### Ejecutar Pruebas

```bash
gradlew.bat test        # Ejecutar todas las pruebas unitarias/integración
gradlew.bat build       # Build completo (pruebas + asamblea)
```

Ambos comandos se completan exitosamente con las 67 pruebas pasando.

## Cambios Entre Proyectos Existentes

**Cambios de configuración requerida en pruebas existentes**: El constructor de `CorsConfig` ahora requiere la nueva dependencia de bean `AdminAuthInterceptor`, lo que habría roto tres clases de prueba existentes que ya construían `CorsConfig` vía `@Import({ApiAuthInterceptor.class, CorsConfig.class})`:
- `ItemApiAuthTest`
- `ItemControllerTest`
- `UserProfileControllerTest`

Todas tres fueron actualizadas durante el mismo paso de implementación para añadir `AdminAuthInterceptor.class` a sus listas `@Import`.

## Brecha Conocida de Verificación

**Importante**: El checklist completo de prueba manual interactiva del navegador **no** fue realizado como parte de esta implementación. Específicamente:

- Configuración de un verdadero valor `admin.emails` en `application.properties`
- Registración a través del formulario real `/register` con un email administrativo
- Inicio de sesión a través del formulario real `/login` con una cuenta administrativo
- Click a través de cada acción del panel administrativo (lista, editar, eliminar)
- Verificación del guardia de auto-protección en un navegador real (intentar editar/eliminar la propia cuenta admin)
- Confirmación de que los nuevos links de navegación Admin aparecen en `items.html` y `perfil.html`

Solo fueron ejecutadas pruebas automatizadas y `gradlew.bat build`. 

**Recomendación**: Antes de considerar esta característica completamente verificada end-to-end, el propietario del proyecto debe caminar a través del checklist de prueba de humo del navegador en `openspec/changes/admin-user-crud/plan.md`, Task 8, Paso 3. Esto confirmará que:
- La bootstrap de administrador funciona como se espera cuando se configura `admin.emails`
- El inicio de sesión promociona correctamente usuarios existentes USUARIO a ADMIN
- El panel administrativo es accesible solo para sesiones ADMIN
- El guardia de auto-protección previene que un admin se modifique a sí mismo
- La eliminación de usuarios elimina correctamente tanto la cuenta de Firebase Auth como el perfil de Firestore
- Los links de navegación aparecen y desaparecen correctamente basado en `rol`

## Explícitamente Fuera de Alcance

Estos elementos fueron explícitamente excluidos por la propuesta original, no son brechas:

- **Crear nuevas cuentas de usuario desde el panel administrativo**: La registración vía `/register` permanece como la única ruta de creación de cuenta. Este cambio es Leer + Actualizar + Eliminar de usuarios ya registrados.
- **Verificación de "último administrador"**: nada impide que los administradores se degraden mutuamente a `USUARIO`, ya que la única restricción es que cada uno no puede tocar su propia cuenta. El guardia de auto-protección es la única mitigación contra un bloqueo total, y es limitada: un administrador podría pedirle a otro que lo degrade a él, o degradar a todos los demás administradores mientras él mismo queda como el único que conserva el rol.

## Qué Sigue

Los siguientes sub-proyectos están diferidos y construirán sobre esta base:

### Completado
- **Auth + Perfil + Items CRUD + Búsqueda** (entrega anterior)
- **CRUD de Usuarios (Admin)** (esta entrega)

### Planeado (en orden de ejecución)

1. **Gestión de Materiales** — UI dedicada para la vista filtrada `esMateriaPrima` y operaciones en lote; actualmente solo disponible como un filtro en el catálogo de items.
2. **Gestión de Inventario** — inventario por usuario con cantidades; cálculos de "crafteable desde inventario".
3. **Validación y almacenamiento de recetas** — definir recetas de crafteo; validar contra inventario.
4. **Calculadora de crafteo** — UI interactiva para planificar cadenas de crafteo; mostrar cantidades de salida.
5. **Grilla 3x3** — UI de grilla de crafteo estilo Minecraft.
6. **CRUD de Proyectos de construcción** — crear, leer, actualizar, eliminar proyectos de construcción; asignar items y usuarios.
7. **Estados de Proyecto** — estados de flujo de trabajo (draft, active, completed, archived); transiciones de máquina de estados.
8. **Generar Documento (PDF)** — exportar proyecto/inventario/recetas a PDF.
9. **Modelo 3D** — visor 3D para items/proyectos (modelos Minecraft); integración opcional con un motor 3D.
10. **Notificación de Toast** — notificaciones toast/snackbar ligeras para mensajes success/error/info (actualmente usando redirecciones de Spring y atributos de error de plantilla).

Cada sub-proyecto seguirá el mismo proceso de implementación y revisión, con docs de diseño y cobertura de pruebas.

## Notas de Implementación

- **Sin cambios disruptivos al contrato de API REST**: todos los cambios de ruta son explícitos (nuevas rutas bajo `/api/admin/**`, no cambios a rutas existentes de usuario). El cambio de modelo de datos (nuevo campo `uid` en `UserProfile`, no requerido — no lleva `@NotBlank`) solo afecta a nuevas lecturas/retornos — los documentos Firestore existentes sin este campo son manejados correctamente. El campo sí se persiste en los documentos escritos a partir de ahora (el serializador POJO de Firestore escribe `getUid()` como cualquier otro getter); simplemente no es obligatorio proveerlo al construir un `UserProfile`.
- **Documentos de Firestore sin el nuevo campo `uid`**: documentos creados antes de este cambio no tienen `uid`. Cuando se retornan a través de `UserProfileService.get()` o `list()`, se rellenan con el id del documento. El servicio maneja esto estableciendo explícitamente `p.setUid(doc.getId())` antes de retornar.
- **Historial de Git**: Todos los cambios permanecen en el árbol de trabajo, sin commitear. El propietario del proyecto los comiteará manualmente.
