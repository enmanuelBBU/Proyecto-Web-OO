# Refactor visual del dashboard — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reemplazar la nav de `<details>` y el inicio-acordeón por una topbar horizontal accesible, un inicio con KPIs y un sistema de componentes coherente aplicado a los 23 templates.

**Architecture:** Thymeleaf server-side. Un fragmento `head`, un fragmento `nav` (topbar) que recibe la clave de la página activa como variable local, un fragmento de íconos SVG y un fragmento de badge de estado. `style.css` se reescribe por capas: tokens, base, layout, componentes, páginas y legado (recetas, 3D, toasts). Un JS chico (`nav-menu.js`) mejora los `<details>`. Los KPIs salen de un `DashboardService` nuevo en el paquete `home`.

**Tech Stack:** Spring Boot 4.1.1, Thymeleaf 3.1, JUnit 5 + MockMvc (`@WebMvcTest`) + Mockito, CSS plano, JS vanilla.

**Spec:** `docs/superpowers/specs/2026-11-25-dashboard-refactor-design.md`

## Global Constraints

- **No ejecutar `git add` / `commit` / `push` ni ninguna escritura de git.** Cada checkpoint lista los archivos para `git add` y un mensaje sugerido; el usuario commitea.
- Colores solo por tokens de la tabla del spec. No usar hex sueltos en templates ni fuera del bloque `:root` de `style.css`.
- Texto ámbar/rojo/verde/azul siempre con `--*-ink`. Los colores base se usan solo como fondo o acento.
- No dejar `style="..."` estáticos en templates. `th:style` solo para valores calculados.
- No usar emojis en la UI. Los íconos vienen de `fragments/icons :: icon(name)` con `aria-hidden="true"`.
- Copy en español y con el vocabulario de `DESIGN-SYSTEM.md` §8 (Ítem, Craftear, Constructor).
- Foco visible: `outline: 3px solid var(--amber); outline-offset: 2px`. Respetar `prefers-reduced-motion`.
- Transiciones de 150–200ms, solo de color, borde u opacidad.
- Breakpoints: topbar colapsa < 900px; KPIs a 2 columnas < 900px y a 1 columna < 480px.
- Comando de tests: `.\gradlew.bat test` (PowerShell) o `./gradlew test` (bash).
- Las rutas y los nombres de atributos de modelo existentes no cambian.

## Review Focus

1. **Falla una fuente de KPI** (Firestore caído en inventario, por ejemplo). Ese KPI muestra "—", los demás se ven y `/inicio` responde 200. Test en Task 3.
2. **`session.nombre` es null o muy largo.** La topbar muestra "Constructor" o trunca con ellipsis sin empujar los menús. El test de null va en Task 1; el largo se revisa a mano en Task 6.
3. **Interacción de menús.** Abrir uno cierra los otros. Clic afuera y `Esc` cierran. Redimensionar de móvil a escritorio con el panel abierto deja un estado limpio (panel cerrado y `aria-expanded="false"`). Se revisa a mano en Task 6.
4. **Página que no pasa `activo`.** La nav renderiza sin ítem activo y sin error. Test en Task 1.
5. **Contraste en modo oscuro** de badges, links y texto `--muted`. Se revisa a mano en Task 6 con la tabla del spec.

---

### Task 1: Base — head, topbar, íconos, badge, modelo global y CSS

**Files:**
- Create: `src/main/resources/templates/fragments/head.html`
- Create: `src/main/resources/templates/fragments/icons.html`
- Create: `src/main/resources/templates/fragments/badge.html`
- Create: `src/main/resources/static/nav-menu.js`
- Create: `src/main/java/cl/grupo5/proyectominecraft/config/GlobalModelAdvice.java`
- Modify: `src/main/resources/templates/fragments/nav.html` (reescritura completa)
- Modify: `src/main/resources/static/style.css` (reescritura completa)
- Test: `src/test/java/cl/grupo5/proyectominecraft/home/WebHomeControllerTest.java`
- Test: `src/test/java/cl/grupo5/proyectominecraft/config/GlobalModelAdviceTest.java`

