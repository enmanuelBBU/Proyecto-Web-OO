# Funcionalidades Base — Plan de Implementación

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Implementar Recetas de Horno, CRUD de Mundos, Proyectos Colaborativos (privados + ids no-slug), Historial de Crafteo y Log de Auditoría.

**Architecture:** Cada funcionalidad sigue el patrón existente: modelo Lombok `@Data` persistido en Firestore, servicio `@Service` con la lógica pura en métodos `static` (testeables sin Firestore), controlador web Thymeleaf con chequeo manual de sesión y controlador REST bajo `/api/**` (ya protegido por `ApiAuthInterceptor`; `/api/admin/**` por `AdminAuthInterceptor`).

**Tech Stack:** Java 17, Spring Boot 4.1.1 (webmvc, thymeleaf, validation), Lombok, Firebase Admin / Firestore, JUnit 5 + AssertJ + Mockito (`@WebMvcTest` + `@MockitoBean`).

**Spec:** `docs/superpowers/specs/2026-11-23-funcionalidades-base-design.md`

## Global Constraints

- Sin dependencias nuevas en `build.gradle`.
- Todo campo nuevo en un modelo persistido debe tolerar `null` (documentos Firestore existentes no lo tienen).
- No agregar getters/`isX()` derivados a modelos persistidos (Firestore los serializaría); la lógica derivada va en métodos `static` de servicios.
- Sesión: `uid`, `nombre`, `rol` (`"ADMIN"` o `"USUARIO"`); páginas web sin `uid` ⇒ `redirect:/login`.
- Mensajes de error en español, como los existentes; éxito/error en web vía `toastSuccess` / `toastError`.
- TDD: test que falla → implementación → `gradlew.bat test` verde.
- **No commitear.** Al cerrar cada tarea, dejar los cambios en el working tree con tests verdes; el usuario hace los commits (sin co-autor).
- Comando de tests: `.\gradlew.bat test --console=plain` (una clase: `.\gradlew.bat test --tests "cl.grupo5.proyectominecraft.<paquete>.<Clase>" --console=plain`).

## Review Focus

1. **Documentos existentes sin campos nuevos** (`tipoReceta`, `miembros`, `miembroUids`, `mundoId` = null): deben cargarse y comportarse con los defaults (CRAFTEO, sin miembros, sin mundo). Tests en Tasks 1 y 6.
2. **Editar un proyecto desde el formulario no debe borrar sus miembros** (el form no envía miembros). Test en Task 6.
3. **Invitar con email en otras mayúsculas o con espacios** (`" Alex@Mail.com "`) debe encontrar al usuario. Test en Task 6.
4. **API `GET /api/proyectos/{id}` sobre un proyecto privado ajeno** debe dar 404, no 403 ni 200. Test en Task 7.
5. **Pasar un ítem de HORNO a CRAFTEO** debe limpiar `recetaHorno` y recalcular `ingredientesParaCalculo` desde la matriz. Test en Task 1.

---

### Task 1: Horno — modelo y reglas en `ItemService`

**Files:**
- Modify: `src/main/java/cl/grupo5/proyectominecraft/items/Item.java`
- Modify: `src/main/java/cl/grupo5/proyectominecraft/items/ItemService.java`
- Test: `src/test/java/cl/grupo5/proyectominecraft/items/ItemServiceTest.java`

**Interfaces:**
- Produces:
  - `Item`: `String tipoReceta`, `String recetaHorno`.
  - `static void ItemService.normalizarReceta(Item item)` — fija `tipoReceta` (`null`/vacío ⇒ `"CRAFTEO"`), valida, y limpia el campo que no corresponde.
  - `static List<Ingrediente> ItemService.computeIngredientes(Item item)` — HORNO ⇒ `[{recetaHorno, 1}]`; CRAFTEO ⇒ cuenta de la matriz (la versión `List<String>` actual queda como helper privado o se reemplaza).
  - `static List<String> ItemService.idsIngredientes(Item item)` — ids distintos referenciados por la receta (matriz o horno); vacío si no hay.
  - `static String ItemService.tipoRecetaDe(Item item)` — `"HORNO"` si `tipoReceta` es HORNO, si no `"CRAFTEO"`.
  - `RecetaLookup.receta(id)` pasa a devolver `idsIngredientes` del ítem referenciado (no su matriz).

- [ ] **Step 1: Tests que fallan** (en `ItemServiceTest`):

