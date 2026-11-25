# Funcionalidades encima (A–D) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Faltantes de proyecto con horno e intermedios, comentarios, calculadora Nether + portales y dashboard de estadísticas.

**Architecture:** Lógica pura `static` en servicios (motor de crafteo, reglas de comentarios, portales, agregaciones), persistencia Firestore delgada, controladores web/API con chequeo de sesión manual, vistas Thymeleaf con fragments.

**Tech Stack:** Java 17, Spring Boot 4 (webmvc), Lombok, Firestore, Thymeleaf, JUnit 5 + AssertJ + MockMvc, Chart.js 4.4.1 (cdnjs).

**Spec:** `docs/superpowers/specs/2026-11-25-funcionalidades-encima-design.md`

## Global Constraints

- TDD estricto; verificación: `gradlew.bat test` verde al cerrar cada task.
- Sin dependencias Gradle nuevas; front solo CDN con versión exacta (`https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.min.js`).
- Campos nuevos de Firestore toleran `null`.
- Vistas: fragments `head/nav/badge/icons/toast`, sin `style="..."`, sin emojis, solo `th:text` (nunca `th:utext`). Toda página nueva entra en `TemplateHygieneTest.paginas()`.
- Proyecto no visible ⇒ web `redirect:/proyectos` + `toastError "Proyecto no encontrado."`; API 404.
- **Sin git** (regla del usuario): en vez de commit, el paso final de cada task es correr la suite.

## Review Focus

1. Mundo editado desde el formulario pierde sus portales → `MundoServiceTest.actualizarPreservaPortales` (Task 6).
2. Proyecto con un ítem borrado del catálogo rompe el detalle → `detailMuestraErrorDeFaltantesSinRomper` (Task 2).
3. Comentario de solo espacios o 1001 caracteres → `normalizarTextoRechazaVacioYLargo` (Task 3).
4. Re-enlazar un portal ya enlazado deja al antiguo par apuntándolo → `enlazarSueltaParesAnteriores` (Task 6).
5. Un proyecto roto en estadísticas consume stock compartido antes de fallar → `escasosOmiteProyectoQueFallaSinConsumirStock` (Task 8).

---

### Task 1: Motor de faltantes + calculadora/craft sobre el motor

**Files:**
- Create: `inventario/NodoCrafteo.java`, `inventario/MaterialCantidad.java`, `inventario/ResultadoFaltantes.java`, `inventario/Demanda.java` (record), `templates/fragments/arbol.html`
- Modify: `inventario/CraftingCalculatorService.java` (quita `expandRequirements`/`toPlan`), `inventario/CraftingPlan.java`, `inventario/HistorialCrafteoService.java`, `templates/calculadora.html`, `static/style.css` (`.tree`, `.alert-info`)
- Test: `CraftingCalculatorServiceTest` (reescrito), `HistorialCrafteoServiceTest`, `CraftingControllerTest`, `WebCraftingControllerTest`

**Interfaces (Produces):**
- `public record Demanda(String itemId, long cantidad)`
- `@Data NodoCrafteo { String itemId, nombre; long requerido, usadoDeInventario, aFabricar, faltante; String tipo; List<NodoCrafteo> hijos }`; constantes `MATERIA_PRIMA`, `CRAFTEO`, `HORNO`.
- `@Data MaterialCantidad { String itemId, nombre; long cantidad }` (+ all-args ctor)
- `@Data ResultadoFaltantes { List<NodoCrafteo> arbol; List<MaterialCantidad> consumidosDeInventario, faltantes; boolean usaIntermedios }`
- `public static ResultadoFaltantes calcular(List<Demanda>, RecetaFuente, Map<String,Long> stock)` (stock mutado)
- `static ResultadoFaltantes calcularCrafteo(String targetId, long cantidad, RecetaFuente, Map<String,Long> stock)` (raíz no usa stock)
- `static Map<String,Long> aplicarConsumo(Map<String,Long> actual, List<MaterialCantidad> consumidos, String targetId, long cantidad)` lanza `InventoryException("No tienes suficientes materiales.")`
- `public interface RecetaFuente { Item receta(String id) throws Exception; }`
- `CraftingPlan` + `arbol`, `consumidos`, `faltantes`, `usaIntermedios`.
- `public ResultadoFaltantes faltantesProyecto(Proyecto p, String uid)`

