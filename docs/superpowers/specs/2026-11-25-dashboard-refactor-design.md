# Refactor visual del dashboard — Diseño

Fecha: 2026-11-25 · Estado: aprobado para planificar

## Decisiones del usuario

- Navegación: **topbar horizontal** (no sidebar), con menús que se cierran bien.
- Inicio: **con KPIs** (se permite tocar `WebHomeController`).
- Ejecución: **todo de una vez**. Una vuelta de desarrollo y luego una vuelta de dudas/correcciones.
- Esta sesión es solo planificación; la implementación ocurre en otra sesión.

## Problemas a resolver

1. Desplegables `<details>` en la nav: no se cierran con clic afuera ni con Esc, y varios quedan abiertos a la vez, solapados.
2. Las cartas de inicio son acordeones. Esconden los links y desalinean la grilla al abrirse.
3. Todos los links de inicio son `btn-info` azul. Se rompe la jerarquía y el azul pierde su significado ("info/pendiente").
4. Hay emojis como íconos en los `h1` y en "📄 Exportar PDF". `DESIGN-SYSTEM.md` §5 lo prohíbe.
5. Hay 104 atributos `style="..."` estáticos repartidos en 15 templates.
6. Layout de una sola columna de 860px, con el `h1` encima de la nav.
7. "Estados de Proyecto" apunta a `/proyectos` (la vista es por proyecto: `/proyectos/{id}/estados`). La página actual no se marca en la nav.
8. Los badges muestran el enum crudo (`EN_CONSTRUCCION`). El texto ámbar sobre blanco (`#D69A2D`) tiene ~2.4:1 de contraste y falla AA.
9. 21 de 23 páginas no tienen `<meta name="viewport">`. Fuentes y CSS se repiten a mano en cada `<head>`.

## Diseño

### Estructura de página

```
┌────────────────────────────────────────────────────────────────┐
│ ◆ SGI Minecraft   Inicio  Items ▾  Proyectos ▾  Admin ▾   Steve ▾│  topbar 56px, sticky
├────────────────────────────────────────────────────────────────┤
│  Inicio                                      [acción primaria]  │  page-header
│  Subtítulo breve en gris                                        │
│                                                                 │
│  contenido (max-width 1200px, padding 24px / 16px móvil)        │
└────────────────────────────────────────────────────────────────┘
```

- **Topbar.** A la izquierda va la marca (gema SVG + "SGI Minecraft", link a `/inicio`). Al centro, grupos: Inicio (link directo), Items ▾, Proyectos ▾ y Admin ▾ (solo si `isAdmin`). A la derecha, un menú de usuario con `${session.nombre}` (fallback "Constructor") y las opciones Mi perfil, Inventario y Salir.
- Contenido de los menús:
  - Items: Catálogo, Materiales, Calculadora, Historial de crafteo, Modelo 3D.
  - Proyectos: Proyectos, Mis mundos, Exportar PDF.
  - Admin: Usuarios, Auditoría.
  - "Estados de Proyecto" se elimina de la nav porque se llega desde el detalle de cada proyecto.
- **Comportamiento de los desplegables** (`nav-menu.js`, mejora progresiva sobre `<details>`):
  - Abrir uno cierra los demás.
  - Un clic fuera del menú cierra todos.
  - `Esc` cierra el abierto y devuelve el foco a su `<summary>`.
  - Elegir un link cierra el menú.
  - Sin JS siguen funcionando como `<details>`.
- **Página activa.** Cada página pasa una clave a la nav. El link activo lleva `aria-current="page"` y el `<summary>` de su grupo lleva la clase `is-active` (subrayado ámbar de 2px).
- **Móvil (< 900px).** Los grupos se ocultan y aparece un botón hamburguesa (`aria-expanded`, `aria-controls`). El panel se despliega bajo el topbar con los grupos expandidos como listas estáticas. Se cierra con Esc, con clic afuera o al elegir un link.

### Inicio (dashboard)

1. `page-header`: título "Hola, {nombre}" y subtítulo "Resumen de tu actividad".
2. Fila de 4 KPIs (grid de 4 columnas, 2 bajo 900px, 1 bajo 480px):
   - **Proyectos activos**: proyectos visibles con estado `PLANIFICACION` o `EN_CONSTRUCCION`. Estado null cuenta como `PLANIFICACION`. Sub-texto "de N en total".
   - **Ítems en inventario**: suma de cantidades > 0. Sub-texto "N tipos distintos".
   - **Crafteos**: tamaño del historial del usuario. Sub-texto "registrados".
   - **Mundos**: mundos del usuario. Sub-texto "creados".
   - Cada KPI enlaza a su página (`/proyectos`, `/inventario`, `/historial-crafteo`, `/mundos`).
   - Si la fuente de un KPI falla, ese KPI muestra "—" y la página sigue respondiendo 200.
3. **Accesos rápidos**: grid de cartas-link planas (ícono SVG + título + una línea de descripción), cada una clicable entera. Las cartas son Catálogo de ítems, Calculadora, Inventario, Proyectos, Mis mundos y Exportar PDF.
4. **Proyectos recientes**: hasta 5 proyectos visibles ordenados por `fechaCreacion` desc (nulls al final). Cada fila muestra nombre (link), badge de estado y mundo/creador. Si no hay proyectos, un estado vacío con el botón primario "Crear proyecto" → `/proyectos`.