```java
@Test void tipoRecetaNullSeNormalizaACrafteo() {
  var it = item("palo", List.of("tablones", null, null, null, null, null, null, null, null));
  ItemService.normalizarReceta(it);
  assertThat(it.getTipoReceta()).isEqualTo("CRAFTEO");
  assertThat(it.getRecetaHorno()).isNull();
}
@Test void recetaHornoGeneraUnIngredienteDeCantidadUno() {
  var it = horno("lingote_de_hierro", "hierro_crudo");
  ItemService.normalizarReceta(it);
  assertThat(ItemService.computeIngredientes(it))
      .extracting(Ingrediente::getItemId, Ingrediente::getCantidad)
      .containsExactly(tuple("hierro_crudo", 1));
}
@Test void hornoSinEntradaSeRechaza() {
  assertThatThrownBy(() -> ItemService.normalizarReceta(horno("lingote", null)))
      .isInstanceOf(RecipeValidationException.class)
      .hasMessage("Una receta de horno necesita un ítem de entrada.");
}
@Test void hornoConMatrizSeRechaza() {
  var it = horno("lingote", "hierro_crudo");
  it.setRecetaMatriz(List.of("a", "", "", "", "", "", "", "", ""));
  assertThatThrownBy(() -> ItemService.normalizarReceta(it))
      .hasMessage("Una receta de horno no usa la grilla de crafteo.");
}
@Test void materiaPrimaConHornoSeRechaza() {
  var it = horno("hierro_crudo", "x"); it.setEsMateriaPrima(true);
  assertThatThrownBy(() -> ItemService.normalizarReceta(it))
      .hasMessage("Una materia prima no puede tener receta.");
}
@Test void tipoRecetaInvalidoSeRechaza() {
  var it = item("x", List.of()); it.setTipoReceta("TELAR");
  assertThatThrownBy(() -> ItemService.normalizarReceta(it)).hasMessage("Tipo de receta inválido.");
}
@Test void pasarDeHornoACrafteoLimpiaEntradaYRecalcula() {   // Review Focus 5
  var it = item("antorcha", List.of("carbon", "", "", "palo", "", "", "", "", ""));
  it.setTipoReceta("CRAFTEO"); it.setRecetaHorno("hierro_crudo");
  ItemService.normalizarReceta(it);
  assertThat(it.getRecetaHorno()).isNull();
  assertThat(ItemService.computeIngredientes(it)).extracting(Ingrediente::getItemId)
      .containsExactly("carbon", "palo");
}
@Test void idsIngredientesCubreHornoYMatriz() {
  assertThat(ItemService.idsIngredientes(horno("lingote", "hierro_crudo"))).containsExactly("hierro_crudo");
  assertThat(ItemService.idsIngredientes(item("x", List.of("a", "a", "b", "", "", "", "", "", ""))))
      .containsExactly("a", "b");
}
@Test void cicloAtravesDeHornoSeDetecta() {
  // lingote ← horno(mineral); mineral ← crafteo(lingote): crear lingote debe fallar
  RecetaLookup lookup = id -> id.equals("mineral") ? List.of("lingote") : List.of();
  assertThatThrownBy(() -> ItemService.validarCiclos("lingote", List.of("mineral"), lookup))
      .isInstanceOf(RecipeValidationException.class);
}
@Test void tipoRecetaDeTrataNullComoCrafteo() {
  assertThat(ItemService.tipoRecetaDe(item("x", List.of()))).isEqualTo("CRAFTEO");
  assertThat(ItemService.tipoRecetaDe(horno("l", "h"))).isEqualTo("HORNO");
}
```

Helpers `item(id, matriz)` y `horno(id, entrada)` (con `nombre = id`) en la clase de test.

- [ ] **Step 2:** Correr `ItemServiceTest` → FAIL (no compila: métodos/campos inexistentes).
- [ ] **Step 3:** Agregar campos a `Item`; implementar los métodos de Interfaces. `normalizarReceta` reemplaza la llamada a `validateRecetaMatriz` en `create`/`update` (conserva sus reglas para CRAFTEO). En `create`/`update`: `normalizarReceta(item)`, luego `validarReferenciasYCiclos(id, idsIngredientes(item))` (el lookup lee el ítem referenciado y devuelve `idsIngredientes(it)`), luego `item.setIngredientesParaCalculo(computeIngredientes(item))`. Ajustar tests existentes que llamen `computeIngredientes(List<String>)` si cambia la firma.
- [ ] **Step 4:** `.\gradlew.bat test --console=plain` → PASS (toda la suite).
- [ ] **Step 5:** Dejar listo para commit del usuario (no commitear).

---

### Task 2: Horno — formularios, catálogo y calculadora

**Files:**
- Modify: `src/main/java/cl/grupo5/proyectominecraft/items/WebItemController.java`
- Modify: `src/main/java/cl/grupo5/proyectominecraft/inventario/CraftingPlan.java`, `CraftingCalculatorService.java`
- Modify: `src/main/resources/templates/items.html`, `item-edit.html`, `calculadora.html`
- Modify: `src/main/resources/static/recipe-item-list.js`
- Create: `src/main/resources/static/recipe-type.js`
- Test: `src/test/java/cl/grupo5/proyectominecraft/items/WebItemControllerTest.java`, `src/test/java/cl/grupo5/proyectominecraft/inventario/WebCraftingControllerTest.java`

**Interfaces:**
- Consumes: `Item.tipoReceta/recetaHorno`, `ItemService.tipoRecetaDe(Item)` (Task 1).
- Produces: `CraftingPlan.tipoReceta` (`"CRAFTEO"`/`"HORNO"`); params de formulario `tipoReceta` y `entradaHorno` en `POST /items` y `POST /items/{id}/update`.

- [ ] **Step 1: Tests que fallan:**

```java
// WebItemControllerTest
@Test void crearItemHornoPasaTipoYEntradaAlServicio() throws Exception {
  mvc.perform(post("/items").session(adminSession())
      .param("nombre", "Lingote de hierro").param("fullId", "minecraft:iron_ingot")
      .param("tipoReceta", "HORNO").param("entradaHorno", "hierro_crudo")
      .param("slot", "palo", "", "", "", "", "", "", "", ""))
    .andExpect(redirectedUrl("/items"));
  var captor = ArgumentCaptor.forClass(Item.class);
  verify(items).create(captor.capture());
  assertThat(captor.getValue().getTipoReceta()).isEqualTo("HORNO");
  assertThat(captor.getValue().getRecetaHorno()).isEqualTo("hierro_crudo");
  assertThat(captor.getValue().getRecetaMatriz()).isEmpty();   // con HORNO se ignoran los slots
}
// WebCraftingControllerTest
@Test void calculadoraExponePlanConTipoHorno() throws Exception {
  var plan = new CraftingPlan(); plan.setTargetItemId("lingote"); plan.setCantidad(1);
  plan.setMateriales(List.of()); plan.setCraftable(true); plan.setTipoReceta("HORNO");
  when(calculator.plan("u1", "lingote", 1)).thenReturn(plan);
  mvc.perform(get("/calculadora").param("targetId", "lingote").session(session("u1")))
    .andExpect(model().attribute("plan", hasProperty("tipoReceta", is("HORNO"))));
}
```

(Usar los nombres de helpers/mocks que ya existan en cada clase de test.)