**Interfaces:**
- Produces:
  - `~{fragments/head :: head(titulo)}` — `<head>` completo con charset, `viewport`, `<title>` = `titulo + ' · SGI Minecraft'`, Google Fonts (mismo URL actual), `/style.css` y `<script src="/nav-menu.js" defer>`.
  - `~{fragments/nav :: nav}` — fragmento **sin parámetros declarados**. Lee la variable `activo`, que se pasa como variable local: `~{fragments/nav :: nav(activo='items')}`. La llamada sin variable sigue funcionando.
  - Claves de `activo`: `inicio, items, materiales, calculadora, historial, modelo3d, proyectos, mundos, reportes, perfil, inventario, admin-usuarios, admin-auditoria`.
  - Grupos: Items = `items, materiales, calculadora, historial, modelo3d`; Proyectos = `proyectos, mundos, reportes`; Admin = `admin-usuarios, admin-auditoria`; Usuario = `perfil, inventario`.
  - `~{fragments/icons :: icon(name)}` — SVG 20×20 con `class="icon"` y `aria-hidden="true"`, por `th:switch`. Nombres: la lista del spec (§Componentes, Íconos).
  - `~{fragments/badge :: estado(valor)}` — `<span class="badge badge-{lower(valor)}">` con un punto y la etiqueta: `PLANIFICACION`→"Planificación", `EN_CONSTRUCCION`→"En construcción", `COMPLETADO`→"Completado", `CANCELADO`→"Cancelado". Null cuenta como `PLANIFICACION`; cualquier otro valor se muestra crudo con la clase `badge-neutral`.
  - `GlobalModelAdvice` (`@ControllerAdvice`) con `@ModelAttribute("isAdmin") boolean isAdmin(HttpSession s)` → `"ADMIN".equals(s.getAttribute("rol"))` y `@ModelAttribute("nombreUsuario") String nombreUsuario(HttpSession s)` → `session.nombre` si no está en blanco, si no `"Constructor"`.
  - Clases CSS que usan las Tasks 2–5: `.page`, `.page-header`, `.page-title`, `.page-sub`, `.section-title`, `.card`, `.card-link`, `.btn`, `.btn-primary`, `.btn-secondary`, `.btn-danger`, `.btn-ghost`, `.btn-sm`, `.badge*`, `.table-wrap`, `.num`, `.toolbar`, `.form-grid`, `.field`, `.stack`, `.row`, `.muted`, `.empty-state`, `.actions`, `.alert-error`, `.alert-ok` (los alias `.error` y `.ok` se mantienen), `.kpi-grid`, `.kpi`, `.kpi-label`, `.kpi-value`, `.kpi-sub`, `.quick-grid`, `.recent-list`.

- [ ] **Step 1: Escribir los tests que fallan**

En `WebHomeControllerTest` (usar `content().string(...)` con `containsString`/`not`):
- `inicioMarksInicioAsActiveInTopbar`: sesión USUARIO. El HTML contiene `class="topbar"` y `aria-current="page"`. (El chequeo de que no aparece `Estados de Proyecto` va en la Task 3, porque el body viejo de inicio todavía lo contiene.)
- `inicioHidesAdminMenuForRegularUser`: sesión USUARIO. El HTML no contiene `/admin/usuarios`.
- `inicioShowsAdminMenuForAdmin`: sesión ADMIN. El HTML contiene `/admin/usuarios` y `/admin/auditoria`.
- `topbarFallsBackToConstructorWhenNombreMissing`: sesión sin `nombre`. El HTML contiene `Constructor`.
- `topbarShowsSessionNombre`: `nombre="Steve"`. El HTML contiene `Steve`.

En `GlobalModelAdviceTest` (unit, sin Spring):
- `isAdminTrueOnlyForAdminRol`: `ADMIN`→true, `USUARIO`→false, null→false.
- `nombreUsuarioFallsBack`: null→`"Constructor"`, `"  "`→`"Constructor"`, `"Alex"`→`"Alex"`.

(Hasta la Task 3 `inicio.html` sigue llamando `nav` sin `activo`. En el Step 3 cambiar solo su `<head>` y la llamada de nav a `nav(activo='inicio')`; el cuerpo se rehace en la Task 3.)

- [ ] **Step 2: Correr y verificar que fallan**

Run: `.\gradlew.bat test --tests "*WebHomeControllerTest" --tests "*GlobalModelAdviceTest"`
Expected: FAIL. No compila `GlobalModelAdvice`, y no encuentra `class="topbar"`.

