# Diseño: 5 funcionalidades base (Mundos, Horno, Colaborativos, Historial de Crafteo, Auditoría)

Fecha: 2026-11-23 · Rama: `nuevas-funcionalidades-base`

## Intención

La próxima entrega del curso exige 10 funcionalidades nuevas. Este trabajo
implementa las 5 que sirven de **cimiento** para las otras; un compañero de
equipo construye después las 5 restantes encima:

| Base (este diseño) | Encima (compañero, después) |
|---|---|
| 1. CRUD de Mundos / Servidores | 2. Calculadora Nether ↔ Overworld (portales dentro de un mundo) |
| 8. Recetas de Horno y Fundición | 3. Lista de faltantes (usa la calculadora con horno) |
| 6. Proyectos Colaborativos | 9. Notas y comentarios (usa los miembros del proyecto) |
| 5. Historial de Crafteo | 4. Dashboard de estadísticas (lee proyectos, recetas, historial, inventario) |
| 7. Log de Auditoría de Admin | 10. Previsualización de ítems en Steve (independiente) |

Éxito = las 5 funcionan de punta a punta (web + API REST donde ya existe API
para esa área), con tests, y dejan modelos de datos claros sobre los que el
compañero pueda construir sin rehacerlos.

## Metas

- Cada funcionalidad completa: modelo, servicio, controlador web (Thymeleaf),
  API REST cuando el área ya tiene API, enlace en el nav, tests.
- Seguir las convenciones existentes: Spring Boot 4 + Lombok, Firestore vía
  `com.google.cloud.firestore.Firestore`, sesión HTTP (`uid`, `nombre`, `rol`),
  chequeos de sesión manuales por método, toasts vía flash attributes, lógica
  pura en métodos `static` testeables sin Firestore, `@WebMvcTest` +
  `@MockitoBean` para controladores.
- TDD (regla del repo: `openspec/config.yaml` → `strict_tdd: true`,
  `gradlew.bat test`).

## No-metas

- Las otras 5 funcionalidades (las hace el compañero).
- El requerimiento transversal de simplificar el frontend.
- Combustible del horno (carbón, etc.): la receta de horno modela solo
  entrada → salida.
- Varias recetas para el mismo ítem.
- Flujo de invitaciones con aceptar/rechazar; notificaciones.
- Spring Security / AOP.

## Anti-metas (sería un fracaso aunque "funcione")

- Que un usuario vea o modifique un proyecto privado ajeno por la web o por la API.
- Que un crafteo quede registrado en el historial sin haber descontado el
  inventario, o al revés.
- Que una falla al escribir la auditoría rompa la acción del usuario.
- Que los ítems y proyectos existentes en Firestore dejen de cargar
  (todos los campos nuevos deben tolerar `null`).

## Restricciones

- Firestore sin migraciones: los documentos existentes no tienen los campos
  nuevos; el código los trata con valores por defecto.
- Los listados actuales cargan la colección completa y filtran en memoria
  (`ProyectoService.filter`, `ItemService.filter`); se mantiene ese estilo
  (volumen pequeño, lógica testeable).
- Java 17, sin dependencias nuevas en Gradle.

---

## 1. CRUD de Mundos / Servidores

### Modelo

Colección `mundos`, id autogenerado por Firestore.

```java
@Data class Mundo {
  String id;
  String uid;            // dueño
  @NotBlank String nombre;
  @NotBlank String version;      // texto libre, ej. "1.21.4"
  String modoJuego;      // SUPERVIVENCIA | CREATIVO | AVENTURA | ESPECTADOR | HARDCORE
  String semilla;        // opcional, texto (las semillas pueden ser texto o número)
  String fechaCreacion;  // ISO-8601
}
```

`ModoJuego` es un enum con `fromString` igual que `EstadoProyecto`.

`Proyecto` gana: `String mundoId`, `Integer x`, `Integer y`, `Integer z`
(todos opcionales).

