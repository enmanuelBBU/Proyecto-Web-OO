# Diseño: 4 funcionalidades "encima" (Faltantes + Horno, Comentarios, Nether, Estadísticas)

Fecha: 2026-11-25 · Rama: `feat/visual-nuevas-funcionalidades`

Construye sobre `2026-11-23-funcionalidades-base-design.md` (Mundo, Horno, Miembros,
Historial, Auditoría) y respeta `2026-11-25-dashboard-refactor-design.md` +
`DESIGN-SYSTEM.md` (fragments `head/nav/badge/icons/toast`, tokens, sin `style=""`
estático, sin emojis, texto de usuario solo con `th:text`).

Orden: **A → B → C → D**. Cada bloque termina con `gradlew.bat test` verde.
La previsualización en Steve (punto 5) queda fuera.

## Reglas globales

- Spring Boot + Lombok, Firestore, sesión HTTP (`uid`, `nombre`, `rol`), chequeo de
  sesión manual por método, toasts por flash attributes, lógica pura `static`,
  `@WebMvcTest` + `@MockitoBean`. TDD estricto. Java 17, sin dependencias nuevas.
- Campos nuevos en Firestore toleran `null`.
- Permisos de proyecto: `ProyectoService.canView/canEdit/canManage`. No visible ⇒ web
  redirige a `/proyectos` con toast de error; API ⇒ 404.
- APIs nuevas bajo `/api/**` (ya cubierto por `ApiAuthInterceptor` en `CorsConfig`).

---

## A) Faltantes de proyecto + horno en el desglose

### Motor (`CraftingCalculatorService`, static, puro)

```java
public static ResultadoFaltantes calcular(List<Demanda> demandas, RecetaFuente fuente, Map<String, Long> stock)
static ResultadoFaltantes calcularCrafteo(String targetId, long cantidad, RecetaFuente fuente, Map<String, Long> stock)
```

- `stock` es una **copia mutable** compartida por todo el cálculo: lo que usa una rama
  ya no está disponible para la siguiente ni para el siguiente ítem del proyecto.
- Por demanda (DFS, orden de `itemsRequeridos` y luego orden de ingredientes):
  `usar = min(stock, necesario)`; se resta; `resto = necesario − usar`.
  Materia prima (`isRaw`) ⇒ `faltante = resto`. Si no ⇒ `aFabricar = resto` y se
  expande `ingredientesParaCalculo` (CRAFTEO u HORNO) por `resto × cantidadIngrediente`.
- `calcularCrafteo` no descuenta el stock del propio ítem objetivo (en `/calculadora`
  quieres fabricar N más, no gastar los que ya tienes).
- Límite: 1000 expansiones (`InventoryException`). Ítem inexistente ⇒
  `InventoryException("El ítem 'x' no existe en el catálogo.")`.
- 1 receta = 1 unidad (sin rendimiento por receta).

### Resultado

```java
NodoCrafteo { itemId, nombre, requerido, usadoDeInventario, aFabricar, faltante,
              tipo: MATERIA_PRIMA | CRAFTEO | HORNO, List<NodoCrafteo> hijos }
ResultadoFaltantes { List<NodoCrafteo> arbol; List<MaterialCantidad> consumidosDeInventario;
                     List<MaterialCantidad> faltantes; boolean usaIntermedios }
MaterialCantidad { itemId, nombre, cantidad }
```

`usaIntermedios` = algún nodo no-materia-prima con `usadoDeInventario > 0`.

### Calculadora y crafteo

- `CraftingPlan` gana `arbol`, `consumidos`, `faltantes`, `usaIntermedios`.
  `materiales` (compatibilidad API) se deriva: por ítem en consumidos ∪ faltantes,
  `requerido = usado + faltante`, `disponible = stock original`, `faltante`.
  `craftable = faltantes.isEmpty()`.
- `craft()`: dentro de la transacción, revalida y descuenta **exactamente**
  `consumidos` (incluye intermedios) con `aplicarConsumo` (static), suma el objetivo
  y crea el registro de historial con esos mismos `consumidos`.

### Proyecto

- `CraftingCalculatorService.faltantesProyecto(Proyecto, uid)`: catálogo cargado una
  vez, stock = inventario de quien mira.
- `proyecto-detalle.html`: sección "Faltantes" con banner "Se descuenta primero lo que ya
  tienes en tu inventario, incluidos ítems intermedios", árbol `details/summary`
  (fragment recursivo `fragments/arbol :: nodo`), tabla plana de faltantes, estado
  vacío sin ítems, "Tienes todo" si no falta nada. Un error del motor muestra mensaje
  en la sección sin romper la página.