- [ ] **Step 2:** Correr ambas clases → FAIL.
- [ ] **Step 3:** Implementar:
  - `WebItemController.create/update`: `@RequestParam(required=false) String tipoReceta, String entradaHorno`; si `"HORNO".equals(tipoReceta)` ⇒ `setRecetaMatriz(List.of())`, `setRecetaHorno(blank ? null : entradaHorno.trim())`; si no ⇒ comportamiento actual con `setRecetaHorno(null)`. `setTipoReceta(tipoReceta)`. En `edit` exponer `entradaHorno` = `it.getRecetaHorno()`.
  - `CraftingPlan`: campo `String tipoReceta`. `CraftingCalculatorService.plan` lo fija con `ItemService.tipoRecetaDe(target)`.
  - Templates: en ambos formularios, `<select id="…-tipo-receta" name="tipoReceta">` (Mesa de crafteo = `CRAFTEO`, Horno = `HORNO`) antes del editor; dentro de `.recipe-editor` un bloque `.furnace-slot` con un único `.recipe-slot` cuyo hidden es `name="entradaHorno"`. `recipe-type.js` muestra `.recipe-grid` o `.furnace-slot` según el select (al cargar y en `change`). En `recipe-item-list.js`, `fillSlotVisual/clearSlotVisual` y el resolver de slots ya llenos usan `slot.querySelector('input[type="hidden"]')` en vez de `input[name="slot"]`.
  - `items.html`: badge "Horno" en ítems con `tipoReceta == 'HORNO'`. `calculadora.html`: título/botón "Fundir" si `plan.tipoReceta == 'HORNO'`, si no "Craftear".
- [ ] **Step 4:** Suite completa → PASS.
- [ ] **Step 5:** Dejar listo para commit del usuario.

---

### Task 3: Mundos — modelo y `MundoService`

**Files:**
- Create: `src/main/java/cl/grupo5/proyectominecraft/mundos/Mundo.java`, `ModoJuego.java`, `MundoService.java`, `MundoValidationException.java`
- Test: `src/test/java/cl/grupo5/proyectominecraft/mundos/MundoServiceTest.java`

**Interfaces:**
- Produces:
  - `Mundo` (`@Data`): `id, uid, nombre, version, modoJuego, semilla, fechaCreacion` (todos `String`).
  - `enum ModoJuego { SUPERVIVENCIA, CREATIVO, AVENTURA, ESPECTADOR, HARDCORE }` con `static ModoJuego fromString(String)` (IllegalArgumentException si inválido).
  - `MundoValidationException extends RuntimeException`.
  - `MundoService(Firestore db)`:
    - `List<Mundo> listar(String uid)` — mundos de ese uid ordenados por nombre (sin mayúsculas).
    - `Mundo get(String id)` — null si no existe.
    - `Mundo crear(Mundo m, String uid)` — id autogenerado (`db.collection("mundos").document()`), fija `uid` y `fechaCreacion = Instant.now().toString()`.
    - `Mundo actualizar(String id, Mundo m, String uid, String rol)` — null si no existe; preserva `uid` y `fechaCreacion`.
    - `boolean eliminar(String id, String uid, String rol)` — false si no existe; rechaza si `db.collection("proyectos").whereEqualTo("mundoId", id)` tiene resultados.
    - `static void validar(Mundo m)` — trim; modo `null`/vacío ⇒ `SUPERVIVENCIA`; semilla vacía ⇒ null.
    - `static boolean puedeGestionar(Mundo m, String uid, String rol)` — dueño o ADMIN.
    - `static List<Mundo> delUsuario(List<Mundo> todos, String uid)`.

- [ ] **Step 1: Tests que fallan:**

```java
@Test void validarExigeNombre() { assertThatThrownBy(() -> MundoService.validar(mundo(" ", "1.21", null)))
    .isInstanceOf(MundoValidationException.class).hasMessage("El nombre del mundo es obligatorio."); }
@Test void validarExigeVersion() { assertThatThrownBy(() -> MundoService.validar(mundo("Survival", "", null)))
    .hasMessage("La versión es obligatoria."); }
@Test void validarRechazaModoInvalido() { assertThatThrownBy(() -> MundoService.validar(mundo("S", "1.21", "PACIFICO")))
    .hasMessage("Modo de juego inválido."); }
@Test void validarNormalizaModoYSemilla() {
  var m = mundo("  Survival  ", "1.21", "creativo"); m.setSemilla("  ");
  MundoService.validar(m);
  assertThat(m.getNombre()).isEqualTo("Survival");
  assertThat(m.getModoJuego()).isEqualTo("CREATIVO");
  assertThat(m.getSemilla()).isNull();
  var sinModo = mundo("S", "1.21", null); MundoService.validar(sinModo);
  assertThat(sinModo.getModoJuego()).isEqualTo("SUPERVIVENCIA");
}
@Test void soloDuenoOAdminGestiona() {
  var m = mundo("S", "1.21", null); m.setUid("u1");
  assertThat(MundoService.puedeGestionar(m, "u1", "USUARIO")).isTrue();
  assertThat(MundoService.puedeGestionar(m, "u2", "ADMIN")).isTrue();
  assertThat(MundoService.puedeGestionar(m, "u2", "USUARIO")).isFalse();
  assertThat(MundoService.puedeGestionar(null, "u1", "ADMIN")).isFalse();
}
@Test void delUsuarioFiltraYOrdena() {
  var a = mundo("zeta", "1", null); a.setUid("u1");
  var b = mundo("Alfa", "1", null); b.setUid("u1");
  var c = mundo("otro", "1", null); c.setUid("u2");
  assertThat(MundoService.delUsuario(List.of(a, b, c), "u1")).extracting(Mundo::getNombre).containsExactly("Alfa", "zeta");
}
```

Mensajes extra usados por los métodos con Firestore (no testeados aquí): `"No tienes permisos sobre este mundo."`, `"El mundo tiene " + n + " proyecto(s) asignado(s)."`.

- [ ] **Step 2:** Correr `MundoServiceTest` → FAIL.
- [ ] **Step 3:** Implementar según Interfaces (`crear`/`actualizar` llaman `validar`; `actualizar`/`eliminar` lanzan `MundoValidationException("No tienes permisos sobre este mundo.")` si `!puedeGestionar`).
- [ ] **Step 4:** Suite → PASS.
- [ ] **Step 5:** Dejar listo para commit del usuario.

---

### Task 4: Mundos — web, API y nav