- [ ] **Step 3: Implementar**
  - `nav.html`: `<header class="topbar">` con `.topbar-inner` (max-width 1200px).
    - Marca: `icon('gem')` + "SGI Minecraft" → `/inicio`.
    - `<nav class="topbar-nav" id="topbar-nav" aria-label="Principal">` con el link Inicio y un `<details class="menu">` por grupo: Items, Proyectos y Admin con `th:if="${isAdmin}"`. Cada uno lleva `<summary class="menu-trigger">` con el texto y `icon('chevron-down')`, y `<ul class="menu-panel">`.
    - Menú de usuario a la derecha (`.menu.menu-user`, panel alineado a la derecha) con `nombreUsuario` en `.user-name` (max-width 16ch, ellipsis). Contiene Mi perfil, Inventario y `<form method="post" action="/logout"><button class="menu-item">Salir</button></form>`.
    - Botón `.topbar-toggle` (`icon('menu')`, `aria-controls="topbar-nav"`, `aria-expanded="false"`, `aria-label="Abrir menú"`), visible solo < 900px.
    - Link activo: `th:attrappend` / `th:attr="aria-current=${activo == 'items'} ? 'page' : null"`. Los `<summary>` llevan `th:classappend="${activo} in grupo ? 'is-active'"`; usar `${#lists.contains({'items','materiales',...}, activo)}`.
    - "Exportar PDF" va sin emoji, con `icon('file')`. "Estados de Proyecto" se elimina.
  - `nav-menu.js` (IIFE, sin dependencias):
    - En el evento `toggle` de cada `details.menu` que se abre, cerrar los demás.
    - Con `click` en `document` fuera de `.topbar`, cerrar todos y el panel móvil.
    - Con `keydown Escape`, cerrar el menú abierto y hacer `summary.focus()`; si el panel móvil está abierto, cerrarlo y devolver el foco al toggle.
    - Con clic en un `a` dentro de `.menu-panel`, cerrar ese menú.
    - El toggle móvil alterna `.topbar.is-open` y `aria-expanded`.
    - `matchMedia('(min-width: 900px)')` en `change`: quitar `is-open`, poner `aria-expanded="false"` y cerrar los menús.
  - `style.css`: reescribir en este orden y con estos comentarios de sección: `/* tokens */`, `/* base */`, `/* layout */`, `/* topbar */`, `/* components */`, `/* dashboard */`, `/* auth */`, `/* recipes */`, `/* modelo-3d */`, `/* toasts */`, `/* proyectos */`.
    - Valores de tokens y componentes: los del spec.
    - Copiar las secciones auth, recipes, modelo-3d y toasts actuales, reemplazando los colores por tokens (`--card` queda como alias de `--surface`).
    - Quitar el `max-width: 860px` del `body`; el ancho lo fija `.page` (max-width 1200px, `margin: 0 auto`, padding 24px / 16px < 640px).
    - Topbar: `position: sticky; top: 0; z-index: 50`, alto 56px, `--surface` con borde inferior. `.menu-panel` es absoluto con `z-index: 60`, `--shadow-pop`, `min-width: 220px` y `.menu-item` de 40px de alto.
    - Bajo 900px: `.topbar-nav` se oculta; con `.topbar.is-open` pasa a columna bajo la barra, los `.menu-panel` son estáticos (sin sombra) y los `details` se muestran como listas.
    - Todos los textos de color usan `--*-ink`.
  - `head.html`, `icons.html`, `badge.html` y `GlobalModelAdvice.java`: según las Interfaces.
  - `inicio.html`: solo el `<head>` → `th:replace="~{fragments/head :: head('Inicio')}"` y la nav → `nav(activo='inicio')`.

- [ ] **Step 4: Correr y verificar que pasan, junto con la suite completa**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL. Los demás `Web*ControllerTest` siguen pasando; la nav sin `activo` no rompe ninguna página.

- [ ] **Step 5: Checkpoint (sin git)**

Reportar al usuario:
`git add src/main/resources/templates/fragments/head.html src/main/resources/templates/fragments/icons.html src/main/resources/templates/fragments/badge.html src/main/resources/templates/fragments/nav.html src/main/resources/templates/inicio.html src/main/resources/static/nav-menu.js src/main/resources/static/style.css src/main/java/cl/grupo5/proyectominecraft/config/GlobalModelAdvice.java src/test/java/cl/grupo5/proyectominecraft/home/WebHomeControllerTest.java src/test/java/cl/grupo5/proyectominecraft/config/GlobalModelAdviceTest.java`
Mensaje: `feat(ui): topbar accesible, tokens y sistema de componentes base`