### Reglas

- Un usuario solo ve/edita/elimina sus propios mundos (admin: todos).
- Validación: nombre y versión obligatorios, modo válido.
- Coordenadas: solo se guardan si hay `mundoId`; `y` en [-64, 320];
  `x`,`z` en [-30_000_000, 30_000_000]. Si hay mundo pero no coordenadas, se
  permite (proyecto "en ese mundo, ubicación por definir").
- El mundo asignado a un proyecto debe pertenecer al **creador del proyecto**
  (así un miembro editor ve y elige entre los mundos del creador).
- Eliminar un mundo con proyectos asignados se **rechaza** con mensaje
  ("El mundo tiene N proyecto(s) asignado(s)").

### Superficies

- Web: `/mundos` (lista + formulario de creación), `/mundos/{id}/edit`,
  `POST /mundos`, `POST /mundos/{id}/update`, `POST /mundos/{id}/delete`.
  El detalle del mundo lista los proyectos visibles asignados a él.
- API: `/api/mundos` (GET, POST), `/api/mundos/{id}` (GET, PUT, DELETE),
  protegida por `ApiAuthInterceptor` (registrar el patrón en `CorsConfig`).
- Formulario de proyecto (crear/editar): selector de mundo (mundos del
  creador) + campos X/Y/Z. Detalle de proyecto: muestra mundo y coordenadas.
- Nav: "Mis Mundos" en el grupo Proyectos.

### Para el compañero (feature 2)

Los portales serán una subcolección o lista dentro de `Mundo`; este diseño no
los crea pero el modelo no lo impide.

---

## 8. Recetas de Horno y Fundición

### Modelo

`Item` gana:

```java
String tipoReceta;    // "CRAFTEO" (default si null) | "HORNO"
String recetaHorno;   // id del ítem de entrada; solo si tipoReceta == HORNO
```

### Reglas (`ItemService`)

- `tipoReceta` null o vacío ⇒ `CRAFTEO` (compatibilidad con ítems existentes).
- `HORNO`: `recetaHorno` obligatorio, `recetaMatriz` debe venir vacía, el
  ítem no puede ser materia prima, la entrada no puede ser el mismo ítem.
  `ingredientesParaCalculo = [{recetaHorno, 1}]`.
- `CRAFTEO`: `recetaHorno` se limpia a null; comportamiento actual intacto.
- Validación de existencia y de ciclos cubre ambas recetas: el
  `RecetaLookup` pasa a devolver **los ids de ingredientes** del ítem
  (de `recetaMatriz` o `recetaHorno`), no solo la matriz. Ej. ciclo:
  `lingote ← horno(mineral)` y `mineral ← crafteo(lingote…)` se rechaza.

### Calculadora

`CraftingCalculatorService.expandRequirements` ya recorre
`ingredientesParaCalculo`, así que resuelve horno sin cambios de algoritmo.
Cambios visibles:

- `MaterialRequirement` no cambia. `CraftingPlan` gana `tipoReceta` del ítem
  objetivo para que la vista diga "Fundir" en vez de "Craftear".
- El botón de acción usa el mismo endpoint `/calculadora/craftear`.

### UI

`item-edit.html` y el formulario de creación en `items.html`: selector
"Mesa de crafteo / Horno". Con Horno se oculta la grilla 3x3 y se muestra una
sola casilla de entrada (reutiliza el buscador de `recipe-item-list.js`).
El catálogo muestra un badge "Horno" en ítems con esa receta.

---

## 6. Proyectos Colaborativos (proyectos privados)

### Modelo

```java
@Data class Miembro {
  String uid;
  String nombre;
  String email;
  String permiso;        // LECTURA | EDICION
  String fechaIngreso;
}
```

`Proyecto` gana `List<Miembro> miembros` y `List<String> miembroUids`
(espejo de los uid, mantenido por el servicio; deja abierta una consulta
`array-contains` a futuro).