**Files:**
- Create: `src/main/java/cl/grupo5/proyectominecraft/mundos/WebMundoController.java`, `MundoController.java`
- Create: `src/main/resources/templates/mundos.html`, `mundo-detalle.html`, `mundo-edit.html`
- Modify: `src/main/resources/templates/fragments/nav.html`
- Modify: `src/main/java/cl/grupo5/proyectominecraft/proyectos/ProyectoService.java` (agrega `listarPorMundo`)
- Test: `src/test/java/cl/grupo5/proyectominecraft/mundos/WebMundoControllerTest.java`, `MundoControllerTest.java`

**Interfaces:**
- Consumes: `MundoService` (Task 3).
- Produces: `List<Proyecto> ProyectoService.listarPorMundo(String mundoId)` (`whereEqualTo("mundoId", mundoId)`).
- Rutas web: `GET /mundos` (lista propia + form crear; model `mundos`, `modos` = `ModoJuego.values()`), `POST /mundos`, `GET /mundos/{id}` (model `mundo`, `proyectos`), `GET /mundos/{id}/edit`, `POST /mundos/{id}/update`, `POST /mundos/{id}/delete`. Mundo inexistente o sin `puedeGestionar` ⇒ `redirect:/mundos` con `toastError`.
- API: `GET/POST /api/mundos`, `GET/PUT/DELETE /api/mundos/{id}`. Ajeno o inexistente ⇒ 404; `MundoValidationException` de permisos no aplica (se filtra antes); validación ⇒ 400; eliminar con proyectos ⇒ 409 con el mensaje. Crear ⇒ 201.

- [ ] **Step 1: Tests que fallan** (patrón de `WebProyectoControllerTest`):

```java
@Test void mundosSinSesionRedirigeALogin()            // GET /mundos → redirect /login
@Test void listaSoloMundosDelUsuario()                 // when(mundos.listar("u1")) → model "mundos" igual a esa lista
@Test void crearMundoInvalidoMuestraError()            // crear lanza MundoValidationException("La versión es obligatoria.") → view "mundos", model error
@Test void detalleDeMundoAjenoRedirige()               // get("m1") devuelve mundo uid "u2"; sesión u1 USUARIO → redirect /mundos
@Test void eliminarConProyectosMuestraToastError()     // eliminar lanza MundoValidationException("El mundo tiene 2 proyecto(s) asignado(s).") → redirect /mundos/m1, flash toastError
// MundoControllerTest
@Test void apiGetMundoAjenoDa404()
@Test void apiCrearDevuelve201()
@Test void apiEliminarConProyectosDa409()
```

- [ ] **Step 2:** Correr → FAIL.
- [ ] **Step 3:** Implementar controladores y templates (copiar estructura de `proyectos.html`/`proyecto-edit.html`: `<nav th:replace>`, `.card`, `fragments/toast`). Detalle muestra nombre, versión, modo, semilla, fecha y tabla de proyectos asignados con X/Y/Z. Nav: `<li><a href="/mundos">Mis Mundos</a></li>` en grupo Proyectos.
- [ ] **Step 4:** Suite → PASS.
- [ ] **Step 5:** Dejar listo para commit del usuario.

---

### Task 5: Proyecto ↔ Mundo (ubicación)

**Files:**
- Modify: `src/main/java/cl/grupo5/proyectominecraft/proyectos/Proyecto.java`, `ProyectoService.java`, `WebProyectoController.java`
- Modify: `src/main/resources/templates/proyectos.html` (form crear), `proyecto-edit.html`, `proyecto-detalle.html`
- Test: `ProyectoServiceTest.java`, `WebProyectoControllerTest.java`

**Interfaces:**
- Consumes: `MundoService.get`, `MundoService.listar` (Task 3).
- Produces:
  - `Proyecto`: `String mundoId`, `Integer x`, `Integer y`, `Integer z`.
  - `@FunctionalInterface interface ProyectoService.MundoLookup { Mundo get(String id) throws Exception; }`
  - `static void ProyectoService.validarUbicacion(Proyecto p, String creadorUid, MundoLookup mundos)` — `mundoId` vacío ⇒ `mundoId, x, y, z = null`.
  - `ProyectoService` recibe `MundoService` por constructor; `create`/`update` llaman `validarUbicacion(p, creadorUid, mundoService::get)`.
  - Form params `mundoId`, `x`, `y`, `z` (Integer, opcionales) en `POST /proyectos` y `/proyectos/{id}/update`. Model `mundos` en `/proyectos` (del usuario) y edit (del creador). Detalle: model `mundo` (o null).

- [ ] **Step 1: Tests que fallan:**

```java
@Test void sinMundoLimpiaCoordenadas() throws Exception {
  var p = new Proyecto(); p.setMundoId(" "); p.setX(5); p.setY(70); p.setZ(5);
  ProyectoService.validarUbicacion(p, "u1", id -> null);
  assertThat(p.getMundoId()).isNull(); assertThat(p.getX()).isNull(); assertThat(p.getY()).isNull();
}
@Test void mundoInexistenteSeRechaza() {
  var p = new Proyecto(); p.setMundoId("m9");
  assertThatThrownBy(() -> ProyectoService.validarUbicacion(p, "u1", id -> null))
      .isInstanceOf(ProyectoValidationException.class).hasMessage("El mundo seleccionado no existe.");
}
@Test void mundoDeOtroUsuarioSeRechaza() {
  var m = new Mundo(); m.setUid("u2");
  var p = new Proyecto(); p.setMundoId("m1");
  assertThatThrownBy(() -> ProyectoService.validarUbicacion(p, "u1", id -> m))
      .hasMessage("El mundo debe pertenecer al creador del proyecto.");
}
@Test void yFueraDeRangoSeRechaza() {
  var m = new Mundo(); m.setUid("u1");
  var p = new Proyecto(); p.setMundoId("m1"); p.setY(321);
  assertThatThrownBy(() -> ProyectoService.validarUbicacion(p, "u1", id -> m))
      .hasMessage("La coordenada Y debe estar entre -64 y 320.");
}
@Test void xzFueraDeRangoSeRechaza() {
  var m = new Mundo(); m.setUid("u1");
  var p = new Proyecto(); p.setMundoId("m1"); p.setX(30_000_001);
  assertThatThrownBy(() -> ProyectoService.validarUbicacion(p, "u1", id -> m))
      .hasMessage("Las coordenadas X y Z deben estar entre -30.000.000 y 30.000.000.");
}
@Test void mundoSinCoordenadasEsValido()   // mundo propio, x/y/z null → no lanza
// WebProyectoControllerTest
@Test void crearProyectoEnviaMundoYCoordenadas()   // params mundoId=m1,x=10,y=64,z=-5 → captor del Proyecto pasado a create
```