---

### Task 2: DashboardService (KPIs)

**Files:**
- Create: `src/main/java/cl/grupo5/proyectominecraft/home/DashboardResumen.java`
- Create: `src/main/java/cl/grupo5/proyectominecraft/home/DashboardService.java`
- Test: `src/test/java/cl/grupo5/proyectominecraft/home/DashboardServiceTest.java`

**Interfaces:**
- Consumes:
  - `ProyectoService.list(String q, String estado, String creadorUid, String viewerUid, String viewerRol)`, llamado como `list(null, null, null, uid, rol)`.
  - `InventoryService.get(String uid): Map<String, Long>`.
  - `HistorialCrafteoService.listar(String uid): List<RegistroCrafteo>`.
  - `MundoService.listar(String uid): List<Mundo>`.
- Produces:
  - `record DashboardResumen(Integer proyectosActivos, Integer proyectosTotal, Long unidadesInventario, Integer tiposInventario, Integer crafteos, Integer mundos, List<Proyecto> recientes)`. Un campo null significa que su fuente falló; `recientes` nunca es null.
  - `@Service DashboardService(ProyectoService, InventoryService, HistorialCrafteoService, MundoService)` con `DashboardResumen resumen(String uid, String rol)`.
  - Estáticos puros, para testear sin mocks: `static int contarActivos(List<Proyecto>)`, `static List<Proyecto> recientes(List<Proyecto>, int limite)`, `static long unidades(Map<String, Long>)` y `static int tipos(Map<String, Long>)`.

- [ ] **Step 1: Escribir los tests que fallan** (`DashboardServiceTest`, Mockito plano)
  - `contarActivosIncluyePlanificacionEnConstruccionYNull`: estados `[PLANIFICACION, EN_CONSTRUCCION, null, COMPLETADO, CANCELADO]` → `3`.
  - `recientesOrdenaDescYLimita`: 7 proyectos con `fechaCreacion` ISO distintas más 1 con null, límite 5. Devuelve 5, el primero es la fecha mayor y el de null no aparece.
  - `recientesConNullAlFinal`: `["2026-01-01", null]`, límite 5 → el orden es `["2026-01-01", null]`.
  - `unidadesYTiposIgnoranCeroYNegativos`: `{a:3, b:0, c:5, d:-1}` → unidades `8`, tipos `2`.
  - `resumenAgregaTodasLasFuentes`: los mocks devuelven 2 proyectos activos de 3, inventario `{a:4}`, 2 registros y 1 mundo → `DashboardResumen(2, 3, 4L, 1, 2, 1, [...])`.
  - `resumenToleraFallaDeUnaFuente`: `inventory.get` lanza `RuntimeException` → `unidadesInventario` y `tiposInventario` quedan null, y el resto viene poblado.
  - `resumenToleraFallaDeProyectos`: `proyectoService.list` lanza → `proyectosActivos` y `proyectosTotal` null, y `recientes` vacío.

- [ ] **Step 2: Correr y verificar que fallan**

Run: `.\gradlew.bat test --tests "*DashboardServiceTest"`
Expected: FAIL (no compila: `DashboardService` no existe).

- [ ] **Step 3: Implementar `DashboardResumen` y `DashboardService`**

Cada fuente va en su propio `try/catch (Exception e)`. Si falla, se registra con `org.slf4j.Logger.warn` y sus campos quedan null. `recientes` ordena por `fechaCreacion` como string ISO, desc, con `Comparator.nullsLast`.

- [ ] **Step 4: Correr y verificar que pasan**

Run: `.\gradlew.bat test --tests "*DashboardServiceTest"`
Expected: PASS (7 tests).

- [ ] **Step 5: Checkpoint (sin git)**

`git add src/main/java/cl/grupo5/proyectominecraft/home/DashboardResumen.java src/main/java/cl/grupo5/proyectominecraft/home/DashboardService.java src/test/java/cl/grupo5/proyectominecraft/home/DashboardServiceTest.java`
Mensaje: `feat(home): DashboardService con KPIs tolerantes a fallas`

---