### Reglas de acceso (métodos `static` en `ProyectoService`)

| Acción | Creador | Miembro EDICION | Miembro LECTURA | Admin | Otro |
|---|---|---|---|---|---|
| Ver (lista, detalle, PDF) | ✓ | ✓ | ✓ | ✓ | ✗ |
| Editar datos, estados, mundo/coords | ✓ | ✓ | ✗ | ✓ | ✗ |
| Gestionar miembros | ✓ | ✗ | ✗ | ✓ | ✗ |
| Eliminar proyecto | ✓ | ✗ | ✗ | ✓ | ✗ |

- `canView(p, uid, rol)`, `canEdit(p, uid, rol)` (reemplaza el uso de
  `canUserModify` para editar/estados), `canManage(p, uid, rol)`
  (miembros y eliminar). `canUserModify` se elimina o queda como alias de
  `canManage`; los tests existentes se ajustan.
- `list(...)` filtra con `visiblePara(proyectos, uid, rol)` antes de los
  filtros actuales. El filtro `creadorUid` sigue funcionando.
- Proyecto no visible ⇒ web redirige a `/proyectos` con toast de error;
  API responde **404** (no revela existencia).
- `update` preserva `miembros`/`miembroUids` del documento actual (no se
  editan desde el formulario de datos), igual que preserva el creador.
- La página de reportes PDF y `ProyectoPdfController` solo ofrecen/generan
  proyectos visibles.

### Invitar / quitar / cambiar permiso

- Invitar por **email de un usuario registrado** (`UserProfileService.findByEmail`,
  búsqueda en memoria sobre `list()`, sin distinguir mayúsculas).
  Membresía inmediata.
- Errores: email no registrado; invitarse a uno mismo / al creador;
  permiso inválido. Invitar a alguien que ya es miembro actualiza su permiso.
- Quitar miembro y cambiar permiso: solo creador/admin.
- Web: sección "Miembros" en `proyecto-detalle.html` (lista con permiso;
  formulario de invitación y botones solo si `canManage`).
  `POST /proyectos/{id}/miembros`, `POST /proyectos/{id}/miembros/{uid}/permiso`,
  `POST /proyectos/{id}/miembros/{uid}/delete`.
- API: `GET/POST /api/proyectos/{id}/miembros`,
  `PUT/DELETE /api/proyectos/{id}/miembros/{uid}`.
- Lista de proyectos: badge "Compartido · Lectura/Edición" en proyectos
  donde el usuario es miembro.

### Ids de proyecto (arreglo incluido)

Con proyectos privados, el id por slug global revelaría que existe un
proyecto ajeno con el mismo nombre. Se cambia:

- Proyectos **nuevos**: id autogenerado por Firestore
  (`collection("proyectos").document()`), ya no `Slugs.slugify(nombre)`.
- Unicidad del nombre **solo entre los proyectos del mismo creador**
  (comparación sin mayúsculas/acentos vía `Slugs.slugify`), en crear y en
  editar (excluyendo el propio proyecto). Error: "Ya tienes un proyecto
  llamado …". `ProyectoAlreadyExistsException` se mantiene con ese mensaje.
- Proyectos **existentes** conservan su id slug; no hay migración y sus URLs
  siguen funcionando (se buscan por id de documento).
- Ítems no cambian: su id slug es intencional (catálogo global, recetas
  referencian slugs).

### Para el compañero (feature 9)

Comentarios: quien puede `canView` puede leer y comentar; `canEdit`/autor
decide sobre editar/borrar. Los helpers ya existen.

---

## 5. Historial de Crafteo

### Modelo

Colección `crafteos`, id autogenerado.

```java
@Data class RegistroCrafteo {
  String id;
  String uid;
  String itemId;
  String itemNombre;
  String tipoReceta;                        // CRAFTEO | HORNO
  int cantidad;
  List<MaterialConsumido> materiales;       // {itemId, nombre, cantidad}
  String fecha;                             // ISO-8601
}
```