- [ ] **Step 2:** Correr → FAIL.
- [ ] **Step 3:** Implementar. Agregar `@MockitoBean MundoService` en los `@WebMvcTest` que lo necesiten. Formularios: `<select name="mundoId">` (opción vacía "Sin mundo") + tres `<input type="number" name="x|y|z">`. Detalle: "Mundo: <nombre> · X/Y/Z" con enlace a `/mundos/{id}` si el usuario es dueño del mundo.
- [ ] **Step 4:** Suite → PASS.
- [ ] **Step 5:** Dejar listo para commit del usuario.

---

### Task 6: Colaborativos — reglas de acceso, miembros e ids (servicio)

**Files:**
- Create: `src/main/java/cl/grupo5/proyectominecraft/proyectos/Miembro.java`
- Modify: `Proyecto.java`, `ProyectoService.java`, `ProyectoAlreadyExistsException.java`
- Modify: `src/main/java/cl/grupo5/proyectominecraft/perfil/UserProfileService.java`
- Test: `ProyectoServiceTest.java`, `src/test/java/cl/grupo5/proyectominecraft/perfil/UserProfileServiceTest.java` (nuevo)

**Interfaces:**
- Produces:
  - `Miembro` (`@Data @NoArgsConstructor @AllArgsConstructor`): `uid, nombre, email, permiso, fechaIngreso`.
  - `Proyecto`: `List<Miembro> miembros = new ArrayList<>()`, `List<String> miembroUids = new ArrayList<>()`.
  - Constantes `ProyectoService.LECTURA = "LECTURA"`, `EDICION = "EDICION"`.
  - `static boolean canView(Proyecto p, String uid, String rol)`, `canEdit(...)`, `canManage(...)` — según tabla del spec; tolerantes a `miembros == null`. **`canUserModify` se elimina** (callers pasan a `canEdit`/`canManage`).
  - `static String permisoDe(Proyecto p, String uid)` — permiso del miembro o null.
  - `static List<Proyecto> visiblePara(List<Proyecto> todos, String uid, String rol)`.
  - `List<Proyecto> list(String q, String estado, String creadorUid, String viewerUid, String viewerRol)` — aplica `visiblePara` antes de `filter`. (Reemplaza la firma de 3 args.)
  - `Proyecto getVisible(String id, String uid, String rol)` — null si no existe o no visible.
  - `static boolean nombreDuplicado(List<Proyecto> delCreador, String nombre, String excluirId)` — compara `Slugs.slugify`.
  - `ProyectoAlreadyExistsException(String nombre)` → mensaje `"Ya tienes un proyecto llamado '" + nombre + "'."`.
  - `create`: id = `db.collection("proyectos").document().getId()`; duplicado ⇒ lanza la excepción. `update`: duplicado excluyendo su id; preserva `miembros`/`miembroUids` del documento actual; permiso con `canEdit`. `delete`: `canManage`. `transicionarEstado`: `canEdit`.
  - `static void agregarMiembro(Proyecto p, UserProfile u, String permiso, String fecha)` — valida y agrega o actualiza permiso; sincroniza `miembroUids`.
  - `static void cambiarPermiso(Proyecto p, String uid, String permiso)`; `static void quitarMiembro(Proyecto p, String uid)` — sincronizan `miembroUids`.
  - `Proyecto invitar(String id, String email, String permiso, String uid, String rol)`, `Proyecto cambiarPermisoMiembro(String id, String miembroUid, String permiso, String uid, String rol)`, `Proyecto quitarMiembro(String id, String miembroUid, String uid, String rol)` — null si no existe; sin `canManage` ⇒ `ProyectoValidationException("No tienes permisos para gestionar los miembros de este proyecto.")`.
  - `ProyectoService` recibe `UserProfileService` por constructor.
  - `UserProfileService.findByEmail(String email)` + `static UserProfile buscarPorEmail(List<UserProfile> todos, String email)` (trim + ignore case; null si no hay).

- [ ] **Step 1: Tests que fallan:**