### Task 3: Inicio como dashboard

**Files:**
- Modify: `src/main/java/cl/grupo5/proyectominecraft/home/WebHomeController.java`
- Modify: `src/main/resources/templates/inicio.html` (reescritura del body)
- Test: `src/test/java/cl/grupo5/proyectominecraft/home/WebHomeControllerTest.java`

**Interfaces:**
- Consumes: `DashboardService.resumen(uid, rol)` y `DashboardResumen` (Task 2); los fragmentos `head`, `nav`, `icons` y `badge`, y las clases `.kpi*`, `.quick-grid`, `.recent-list` y `.empty-state` (Task 1).
- Produces: atributo de modelo `resumen` (`DashboardResumen`) en la vista `inicio`.

- [ ] **Step 1: Escribir los tests que fallan**

Agregar `@MockitoBean DashboardService dashboard` al test. Cuando el mock no está configurado devuelve null; en esos tests hacer stub con `new DashboardResumen(0,0,0L,0,0,0,List.of())`.
- `inicioExponeResumen`: el stub devuelve `(2,5,40L,3,7,1,List.of())` → `model().attribute("resumen", ...)`, y el HTML contiene `40` y `de 5 en total`.
- `inicioMuestraGuionCuandoKpiNulo`: `unidadesInventario=null` → status 200 y el HTML contiene `—`.
- `inicioSinProyectosMuestraEstadoVacio`: `recientes=List.of()` → el HTML contiene `Crear proyecto`.
- `inicioListaRecientesConBadge`: un proyecto con nombre `Granja de hierro` y estado `EN_CONSTRUCCION` → el HTML contiene `Granja de hierro` y `En construcción`.
- `inicioNoUsaAcordeon`: el HTML no contiene `home-card-group` ni `Estados de Proyecto`.
- Los tests de redirect e `isAdmin` existentes deben seguir pasando.

- [ ] **Step 2: Correr y verificar que fallan**

Run: `.\gradlew.bat test --tests "*WebHomeControllerTest"`
Expected: FAIL (no existe el atributo `resumen`).

- [ ] **Step 3: Implementar**
  - Inyectar `DashboardService` por constructor. Agregar `m.addAttribute("resumen", dashboard.resumen(uid, rol))` y mantener `isAdmin`.
  - `inicio.html`, dentro de `<main class="page">`:
    - `page-header` con "Hola, [[${nombreUsuario}]]" y "Resumen de tu actividad".
    - `.kpi-grid` con 4 `a.card.card-link.kpi` en el orden y con los textos del spec. El valor usa `${v != null ? v : '—'}`.
    - `section` "Accesos rápidos" con un `.quick-grid` de 6 `a.card.card-link` (ícono, título, una línea).
    - `section` "Proyectos recientes": una `ul.recent-list` con el nombre (link a `/proyectos/{id}`), `badge :: estado(${p.estado})` y `p.creadorNombre` en `.muted`, o un `.empty-state` con `a.btn.btn-primary` "Crear proyecto" → `/proyectos`.
    - Toast fragment al final, como hoy.
  - Borrar del CSS las reglas `.home-grid`, `.home-card*` y `.home-card-group*` si la Task 1 las copió.

- [ ] **Step 4: Correr y verificar que pasan**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Checkpoint (sin git)**

`git add src/main/java/cl/grupo5/proyectominecraft/home/WebHomeController.java src/main/resources/templates/inicio.html src/test/java/cl/grupo5/proyectominecraft/home/WebHomeControllerTest.java src/main/resources/static/style.css`
Mensaje: `feat(home): inicio con KPIs, accesos rápidos y proyectos recientes`

---

### Task 4: Migrar páginas de Items, Usuario y Admin

**Files (Modify):** `items.html`, `item-edit.html`, `materiales.html`, `calculadora.html`, `historial-crafteo.html`, `modelo-3d.html`, `inventario.html`, `perfil.html`, `admin-usuarios.html`, `admin-usuario-edit.html`, `admin-auditoria.html`, `login.html` y `register.html`, todos en `src/main/resources/templates/`. También `style.css` si hace falta una clase de página.