### Tokens (reemplazan el `:root` actual de `style.css`)

| Token | Claro | Oscuro | Uso |
|---|---|---|---|
| `--amber` | `#D69A2D` | `#D69A2D` | fondo de botón primario, acento, foco |
| `--amber-ink` | `#8A5A0B` | `#E0AE52` | texto/links ámbar (AA ≥ 4.5:1) |
| `--red` / `--green` / `--blue` | sin cambio | sin cambio | semánticos |
| `--red-ink` / `--green-ink` / `--blue-ink` | `#A3361F` / `#2B7344` / `#245A91` | `#E57A64` / `#6BC78A` / `#7AAEE6` | texto de badges/alertas |
| `--bg` | `#EDEFF2` | `#1B1E22` | fondo página |
| `--surface` | `#FFFFFF` | `#262A30` | cartas, topbar, menús |
| `--surface-2` | `#F6F7F9` | `#2E3238` | hover de filas, cabecera de tabla |
| `--fg` | `#23262B` | `#EDEFF2` | texto |
| `--muted` | `#5F636A` | `#A4A9B1` | texto secundario (≥ 4.5:1 sobre surface) |
| `--border` | `color-mix(in srgb, var(--fg) 12%, transparent)` | igual | bordes |
| `--shadow-1` | `0 1px 2px rgb(0 0 0 / .06)` | `0 1px 2px rgb(0 0 0 / .4)` | cartas |
| `--shadow-pop` | `0 12px 32px rgb(0 0 0 / .14)` | `0 12px 32px rgb(0 0 0 / .5)` | menús, toasts |
| `--radius-sm/md/lg` | `6px / 10px / 14px` | | inputs-botones / cartas / auth |
| `--space-1..7` | `4 8 12 16 24 32 48px` | | espaciado |

- Se mantiene `--card` como alias de `--surface` para no romper `toast.js` ni el CSS de recetas.
- El tema oscuro se activa con `@media (prefers-color-scheme: dark)`, como hoy.

### Componentes

- **Tipografía.** `h1.page-title`: Big Shoulders 700, 28px. `h2.section-title`: Karla 700, 18px. Cuerpo: Karla 15px / 1.55. Los datos (cantidades, badges, labels de KPI) van en IBM Plex Mono.
- **Carta (`.card`).** `--surface`, borde `--border`, `--shadow-1`, radio 10px, padding 20px, sin margen propio (el espaciado lo pone el contenedor). La variante `.card-link` es clicable entera, con borde `--amber` en hover/focus. No se usan `scale`, rellenos de color ni hover que mueva el layout.
- **Botones.**
  - `.btn-primary`: ámbar, texto `#1D1D1D`. Uno por vista.
  - `.btn-secondary`: surface, borde y texto fg.
  - `.btn-danger`: rojo.
  - `.btn-ghost`: sin borde, texto muted.
  - Alto 40px (44px con `pointer: coarse`).
  - `.btn-info` se mantiene como alias de `.btn-secondary` para no romper templates que no se migren.
  - `button[type=submit]` deja de ser ámbar por defecto; cada formulario elige su clase.
- **Badges.** Pill, Plex Mono 12px, un punto de 6px del color semántico + texto `--*-ink` sobre fondo del color al 14%. Las etiquetas son "Planificación", "En construcción", "Completado" y "Cancelado"; un valor desconocido muestra el valor crudo con estilo neutro. Las variantes `.badge-pendiente`, `.badge-ok` y `.badge-horno` siguen con los tokens nuevos.
- **Tablas.** Van dentro de `.table-wrap` (overflow-x en móvil). `th` usa Plex Mono 12px uppercase muted sobre `--surface-2`. Las filas tienen hover `--surface-2` y las columnas numéricas llevan `.num` (alineado a la derecha, mono).
- **Formularios.** `.toolbar` es una fila flex de filtros (wrap, gap 12px, el input de búsqueda crece). `.form-grid` reparte 2 columnas desde 640px. Cada `.field` tiene label arriba, `--muted` 13px 700. Los inputs, selects y textareas miden 40px (16px de fuente) y usan foco ámbar.
- **Utilidades.** `.stack` (gap vertical 16px), `.row` (flex, gap 12px, centro), `.muted`, `.empty-state` (centrado, ícono + texto + acción) y `.actions` (fila de botones alineada a la derecha). No se crean más utilidades.
- **Íconos.** `fragments/icons.html :: icon(name)` es un SVG outline 20×20 con `stroke-width:1.5` y `currentColor`, siempre `aria-hidden="true"`. Nombres: `gem, home, pickaxe, box, calculator, history, cube, hammer, globe, file, user, shield, users, chevron-down, menu, close, plus, search`.

### Fuera de alcance

- Lógica de negocio, rutas existentes, controladores API, `modelo3d.js` y el editor de recetas (solo heredan los tokens nuevos).
- Cambiar el contenido funcional de cada página (los campos y acciones existentes se mantienen).
- Tema claro/oscuro manual (toggle).