```java
@Test void reglasDeAcceso() {
  var p = proyecto("p1", "Castillo", "", "PLANIFICACION", "creador");
  p.setMiembros(new ArrayList<>(List.of(new Miembro("lector", "L", "l@x.cl", "LECTURA", "t"),
                                        new Miembro("editor", "E", "e@x.cl", "EDICION", "t"))));
  assertThat(ProyectoService.canView(p, "lector", "USUARIO")).isTrue();
  assertThat(ProyectoService.canEdit(p, "lector", "USUARIO")).isFalse();
  assertThat(ProyectoService.canEdit(p, "editor", "USUARIO")).isTrue();
  assertThat(ProyectoService.canManage(p, "editor", "USUARIO")).isFalse();
  assertThat(ProyectoService.canManage(p, "creador", "USUARIO")).isTrue();
  assertThat(ProyectoService.canManage(p, "otro", "ADMIN")).isTrue();
  assertThat(ProyectoService.canView(p, "otro", "USUARIO")).isFalse();
}
@Test void proyectoSinCampoMiembrosSoloLoVeElCreador() {     // Review Focus 1
  var p = proyecto("p1", "C", "", "PLANIFICACION", "creador"); p.setMiembros(null); p.setMiembroUids(null);
  assertThat(ProyectoService.canView(p, "creador", "USUARIO")).isTrue();
  assertThat(ProyectoService.canView(p, "otro", "USUARIO")).isFalse();
}
@Test void visibleParaFiltraPrivados()        // u1 ve el suyo y uno donde es miembro; admin ve todos
@Test void agregarMiembroSincronizaUids() {
  var p = proyecto("p1", "C", "", "PLANIFICACION", "creador");
  ProyectoService.agregarMiembro(p, perfil("u2", "Alex", "alex@x.cl"), "EDICION", "t");
  assertThat(p.getMiembroUids()).containsExactly("u2");
  ProyectoService.agregarMiembro(p, perfil("u2", "Alex", "alex@x.cl"), "LECTURA", "t");
  assertThat(p.getMiembros()).singleElement().extracting(Miembro::getPermiso).isEqualTo("LECTURA");
}
@Test void noSePuedeInvitarAlCreador()        // hasMessage("No puedes invitar al creador del proyecto.")
@Test void permisoInvalidoSeRechaza()         // "ADMIN" → hasMessage("Permiso inválido.")
@Test void cambiarPermisoDeNoMiembroFalla()   // hasMessage("El usuario no es miembro del proyecto.")
@Test void quitarMiembroSincronizaUids()
@Test void nombreDuplicadoIgnoraMayusculasYAcentosYExcluyePropio() {
  var lista = List.of(proyecto("abc", "Casa Árbol", "", "PLANIFICACION", "u1"));
  assertThat(ProyectoService.nombreDuplicado(lista, "casa arbol", null)).isTrue();
  assertThat(ProyectoService.nombreDuplicado(lista, "casa arbol", "abc")).isFalse();
  assertThat(ProyectoService.nombreDuplicado(lista, "Granja", null)).isFalse();
}
// UserProfileServiceTest
@Test void buscarPorEmailIgnoraMayusculasYEspacios() {      // Review Focus 3
  var u = new UserProfile(); u.setUid("u2"); u.setEmail("alex@mail.com");
  assertThat(UserProfileService.buscarPorEmail(List.of(u), " Alex@Mail.com ")).isSameAs(u);
  assertThat(UserProfileService.buscarPorEmail(List.of(u), "otro@mail.com")).isNull();
}
```

Review Focus 2 (update preserva miembros) se cubre con un test de la función pura que arma el documento a guardar: extraer `static Proyecto prepararActualizacion(Proyecto actual, Proyecto cambios, String id)` (copia creador, fecha, `historialEstados`, `miembros`, `miembroUids` desde `actual`) y testear que `cambios` sin miembros conserva los de `actual`.

> Nota: hoy `update` no preserva `historialEstados` (el form no lo envía y se pierde). `prepararActualizacion` lo preserva también — es el mismo bug.

- [ ] **Step 2:** Correr `ProyectoServiceTest` y `UserProfileServiceTest` → FAIL.
- [ ] **Step 3:** Implementar según Interfaces. Reemplazar el test existente `canUserModifyAuthorizationRules` por `reglasDeAcceso`.
- [ ] **Step 4:** Suite → **fallará la compilación de controladores** por la firma nueva de `list` y la eliminación de `canUserModify`: ajustar llamadas mínimamente (pasar `uid`, `rol` de sesión; `canUserModify` → `canEdit`/`canManage`) y sus tests para que la suite quede verde. La UI de miembros va en Task 7.
- [ ] **Step 5:** Dejar listo para commit del usuario.

---

### Task 7: Colaborativos — controladores y vistas

**Files:**
- Modify: `WebProyectoController.java`, `ProyectoController.java`, `ProyectoPdfController.java`
- Modify: `src/main/resources/templates/proyectos.html`, `proyecto-detalle.html`, `proyecto-estados.html` (si usa `canEdit`)
- Test: `WebProyectoControllerTest.java`, `ProyectoControllerTest.java`, `ProyectoPdfControllerTest.java`

**Interfaces:**
- Consumes: todo lo de Task 6.
- Web:
  - `/proyectos`: `list(q, estado, filterUid, uid, rol)`; model `editables` (`Set<String>` ids con `canEdit`) y `compartidos` (`Map<String,String>` id → permiso, solo donde el usuario es miembro). Botón "Editar" usa `editables`; badge "Compartido · Lectura/Edición".
  - `/proyectos/{id}`, `/edit`, `/estados`, `/transicionar`, `/update`: usan `getVisible`; null ⇒ `redirect:/proyectos` + flash `toastError` `"Proyecto no encontrado."`. Edit/update/transicionar requieren `canEdit`. Detalle expone `canEdit`, `canManage`, `permisos` = `List.of("LECTURA","EDICION")`.
  - `POST /proyectos/{id}/miembros` (`email`, `permiso`), `POST /proyectos/{id}/miembros/{miembroUid}/permiso` (`permiso`), `POST /proyectos/{id}/miembros/{miembroUid}/delete` ⇒ `redirect:/proyectos/{id}` con toast de éxito/error.
  - `proyecto-detalle.html`: sección "Miembros" (tabla nombre/email/permiso; formulario invitar + select permiso + botón quitar solo si `canManage`).
- API:
  - `GET /api/proyectos` con visibilidad; `GET /api/proyectos/{id}` y `GET /{id}/estados` ⇒ 404 si no visible.
  - `GET /api/proyectos/{id}/miembros` (404 si no visible), `POST /api/proyectos/{id}/miembros` body `{email, permiso}`, `PUT /api/proyectos/{id}/miembros/{uid}` body `{permiso}`, `DELETE /api/proyectos/{id}/miembros/{uid}`. Mensaje con "permisos" ⇒ 403; otra validación ⇒ 400; null ⇒ 404.
  - `POST /api/proyectos` duplicado ⇒ 409 (como hoy).
- PDF: `/proyectos/reportes` lista visibles; `/proyectos/reporte-pdf?id=` no visible ⇒ redirect `/proyectos/reportes`.

- [ ] **Step 1: Tests que fallan:**

```java
// ProyectoControllerTest
@Test void apiGetProyectoPrivadoAjenoDa404() throws Exception {      // Review Focus 4
  when(service.getVisible("p1", "u2", "USUARIO")).thenReturn(null);
  mvc.perform(get("/api/proyectos/p1").session(session("u2"))).andExpect(status().isNotFound());
}
@Test void apiInvitarSinPermisoDa403()     // invitar lanza ProyectoValidationException("No tienes permisos para gestionar los miembros de este proyecto.")
@Test void apiInvitarEmailDesconocidoDa400()
// WebProyectoControllerTest
@Test void detalleNoVisibleRedirigeConToast()   // getVisible → null ⇒ redirect /proyectos, flash toastError "Proyecto no encontrado."
@Test void lectorNoPuedeAbrirEdicion()           // getVisible devuelve proyecto con u2 LECTURA ⇒ GET /proyectos/p1/edit redirige a /proyectos/p1
@Test void invitarMiembroRedirigeConToastExito()
// ProyectoPdfControllerTest
@Test void pdfDeProyectoNoVisibleRedirige()
```