**Interfaces:**
- Consumes: los fragmentos y las clases de la Task 1. Claves de `activo` por página: items/item-edit → `items`, materiales → `materiales`, calculadora → `calculadora`, historial-crafteo → `historial`, modelo-3d → `modelo3d`, inventario → `inventario`, perfil → `perfil`, admin-usuarios/admin-usuario-edit → `admin-usuarios`, admin-auditoria → `admin-auditoria`.

Receta por página (la misma para las Tasks 4 y 5):
1. Reemplazar `<head>…</head>` por `<head th:replace="~{fragments/head :: head('<título actual sin emoji ni sufijo>')}"></head>`.
2. Mover la nav al primer hijo de `<body>` con `nav(activo='<clave>')` y envolver el resto en `<main class="page">`.
3. Cambiar el `<h1>` por `<header class="page-header"><div><h1 class="page-title">…</h1><p class="page-sub">…</p></div><div class="actions">[acción primaria si existe]</div></header>`, sin emoji.
4. Reemplazar cada `style="..."` estático por una clase existente (`.toolbar`, `.row`, `.stack`, `.muted`, `.form-grid`, `.actions`, `.empty-state`, `.num`). Si ninguna sirve, crear una clase con prefijo de página en la sección `/* proyectos */` o `/* components */`.
5. Botones: una acción primaria por vista → `.btn-primary`. Filtrar/Buscar/Cancelar → `.btn-secondary`; eliminar → `.btn-danger`; `btn-info` → `btn-secondary`. Los submits de formularios principales llevan `.btn btn-primary` explícito.
6. Envolver toda `<table>` en `<div class="table-wrap">`; las columnas de cantidad llevan `class="num"`.
7. `.error`/`.ok` → `.alert-error`/`.alert-ok` y conservar `role="alert"` donde exista.
8. Mantener ids, `name`, `th:*` de datos y scripts al final del body sin cambios: el JS (`items-search.js`, `recipe-*.js`, `modelo3d.js`) depende de ellos.

`login.html`/`register.html`: solo el paso 1 (head) y revisar que `.auth-shell` se vea igual con los tokens nuevos. No llevan nav.

- [ ] **Step 1: Escribir el test de humo que falla**

Crear `src/test/java/cl/grupo5/proyectominecraft/ui/TemplateHygieneTest.java` (unit, lee los archivos de `src/main/resources/templates` con `java.nio.file.Files`):
- `noStaticInlineStyles`: ningún template contiene la regex `(?<!th:)style="` (los fragmentos se incluyen). Para la Task 4, parametrizar con la lista de archivos de esta task; la Task 5 agrega los suyos.
- `noEmojiInHeadings`: ningún `<h1…>…</h1>` contiene caracteres con `Character.getType == OTHER_SYMBOL` o surrogates (`[\x{1F300}-\x{1FAFF}\x{2600}-\x{27BF}]`).
- `everyPageUsesHeadFragment`: todo template fuera de `fragments/` contiene `fragments/head :: head(`.

- [ ] **Step 2: Correr y verificar que falla**

Run: `.\gradlew.bat test --tests "*TemplateHygieneTest"`
Expected: FAIL, listando los archivos con `style="`, emoji o head manual.

- [ ] **Step 3: Aplicar la receta a los 13 templates**

- [ ] **Step 4: Correr la suite completa**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL. `TemplateHygieneTest` pasa para los archivos de esta task y los `Web*ControllerTest` siguen verdes.

- [ ] **Step 5: Checkpoint (sin git)**

`git add` los 13 templates listados, `src/main/resources/static/style.css` y `src/test/java/cl/grupo5/proyectominecraft/ui/TemplateHygieneTest.java`.
Mensaje: `refactor(ui): migrar páginas de items, usuario y admin al sistema nuevo`

---

### Task 5: Migrar páginas de Proyectos y Mundos

**Files (Modify):** `proyectos.html`, `proyecto-detalle.html`, `proyecto-edit.html`, `proyecto-estados.html`, `reportes.html`, `mundos.html`, `mundo-detalle.html`, `mundo-edit.html` y `fragments/ubicacion.html`, todos en `src/main/resources/templates/`. También `style.css` y `TemplateHygieneTest.java`.

**Interfaces:**
- Consumes: la receta de la Task 4 y `badge :: estado(valor)` de la Task 1. Claves de `activo`: proyecto* → `proyectos`, mundo* → `mundos`, reportes → `reportes`.