- API: `GET /api/proyectos/{id}/faltantes` (canView, si no 404).

---

## B) Comentarios en proyectos

- **Subcolección `proyectos/{id}/comentarios`.** Razón: el comentario no existe fuera de
  su proyecto; listar por proyecto no requiere índice ni filtro; el borrado del
  proyecto los elimina en el mismo `WriteBatch` (atómico); y no hay consultas
  transversales previstas.
- `Comentario { id, proyectoId, autorUid, autorNombre, texto, fechaCreacion, fechaEdicion }`.
- Validación: `trim`, 1..1000 caracteres.
- Permisos (static en `ComentarioService`): crear = `canView`; editar = autor;
  borrar = autor ∨ `canManage` (creador o admin). Borrar uno ajeno ⇒
  `AuditService.registrar(ELIMINAR, COMENTARIO, …)` (nunca lanza).
- Web: listado ascendente por `fechaCreacion` en el detalle, formulario al pie;
  `POST /proyectos/{id}/comentarios`, `…/{cid}/update`, `…/{cid}/delete`.
  Botones solo si se puede; marca "(editado)".
- API: `GET/POST /api/proyectos/{id}/comentarios`, `PUT/DELETE …/{cid}`
  (no visible ⇒ 404; comentario inexistente ⇒ 404; sin permiso ⇒ 403; validación ⇒ 400).

---

## C) Calculadora Nether ↔ Overworld + portales

- `Dimension { OVERWORLD, NETHER; fromString }`.
- `NetherCalculator.convertir(dim, x, y, z)`: OW→N `floorDiv(x,8)`, `floorDiv(z,8)`;
  N→OW `x*8`, `z*8`; Y igual. Rangos de entrada: OW y∈[-64,320], x,z∈±30.000.000;
  Nether y∈[0,255], x,z∈±3.750.000.
- `Portal { id (UUID), nombre, dimension, x, y, z, enlazadoId }`; `Mundo.portales`
  (`List<Portal>`, null ⇒ vacía). `MundoService.actualizar` preserva los portales.
- Reglas (`PortalService`, static): gestiona solo dueño o admin; enlace solo entre
  dimensiones distintas del mismo mundo, bidireccional (re-enlazar suelta los pares
  anteriores); borrar un portal limpia el enlace del otro; "pareado" crea el par con
  coordenadas convertidas (Y acotada al rango de la dimensión destino) y los enlaza.
- Web: en `mundo-detalle.html` calculadora (GET con parámetros), lista de portales con su
  par, formulario de alta (botones "Guardar portal" y "Guardar y crear pareado"),
  edición con selector de enlace. `POST /mundos/{id}/portales`,
  `…/{pid}/update`, `…/{pid}/delete`. Página `/calculadora-nether` sin guardar. Nav.
- Detalle de proyecto con X y Z: equivalente en el Nether y el portal **Overworld**
  más cercano del mundo (distancia horizontal euclídea, redondeada).
- API: `GET/POST /api/mundos/{id}/portales`, `PUT/DELETE …/{pid}`,
  `GET /api/nether/convertir?dimension=&x=&y=&z=`.

---

## D) Dashboard de estadísticas

- `/estadisticas` (nav), Chart.js `4.4.1` desde cdnjs; JS propio `static/estadisticas.js`.
- `EstadisticasService` (static puro):
  1. `porEstado(proyectos)`: los 4 estados en orden del enum (null ⇒ PLANIFICACION).
  2. `itemsMasUsados(catalogo, 10)`: recetas distintas que usan el ítem; desempate por
     cantidad total y luego nombre.
  3. `materialesEscasos(proyectos, catalogo, stock, 10)`: proyectos activos
     (PLANIFICACION, EN_CONSTRUCCION), motor A con **un único stock compartido**;
     un proyecto que falla se omite sin consumir stock.
- Datos al template con `th:inline="javascript"` como mapas/listas simples. Colores
  leídos de los tokens CSS (`--blue`, `--amber`, `--green`, `--red`, `--fg`, `--border`).
  Cada gráfico: título, estado vacío, tabla equivalente en `<details>`. Fuente caída ⇒
  "—" en ese gráfico, página 200.

## Fuera de alcance

Rendimiento por receta, combustible del horno, notificaciones de comentarios,
paginación, previsualización en Steve.