- [ ] Tests (fallan): `arbolConStockIntermedioParcial` (pala; stock tablones=1 ⇒ nodo tablones requerido 2, usado 1, aFabricar 1; tronco faltante 1+…; usaIntermedios true), `stockIntermedioTotalNoExpande` (tablones stock suficiente ⇒ hijos vacíos), `stockCompartidoEntreDosDemandas` (dos demandas usan tablones; stock 2 ⇒ primera consume 2, segunda 0), `hornoAnidadoEnCrafteo` (cubeta ← 3 lingote; lingote HORNO ← hierro crudo ⇒ nodo tipo HORNO con hijo hierro_crudo requerido 3), `itemInexistenteLanza`, `limiteDeExpansionLanza` (ciclo a→a), `calcularCrafteoNoUsaStockDelObjetivo`, `aplicarConsumoDescuentaIntermedios`, `aplicarConsumoRevalidaStock`, `consumidosYFaltantesAgregados`.
- [ ] Implementar motor DFS recursivo con contador de 1000; agregación `LinkedHashMap`.
- [ ] `plan()` usa `calcularCrafteo` con catálogo de `items::get`; `materiales` derivados; `craft()` usa `aplicarConsumo` en la tx; `crearRegistro` mapea `plan.getConsumidos()`.
- [ ] `fragments/arbol.html :: nodo(n)` recursivo (`th:block th:each` + `th:replace`), usado en `calculadora.html` junto con tablas "Se descontará de tu inventario" y "Faltantes".
- [ ] `gradlew.bat test` verde.

### Task 2: Faltantes en detalle de proyecto + API

**Files:** Modify `WebProyectoController` (detail + `faltantes`, `faltantesError`), `ProyectoController` (`GET /{id}/faltantes`), `proyecto-detalle.html`. Tests: `WebProyectoControllerTest`, `ProyectoControllerTest` (mock `CraftingCalculatorService`).

- [ ] Tests: `detailExponeFaltantes`, `detailMuestraBannerYTienesTodo`, `detailMuestraErrorDeFaltantesSinRomper` (calculator lanza `InventoryException` ⇒ 200 + mensaje), `apiFaltantesNoVisible404`, `apiFaltantesOk`.
- [ ] Sección "Faltantes": banner exacto "Se descuenta primero lo que ya tienes en tu inventario, incluidos ítems intermedios", árbol, tabla faltantes, vacío / "Tienes todo".
- [ ] Suite verde.

### Task 3: Comentarios — modelo, reglas, servicio

**Files:** Create `proyectos/Comentario.java`, `proyectos/ComentarioService.java`, `proyectos/ComentarioValidationException.java`; Modify `ProyectoService.delete` (WriteBatch borra subcolección), `AuditService` (+ `COMENTARIO`), `WebAuditoriaController` (entidades). Test: `ComentarioServiceTest`.

**Produces:** `static String normalizarTexto(String)`; `static boolean puedeEditar(Comentario, String uid)`; `static boolean puedeBorrar(Comentario, Proyecto, String uid, String rol)`; `static List<Comentario> ordenar(List<Comentario>)`; `listar(String proyectoId)`, `get(String proyectoId, String cid)`, `crear(String proyectoId, String uid, String nombre, String texto)`, `editar(String proyectoId, Comentario c, String texto)`, `eliminar(String proyectoId, String cid)`. Subcolección `proyectos/{id}/comentarios`.

- [ ] Tests: `normalizarTextoRechazaVacioYLargo` (`"   "`, 1001 chars ⇒ excepción; 1000 ok; trim), `soloAutorEdita`, `borranAutorCreadorYAdmin` (miembro EDICION no autor ⇒ false), `ordenAscendenteNullsAlFinal`.
- [ ] Suite verde.

### Task 4: Comentarios — web + API + vista

**Files:** Create `proyectos/WebComentarioController.java`, `proyectos/ComentarioController.java`; Modify `WebProyectoController.detail` (comentarios, `comentariosEditables`, `comentariosBorrables`), `proyecto-detalle.html`. Tests: `WebComentarioControllerTest`, `ComentarioControllerTest`.

- [ ] Tests web: sin sesión ⇒ login; no visible ⇒ `/proyectos`; crear ok ⇒ redirect detalle + toast; texto vacío ⇒ toastError; editar ajeno ⇒ toastError; borrar ajeno por creador ⇒ audit `ELIMINAR COMENTARIO`; borrar propio ⇒ sin audit.
- [ ] Tests API: GET 404 no visible; POST 201; PUT 403 no autor; DELETE 404 cid inexistente; POST 400 vacío.
- [ ] Vista: lista, "(editado)", `<details>` editar, borrar con confirm, formulario al pie.
- [ ] Suite verde.