- [ ] **Step 2:** Correr las tres clases → FAIL.
- [ ] **Step 3:** Implementar.
- [ ] **Step 4:** Suite → PASS.
- [ ] **Step 5:** Dejar listo para commit del usuario.

---

### Task 8: Historial de Crafteo

**Files:**
- Create: `src/main/java/cl/grupo5/proyectominecraft/inventario/RegistroCrafteo.java`, `MaterialConsumido.java`, `HistorialCrafteoService.java`, `WebHistorialCrafteoController.java`, `HistorialCrafteoController.java`
- Create: `src/main/resources/templates/historial-crafteo.html`
- Modify: `CraftingCalculatorService.java`, `WebCraftingController.java`, `calculadora.html`, `fragments/nav.html`
- Test: `src/test/java/cl/grupo5/proyectominecraft/inventario/HistorialCrafteoServiceTest.java`, `WebHistorialCrafteoControllerTest.java`, `HistorialCrafteoControllerTest.java`

**Interfaces:**
- Consumes: `CraftingPlan` (con `tipoReceta`, Task 2), `Item`.
- Produces:
  - `MaterialConsumido` (`@Data`): `itemId, nombre, cantidad (long)`.
  - `RegistroCrafteo` (`@Data`): `id, uid, itemId, itemNombre, tipoReceta, cantidad (int), materiales (List<MaterialConsumido>), fecha`.
  - `static RegistroCrafteo HistorialCrafteoService.crearRegistro(String uid, Item target, CraftingPlan plan, String fecha)` — materiales = `plan.materiales` (itemId, nombre, requerido).
  - `List<RegistroCrafteo> HistorialCrafteoService.listar(String uid)` — `whereEqualTo("uid", uid)`, ordenado por `static ordenarRecientes(List)` (fecha desc).
  - `CraftingCalculatorService.craft`: obtiene `target = items.get(targetItemId)` antes de la transacción; dentro de `runTransaction`, tras `tx.set(ref, …)`, `tx.create(db.collection("crafteos").document(), crearRegistro(uid, target, plan, Instant.now().toString()))`.
  - Web `GET /historial-crafteo` (model `registros`); API `GET /api/crafteos`.

- [ ] **Step 1: Tests que fallan:**

```java
@Test void crearRegistroCopiaMaterialesDelPlan() {
  var target = new Item(); target.setId("antorcha"); target.setNombre("Antorcha");
  var plan = new CraftingPlan(); plan.setTargetItemId("antorcha"); plan.setCantidad(4); plan.setTipoReceta("CRAFTEO");
  var m = new MaterialRequirement(); m.setItemId("carbon"); m.setNombre("Carbón"); m.setRequerido(4);
  plan.setMateriales(List.of(m));
  var r = HistorialCrafteoService.crearRegistro("u1", target, plan, "2026-11-23T10:00:00Z");
  assertThat(r.getUid()).isEqualTo("u1");
  assertThat(r.getItemNombre()).isEqualTo("Antorcha");
  assertThat(r.getCantidad()).isEqualTo(4);
  assertThat(r.getTipoReceta()).isEqualTo("CRAFTEO");
  assertThat(r.getMateriales()).extracting(MaterialConsumido::getItemId, MaterialConsumido::getCantidad)
      .containsExactly(tuple("carbon", 4L));
}
@Test void ordenarRecientesPrimero()      // fechas "2026-11-01…", "2026-11-23…" → la del 23 primero
// Web/API
@Test void historialSinSesionRedirige()
@Test void historialExponeRegistrosDelUsuario()   // when(historial.listar("u1")) → model "registros"
@Test void apiCrafteosDevuelveListaDelUsuario()
```

- [ ] **Step 2:** Correr → FAIL.
- [ ] **Step 3:** Implementar. Template: tabla fecha · ítem (ícono si `fullId` disponible vía `itemMap`, o solo nombre) · tipo · cantidad · materiales ("4× Carbón, 4× Palo"). Toast de éxito de `WebCraftingController.craft` pasa a "¡Crafteo exitoso! Quedó registrado en tu historial." y `calculadora.html` agrega enlace "Ver historial". Nav: "Historial de Crafteo" en grupo Items.
- [ ] **Step 4:** Suite → PASS.
- [ ] **Step 5:** Dejar listo para commit del usuario.

---

### Task 9: Auditoría — servicio y vistas de admin

**Files:**
- Create: `src/main/java/cl/grupo5/proyectominecraft/auditoria/EventoAuditoria.java`, `AuditService.java`, `WebAuditoriaController.java`, `AuditoriaController.java`
- Create: `src/main/resources/templates/admin-auditoria.html`
- Modify: `fragments/nav.html`
- Test: `src/test/java/cl/grupo5/proyectominecraft/auditoria/AuditServiceTest.java`, `WebAuditoriaControllerTest.java`, `AuditoriaControllerTest.java`

**Interfaces:**
- Produces:
  - `EventoAuditoria` (`@Data`): `id, actorUid, actorNombre, accion, entidad, entidadId, entidadNombre, detalle, fecha`.
  - Constantes en `AuditService`: `CREAR, EDITAR, ELIMINAR`; `ITEM, PROYECTO, USUARIO`.
  - `void AuditService.registrar(HttpSession s, String accion, String entidad, String entidadId, String entidadNombre, String detalle)` — arma con `static EventoAuditoria construir(String actorUid, String actorNombre, …, String fecha)` y escribe en `auditoria` (`document()` autogenerado). **Captura `Exception` y solo loguea (SLF4J), nunca relanza.**
  - `List<EventoAuditoria> AuditService.listar(String entidad, String accion, String q)` — lee todo, `static filtrar(...)` (entidad/accion exactos si no vacíos; `q` sin mayúsculas sobre `actorNombre`, `entidadNombre`, `entidadId`), orden fecha desc.
  - Web `GET /admin/auditoria` (guard admin como `WebAdminUserController`; model `eventos`, `entidad`, `accion`, `q`). API `GET /api/admin/auditoria`.