### Reglas

- Se escribe **dentro de la misma transacción** de
  `CraftingCalculatorService.craft` (`tx.create(...)`), así el registro y el
  descuento de inventario son atómicos.
- Los materiales consumidos son los del plan (materias primas tras la
  expansión recursiva) — exactamente lo que se descontó.
- Solo el propio usuario ve su historial (admin no tiene vista especial
  aquí; la auditoría cubre al admin).

### Superficies

- `HistorialCrafteoService.listar(uid)` ordenado por fecha descendente.
- Web: `/historial-crafteo` (tabla: fecha, ítem con ícono, cantidad, tipo,
  materiales consumidos). Enlace desde la calculadora tras craftear y en el
  nav (grupo Items).
- API: `GET /api/crafteos` (del usuario en sesión).

### Para el compañero (feature 4)

El dashboard puede agregar `crafteos` por ítem/fecha.

---

## 7. Log de Auditoría de Admin

### Modelo

Colección `auditoria`, id autogenerado.

```java
@Data class EventoAuditoria {
  String id;
  String actorUid;
  String actorNombre;
  String accion;         // CREAR | EDITAR | ELIMINAR
  String entidad;        // ITEM | PROYECTO | USUARIO
  String entidadId;
  String entidadNombre;
  String detalle;        // opcional: "Estado: PLANIFICACION → EN_CONSTRUCCION", "Invitó a x@y (EDICION)"
  String fecha;
}
```

### Reglas

- `AuditService.registrar(HttpSession, accion, entidad, id, nombre, detalle)`
  lee actor de la sesión. **Nunca lanza**: captura y loguea cualquier error.
- Se llama desde los controladores (web y API) **después** de que la
  operación tuvo éxito:
  - Ítems: crear, editar, eliminar.
  - Proyectos: crear, editar datos, cambio de estado (EDITAR + detalle),
    miembros (EDITAR + detalle), eliminar.
  - Usuarios: registro (CREAR, actor = el mismo usuario), editar perfil
    propio, edición por admin (detalle incluye cambio de rol), eliminar
    cuenta propia y eliminación por admin.
- Solo admin puede consultar.

### Superficies

- Web: `/admin/auditoria` (tabla, más reciente primero, filtros por entidad,
  acción y texto libre sobre actor/entidad). Chequeo de rol ADMIN como en
  `WebAdminUserController`.
- API: `GET /api/admin/auditoria?entidad=&accion=&q=` (ya cubierta por
  `AdminAuthInterceptor` en `/api/admin/**`).
- Nav: "Auditoría" junto a "Administrar Usuarios" (solo admin).

---

## Orden de implementación

1. **Horno** (aislado en `items` + calculadora).
2. **Mundos** (nuevo paquete `mundos` + campos en `Proyecto`).
3. **Colaborativos** (permisos de proyectos; toca lista, detalle, PDF).
4. **Historial de crafteo** (transacción de `craft`; ya conoce `tipoReceta`).
5. **Auditoría** (al final: se engancha a todo, incluidos miembros).

Cada paso termina con `gradlew.bat test` verde. **Los commits los hace el
usuario** (Claude no commitea ni figura como co-autor).

## Riesgos conocidos / decisiones del implementador

- **Cuentas eliminadas** siguen apareciendo como miembros con su nombre
  guardado; al eliminar una cuenta no se limpian sus membresías (aceptable
  para el tamaño del proyecto).
- **Proyectos existentes** pasan a ser privados de su creador al desplegar.
  Es el comportamiento elegido (opción A), pero cambia lo que ven los
  usuarios actuales.
- Detalles de maquetación, textos y estilos: a criterio del implementador,
  siguiendo `DESIGN-SYSTEM.md` y `style.css`.