### Task 5: Nether — conversión pura, API y página

**Files:** Create `mundos/Dimension.java`, `mundos/NetherCalculator.java`, `mundos/Coordenadas.java` (record), `mundos/NetherController.java` (`/api/nether/convertir`), `mundos/WebNetherController.java` (`/calculadora-nether`), `templates/calculadora-nether.html`, `templates/fragments/nether.html` (formulario + resultado reutilizable); Modify `fragments/nav.html`. Tests: `NetherCalculatorTest`, `NetherControllerTest`, `WebNetherControllerTest`.

**Produces:** `static Coordenadas convertir(Dimension origen, int x, int y, int z)` (lanza `MundoValidationException`); `static void validar(Dimension, int x, int y, int z)`; `static int acotarY(Dimension, int y)`; `record Coordenadas(String dimension, int x, int y, int z)`.

- [ ] Tests: `x=-1 ⇒ -1`, `x=-9 ⇒ -2`, `x=8 ⇒ 1`, `netherAOverworldMultiplica`, `yOverworldFueraDeRango` (-65, 321), `yNetherFueraDeRango` (-1, 256), `xzFueraDeRango` (30_000_001; nether 3_750_001), `dimensionInvalida`.
- [ ] Suite verde.

### Task 6: Portales en mundo

**Files:** Create `mundos/Portal.java`, `mundos/PortalService.java`, `mundos/PortalController.java`; Modify `Mundo` (+`portales`), `MundoService.actualizar` (preserva portales), `WebMundoController` (rutas portales + calculadora en detalle), `mundo-detalle.html`. Tests: `PortalServiceTest`, `MundoServiceTest`, `WebMundoControllerTest`, `PortalControllerTest`.

**Produces (static):** `Portal normalizar(Portal)`; `void agregar(Mundo, Portal)`; `void actualizar(Mundo, String pid, Portal cambios)`; `void eliminar(Mundo, String pid)`; `void enlazar(Mundo, String aId, String bId)`; `void desenlazar(Mundo, String pid)`; `Portal crearPareado(Mundo, String pid)`; `Optional<PortalCercano> masCercano(List<Portal>, int x, int z)` con `record PortalCercano(Portal portal, long distancia)`. Instancia: `List<Portal> listar(Mundo)`, `Mundo guardar(Mundo)`.

- [ ] Tests: `enlazarMismaDimensionFalla`, `enlazarEsBidireccional`, `enlazarSueltaParesAnteriores`, `eliminarLimpiaEnlaceDelOtro`, `crearPareadoConvierteYEnlaza` (OW 100,70,-9 ⇒ NETHER 12,70,-2), `pareadoAcotaY`, `masCercanoSoloOverworld`, `actualizarPreservaPortales`, web: no dueño ⇒ `/mundos`; crear ⇒ redirect; API: GET 404 ajeno, POST 201, DELETE 204.
- [ ] Suite verde.

### Task 7: Nether en detalle de proyecto

**Files:** Modify `WebProyectoController.detail` (`netherEquivalente`, `portalCercano`), `proyecto-detalle.html`. Test: `WebProyectoControllerTest.detailMuestraNetherYPortalCercano`.

- [ ] Suite verde.

### Task 8: Estadísticas

**Files:** Create `estadisticas/EstadisticasService.java`, `estadisticas/Estadisticas.java`, `estadisticas/EntradaRanking.java`, `estadisticas/WebEstadisticasController.java`, `templates/estadisticas.html`, `static/estadisticas.js`; Modify nav. Tests: `EstadisticasServiceTest`, `WebEstadisticasControllerTest`.

**Produces:** `static Map<String,Integer> porEstado(List<Proyecto>)`; `static List<EntradaRanking> itemsMasUsados(List<Item>, int)`; `static List<EntradaRanking> materialesEscasos(List<Proyecto>, Map<String,Item>, Map<String,Long>, int)`; `Estadisticas resumen(uid, rol)` (campos null = fuente caída); `static Map<String,Object> datosGraficos(Estadisticas)`.

- [ ] Tests: `porEstadoNullCuentaPlanificacion`, `masUsadosCuentaRecetasDistintasYDesempata`, `masUsadosTop10`, `escasosUsaStockCompartidoYSoloActivos`, `escasosOmiteProyectoQueFallaSinConsumirStock`, controlador: login, 200 con datos, "—" con fuente nula, Chart.js 4.4.1 en HTML.
- [ ] Suite verde final.