- [ ] **Step 1: Tests que fallan:**

```java
@Test void construirUsaActorDeSesion()    // construir("u1","Steve","CREAR","ITEM","palo","Palo",null,"t") → campos copiados
@Test void registrarNoLanzaSiFirestoreFalla() {
  var db = mock(Firestore.class);
  when(db.collection("auditoria")).thenThrow(new RuntimeException("caído"));
  var s = new MockHttpSession(); s.setAttribute("uid", "u1"); s.setAttribute("nombre", "Steve");
  assertThatCode(() -> new AuditService(db).registrar(s, "CREAR", "ITEM", "palo", "Palo", null))
      .doesNotThrowAnyException();
}
@Test void filtrarPorEntidadAccionYTexto()
@Test void filtrarOrdenaMasRecientePrimero()
// Web/API
@Test void auditoriaParaNoAdminRedirige()     // rol USUARIO → redirect /items
@Test void auditoriaParaAdminExponeEventos()
@Test void apiAuditoriaNoAdminDa403()         // usa AdminAuthInterceptor real: ver cómo lo hace AdminUserControllerTest
```

- [ ] **Step 2:** Correr → FAIL.
- [ ] **Step 3:** Implementar. Template: filtros GET (select entidad, select acción, texto) + tabla fecha · actor · acción (badge) · entidad · nombre/id · detalle. Nav: `<li th:if="${isAdmin}"><a href="/admin/auditoria">Auditoría</a></li>`.
- [ ] **Step 4:** Suite → PASS.
- [ ] **Step 5:** Dejar listo para commit del usuario.

---

### Task 10: Auditoría — enganchar las acciones

**Files:**
- Modify: `WebItemController.java`, `ItemController.java`, `WebProyectoController.java`, `ProyectoController.java`, `WebAuthController.java`, `WebProfileController.java`, `UserProfileController.java`, `WebAdminUserController.java`, `AdminUserController.java`
- Test: los `@WebMvcTest` de esos controladores (agregar `@MockitoBean AuditService` a cada uno; sin esto el contexto no levanta).

**Interfaces:**
- Consumes: `AuditService.registrar(...)` (Task 9). Llamar **solo tras éxito**, con `(accion, entidad, entidadId, entidadNombre, detalle)`:

| Controlador / acción | accion | entidad | detalle |
|---|---|---|---|
| Item crear (web, API) | CREAR | ITEM | null |
| Item editar (web, API) | EDITAR | ITEM | `"Receta: HORNO"` si aplica, si no null |
| Item eliminar (web, API) | ELIMINAR | ITEM | null |
| Proyecto crear / editar / eliminar (web, API) | CREAR / EDITAR / ELIMINAR | PROYECTO | null |
| Proyecto transición (web, API) | EDITAR | PROYECTO | `"Estado: A → B"` |
| Miembro invitar / permiso / quitar (web, API) | EDITAR | PROYECTO | `"Invitó a email (PERMISO)"` / `"Permiso de email → PERMISO"` / `"Quitó a email"` |
| Registro (`WebAuthController.doRegister`, tras setear sesión) | CREAR | USUARIO | null |
| Perfil propio guardar (web, API) | EDITAR | USUARIO | null |
| Perfil propio eliminar (web, API) — **antes** de `invalidate()` | ELIMINAR | USUARIO | null |
| Admin editar usuario (web, API) | EDITAR | USUARIO | `"Rol: USUARIO → ADMIN"` si cambió, si no null |
| Admin eliminar usuario (web, API) | ELIMINAR | USUARIO | null |

`entidadNombre`: nombre del ítem/proyecto, email del usuario (para eliminar, leerlo antes de borrar).

- [ ] **Step 1: Tests que fallan** (uno por controlador, ejemplo):

```java
@Test void crearItemRegistraAuditoria() throws Exception {
  when(items.create(any())).thenAnswer(inv -> { Item i = inv.getArgument(0); i.setId("palo"); return i; });
  mvc.perform(post("/items").session(adminSession()).param("nombre", "Palo").param("fullId", "minecraft:stick"));
  verify(audit).registrar(any(), eq("CREAR"), eq("ITEM"), eq("palo"), eq("Palo"), isNull());
}
@Test void crearItemFallidoNoRegistra()     // create lanza ItemAlreadyExistsException → verify(audit, never()).registrar(...)
```

Equivalentes: `transicionRegistraDetalleDeEstado` (WebProyecto), `invitarRegistraDetalle` (WebProyecto), `adminCambioDeRolRegistraDetalle` (WebAdminUser), `eliminarCuentaPropiaRegistraAntesDeInvalidar` (WebProfile), `registroRegistraCreacionDeUsuario` (WebAuth), `apiEliminarItemRegistra` (ItemController).

- [ ] **Step 2:** Correr → FAIL.
- [ ] **Step 3:** Inyectar `AuditService` y agregar las llamadas de la tabla.
- [ ] **Step 4:** Suite → PASS.
- [ ] **Step 5:** Dejar listo para commit del usuario.

---

### Task 11: Verificación final

- [ ] **Step 1:** `.\gradlew.bat build --console=plain` → `BUILD SUCCESSFUL`.
- [ ] **Step 2:** Revisar que el spec esté cubierto (mundos, horno, colaborativos + ids, historial, auditoría) y que `fragments/nav.html` tenga: Mis Mundos, Historial de Crafteo, Auditoría (admin).
- [ ] **Step 3:** Entregar al usuario la lista de pruebas manuales (la app usa Firestore real: el usuario decide si levantarla): crear ítem de horno y fundir; crear mundo y asignar proyecto con coordenadas; invitar a un segundo usuario como lector y verificar que no puede editar; ver historial tras craftear; ver auditoría como admin.