Específico de esta task:
- Todo badge de estado de proyecto (`th:class="'badge badge-' + …"` + `th:text="${p.estado}"`) se reemplaza por `<span th:replace="~{fragments/badge :: estado(${p.estado})}"></span>`.
- `proyectos.html`: el formulario de filtros pasa a `.card` > `form.toolbar`, con el input de búsqueda en `.toolbar-grow`. El grid `.project-grid` se mantiene; las cartas son `.card` sin el hover de borde ámbar, solo `.card-link` cuando la carta completa es clicable. El título "Listado de Proyectos" se cambia por un `h2.section-title`.
- `proyecto-estados.html` (27 inline): la línea de tiempo del historial pasa a `ol.timeline` > `li.timeline-item`, con clases nuevas en `/* proyectos */`: punto de color por estado con el mismo mapeo del badge, fecha en mono `--muted` y motivo debajo.
- `proyecto-detalle.html`: el bloque de datos pasa a `dl.meta-grid` (2 columnas desde 640px) y las acciones al `page-header .actions`.

- [ ] **Step 1: Extender `TemplateHygieneTest` con los 9 archivos de esta task y agregar `estadoBadgesUseFragment`**

El test verifica que ningún template contiene `th:text="${p.estado}"` dentro de un `badge`. Se busca con la regex `class="[^"]*badge[^"]*"[^>]*th:text="\$\{[a-z]+\.estado\}"` y también con el orden inverso de atributos.

- [ ] **Step 2: Correr y verificar que falla**

Run: `.\gradlew.bat test --tests "*TemplateHygieneTest"`
Expected: FAIL en los archivos de proyectos y mundos.

- [ ] **Step 3: Aplicar la receta y los puntos específicos a los 9 archivos**

- [ ] **Step 4: Correr la suite completa**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL. `WebProyectoControllerTest`, `WebMundoControllerTest` y `ProyectoPdfControllerTest` siguen verdes.

- [ ] **Step 5: Checkpoint (sin git)**

`git add` los 9 templates listados, `src/main/resources/static/style.css` y `src/test/java/cl/grupo5/proyectominecraft/ui/TemplateHygieneTest.java`.
Mensaje: `refactor(ui): migrar páginas de proyectos y mundos, badges con etiqueta`

---

### Task 6: Verificación visual y cierre

**Files:** ninguno nuevo. Se corrige en el archivo que corresponda si aparece un defecto.

- [ ] **Step 1: Barrido estático**

Run (bash): `grep -rnE '(^|[^:])style="' src/main/resources/templates ; grep -rn "btn-info\|home-card-group\|📄\|🏗\|🌍" src/main/resources/templates`
Expected: sin salida.

- [ ] **Step 2: Levantar la app**

Run: `.\gradlew.bat bootRun`, o la skill `run` del proyecto.
Expected: arranca y `/login` responde.

- [ ] **Step 3: Checklist manual** (Chrome, escritorio 1280px y móvil 375px, tema claro y oscuro)
  - [ ] Abrir Items y luego Proyectos: solo queda uno abierto. Clic afuera cierra; `Esc` cierra y devuelve el foco al summary.
  - [ ] Móvil: la hamburguesa abre el panel. Al elegir un link navega y el panel se cierra. Al pasar a 1280px con el panel abierto, la barra queda normal.
  - [ ] La página actual se marca en la topbar en cada una de las 13 claves.
  - [ ] Con un nombre de 40 caracteres en el perfil, la topbar trunca con "…" sin saltar de línea.
  - [ ] Inicio: 4 KPIs alineados; 2 columnas en 768px y 1 en 375px. Los proyectos recientes muestran badges con etiqueta legible.
  - [ ] Modo oscuro: links ámbar, badges y texto `--muted` legibles (comparar con la tabla del spec).
  - [ ] No hay scroll horizontal a 375px en ninguna página; las tablas hacen scroll dentro de `.table-wrap`.
  - [ ] El editor de recetas (`item-edit`), el visor 3D y los toasts funcionan igual que antes.
  - [ ] Navegación completa solo con teclado: foco visible en cada link, summary y botón.

- [ ] **Step 4: Suite completa**

Run: `.\gradlew.bat test`
Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Reporte al usuario**

Entregar la lista de defectos encontrados y corregidos, lo pendiente para la vuelta de correcciones y los archivos para `git add` con el mensaje sugerido. No ejecutar git.
