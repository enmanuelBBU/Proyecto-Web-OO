# Esquema de Recetas de Items + Íconos

## Introducción

Este documento resume la implementación de la característica **Esquema de Recetas de Items + Íconos** — el tercer sub-proyecto entregado del backend de Proyecto Minecraft, construido directamente sobre las dos entregas anteriores (Autenticación + Perfil + Items CRUD + Búsqueda; CRUD de Usuarios (Admin)).

Tres de las diez características restantes del roadmap — Gestión de Materiales, Validación y almacenamiento de recetas, y Calculadora de crafteo — necesitan las mismas dos cosas que el catálogo de `Item` todavía no tenía: una receta verificable por máquina (qué 9 casillas de la grilla producen este item, y cuánto de cada materia prima implica eso) y una forma de mostrarle al jugador un ícono real de Minecraft en lugar de un nombre desnudo. Construir cualquiera de esas tres características primero requiere que exista esta base compartida.

El prototipo Node de referencia del equipo (`proyecto-node`) ya había resuelto ambos problemas: `full_id` (p. ej. `minecraft:red_bed`) sirve además como llave para una API pública de íconos (`https://blocksitems.com/api/v1/items/{full_id}/icon`), y las recetas se guardan como una matriz de 9 casillas (`receta_matriz`) más una lista derivada de ingredientes (`ingredientes_para_calculo`), referenciando a otros items por un id tipo slug legible (p. ej. `lana`, `tablones_de_roble`) en lugar de un id opaco de base de datos. Esta entrega adapta ese mismo diseño al backend de Java/Firestore.

## Qué Cambió

### Modelo de Datos

**Campos nuevos en `Item`** (`src/main/java/cl/grupo5/proyectominecraft/items/Item.java`):

| Campo | Tipo | Notas |
|---|---|---|
| `fullId` | `String` | opcional; id namespaced de Minecraft (p. ej. `minecraft:red_bed`), usado como llave para la API de íconos externa |
| `recetaMatriz` | `List<String>` | opcional; cuando está presente, exactamente 9 entradas, cada una es el id de otro item o `null` (casilla vacía). Vacía/ausente cuando `esMateriaPrima = true` |
| `ingredientesParaCalculo` | `List<Ingrediente>` | calculado por `ItemService`, nunca confiado desde el cliente — ver más abajo |

Los campos existentes (`id`, `nombre` con `@NotBlank`, `categoria`, `esMateriaPrima`) no cambiaron.

**Nuevo tipo `Ingrediente`** (`src/main/java/cl/grupo5/proyectominecraft/items/Ingrediente.java`): clase de datos simple Lombok `@Data` con `itemId` (String) y `cantidad` (int) — mismo estilo que `Item`.

**Ubicación del código**: `Item.java` (modificado), `Ingrediente.java` (nuevo).

### Ids de Item basados en Slug

**Comportamiento anterior**: `ItemService.create` dejaba que Firestore autogenerara el id del documento (una cadena opaca).

**Comportamiento nuevo**: `create` deriva el id de `nombre` vía `Slugs.slugify(item.getNombre())` — minúsculas, tildes/diacríticos eliminados (normalización Unicode NFD + eliminación de marcas combinantes), espacios (incluyendo espacios múltiples y los de los extremos) reemplazados por `_`. Por ejemplo, `"Tablones de Roble"` → `"tablones_de_roble"`, `"Peña"` → `"pena"`, `"  Lingote   de   Hierro  "` → `"lingote_de_hierro"`.

**Nuevas utilidades**:
- `Accents` (`src/main/java/cl/grupo5/proyectominecraft/items/Accents.java`) — package-private, `strip(String)` elimina marcas diacríticas vía `Normalizer.normalize(s, Form.NFD)` + una expresión regular `\p{M}`.
- `Slugs` (`src/main/java/cl/grupo5/proyectominecraft/items/Slugs.java`) — pública, `slugify(String nombre)` compone `Accents.strip`, minúsculas, `trim`, y colapso de espacios a `_`.

**Rationale**: esto empareja el algoritmo `scripts/seed-items.js` de `proyecto-node`, de modo que los ids sean legibles por humanos y suficientemente estables para que las `recetaMatriz`/`ingredientesParaCalculo` de otros items los referencien por id. El id queda fijo al momento de la creación; renombrar un item después (`update`) no lo vuelve a "sluggificar" ni mueve el documento, ya que otros items pueden estar referenciando el id original.

**Rechazo de slugs duplicados**: crear un item cuyo id derivado ya existe se rechaza en lugar de sobrescribir el documento existente en silencio — consistente con el tema ya establecido en este código de "nada silencioso" (los fixes de 404 en `update`/`delete` de la entrega anterior). Nueva excepción `ItemAlreadyExistsException` (`src/main/java/cl/grupo5/proyectominecraft/items/ItemAlreadyExistsException.java`, mensaje fijo: "Ya existe un item con ese nombre."), lanzada por `ItemService.create` cuando `db.collection("items").document(id).get()` ya existe, **antes** de cualquier escritura.

**Ubicación del código**: `Accents.java` (nuevo), `Slugs.java` (nuevo), `ItemAlreadyExistsException.java` (nuevo), `ItemService.java` (modificado — método `create`).

### Validación de Forma de la Receta

Nueva excepción `RecipeValidationException` (`src/main/java/cl/grupo5/proyectominecraft/items/RecipeValidationException.java`), lanzada por el nuevo método estático package-private `ItemService.validateRecetaMatriz(Item)`:

- Si `recetaMatriz` es `null` o vacía, no hay validación que hacer (una materia prima o un item sin receta es válido).
- Si `recetaMatriz` está presente pero **no tiene exactamente 9 entradas**: `RecipeValidationException("La receta debe tener exactamente 9 casillas.")`.
- Si `esMateriaPrima = true` **y** `recetaMatriz` es no vacía: `RecipeValidationException("Una materia prima no puede tener receta.")`.

Esta validación se ejecuta tanto en `create` como en `update`, antes de calcular `ingredientesParaCalculo` y antes de cualquier escritura a Firestore.

**Cómputo de `ingredientesParaCalculo`**: nuevo método estático package-private `ItemService.computeIngredientes(List<String> recetaMatriz)` — cuenta las ocurrencias no-nulas/no-en-blanco de cada id en las 9 casillas, agrupadas por id, preservando el orden de primera aparición (`LinkedHashMap`). Por ejemplo, una `recetaMatriz` con `"lana"` tres veces y `"tablones_de_roble"` tres veces (el resto `null`) produce `[{itemId: "lana", cantidad: 3}, {itemId: "tablones_de_roble", cantidad: 3}]`. Este valor **siempre** se recalcula en el servidor a partir de `recetaMatriz` en `create` y `update` — cualquier `ingredientesParaCalculo` enviado por el cliente se descarta, de modo que las dos listas nunca se desincronizan.

**Ubicación del código**: `RecipeValidationException.java` (nuevo), `ItemService.java` (modificado — `validateRecetaMatriz`, `computeIngredientes`, y su uso en `create`/`update`).

### API REST de Items

**`POST /api/items`** y **`PUT /api/items/{id}`** ahora aceptan los tres campos nuevos (`fullId`, `recetaMatriz`, `ingredientesParaCalculo` — este último ignorado si se envía). `ItemController` captura las nuevas excepciones:

- `ItemAlreadyExistsException` → `409 Conflict` (solo en `create`; `update` apunta a un id existente y nunca vuelve a "sluggificar", por lo que no puede colisionar).
- `RecipeValidationException` → `400 Bad Request` (en `create` y `update`).

**Nuevo endpoint — `GET /api/items/sugerencias-icono?nombre=...`**: ver la sección de Sugerencia de Íconos más abajo. Protegido por el mismo `ApiAuthInterceptor` que el resto de `/api/items` (registrado sobre `/api/**` en `CorsConfig`, con exclusión solo de `/api/auth/**`) — 401 sin sesión.

**Ubicación del código**: `ItemController.java` (modificado).

### Formularios Web (`items.html`, `item-edit.html`)

- Ambos formularios (crear y editar) ganan un campo de texto `fullId` (placeholder `minecraft:clay_ball`).
- Ambos ganan una grilla 3x3 de 9 `<input name="slot">` de texto plano — cada uno referencia el id de otro item, o queda vacío. Sin drag-and-drop; la grilla interactiva es una especificación futura separada (Grilla 3x3). En el formulario de edición, cada input se prellena con `recetaMatriz[i]` vía el atributo de modelo `recetaMatriz` (una lista de exactamente 9 elementos, rellenada con `null` por `WebItemController.nineSlots(...)` si la receta almacenada tiene menos de 9 elementos o está ausente).
- `WebItemController.create()`/`update()` recogen los 9 parámetros `slot` en un `List<String>` (mediante `normalizeSlots`, que convierte valores en blanco a `null` y recorta espacios) antes de pasarlo a `ItemService`.
- Ambos formularios capturan `ItemAlreadyExistsException`/`RecipeValidationException` (`create` captura ambas; `update` solo `RecipeValidationException`, ya que no puede colisionar de slug) y re-renderizan con un `error` en español en lugar de la redirección normal, preservando el patrón ya establecido para `nombre` en blanco.
- El listado de items (`items.html`) renderiza `<img th:if="${i.fullId}" th:src="'https://blocksitems.com/api/v1/items/' + i.fullId + '/icon?size=32'" ... onerror="this.style.display='none'">` en una nueva columna "Ícono", cuando `fullId` está presente; si la carga falla, la imagen simplemente se oculta (no hay una imagen de repuesto estática — una desviación menor respecto al `onerror` de repuesto planteado en el diseño original, mismo efecto práctico de no dejar un ícono roto visible).

**Ubicación del código**: `items.html` (modificado), `item-edit.html` (modificado), `WebItemController.java` (modificado).

### Sugerencia de Íconos

El flujo completo es: **diccionario → búsqueda → ranking**.

1. **Diccionario (`MinecraftEsEn`, nuevo, `src/main/java/cl/grupo5/proyectominecraft/items/MinecraftEsEn.java`)**: un `Map<String,String>` curado con ~46 raíces español→inglés que cubren el vocabulario de items conocido de este proyecto (p. ej. `roble`→`oak`, `lana`→`wool`, `hierro`→`iron`, `tablones`→`planks`, `pico`→`pickaxe`, `piedra`→`stone`). El método `translate(String nombre)`: retorna `""` si `nombre` es `null`/en blanco; si no, quita tildes (mismo enfoque NFD que `Slugs`) y pasa a minúsculas, separa por tokens (`[^a-z0-9]+`), descarta una lista corta de stopwords en español (`de`, `la`, `el`, `los`, `las`), traduce cada token restante vía el diccionario (un token sin entrada pasa sin cambios — cubre palabras ya en inglés o de marca como `TNT`), y une el resultado en una frase de búsqueda separada por espacios.
2. **Búsqueda (`IconSuggestionService.search`, privado)**: llama `GET https://blocksitems.com/api/v1/items?search={query}` vía `RestTemplate` (el mismo cliente HTTP ya usado por `FirebaseIdentityService`, sin dependencia nueva). `IconSuggestionService.suggest(String nombre)` primero busca con la frase traducida; si esa búsqueda no retorna resultados, reintenta una vez con el `nombre` original sin traducir antes de rendirse.
3. **Ranking (`IconSuggestionService.rank`, estático package-private, mismo estilo testeable que `ItemService.filter`)**: ordena los candidatos con `namespace == "minecraft"` primero, luego por solapamiento de tokens (intersección de conjuntos, sin librería de similitud externa) entre la frase de consulta y el `display_name` de cada candidato, y trunca a los primeros 5. Cada candidato se mapea a un `IconCandidate{fullId, displayName, iconUrl}`, donde `iconUrl` sigue el mismo patrón `https://blocksitems.com/api/v1/items/{fullId}/icon?size=64` usado en el listado de items.

**Nuevo tipo `IconCandidate`** (`src/main/java/cl/grupo5/proyectominecraft/items/IconCandidate.java`): clase de datos Lombok `@Data` con `fullId`, `displayName`, `iconUrl`.

**Endpoint**: `GET /api/items/sugerencias-icono?nombre=...` en `ItemController`, retorna la lista de `IconCandidate` como JSON (una lista vacía, nunca un error, si no hay coincidencias).

**Widget JS (`src/main/resources/static/item-icon-suggest.js`)** — la primera línea de JavaScript de este proyecto, aceptada porque las futuras Grilla 3x3 y Modelo 3D la necesitarán de todas formas; se mantiene como un archivo vanilla-JS pequeño, sin framework ni paso de build. Un botón "Sugerir ícono" (delegación de eventos por clase `.sugerir-icono-btn`, con atributos `data-nombre`/`data-fullid`/`data-target` que apuntan a los ids de los inputs relevantes) dispara `fetch('/api/items/sugerencias-icono?nombre=' + encodeURIComponent(nombre))`, renderiza hasta 5 tarjetas de candidato clicables (ícono `<img>` + `displayName`) en un contenedor debajo del botón, y un click en una tarjeta llena el input de texto `fullId`. Casos manejados explícitamente: nombre vacío ("Escribe un nombre primero."), sin candidatos ("No se encontraron sugerencias, ingresa el id manualmente."), y fallo de red ("No se pudo buscar sugerencias."). Se incluye (`<script src="/item-icon-suggest.js">`) en ambos, `items.html` y `item-edit.html`.

**Ubicación del código**: `MinecraftEsEn.java` (nuevo), `IconCandidate.java` (nuevo), `IconSuggestionService.java` (nuevo), `ItemController.java` (modificado — endpoint `sugerenciasIcono`), `item-icon-suggest.js` (nuevo).

## Mapeo de Rutas

### API REST de Items

| Método | Ruta | Cambio |
|---|---|---|
| GET | `/api/items?q=&esMateriaPrima=` | sin cambios |
| POST | `/api/items` | acepta `fullId`, `recetaMatriz`; id de documento ahora derivado por slug de `nombre`; **409** si el slug ya existe; **400** si la forma de `recetaMatriz` es inválida |
| PUT | `/api/items/{id}` | acepta `fullId`, `recetaMatriz`; **400** si la forma de `recetaMatriz` es inválida; sigue retornando 404 en id desconocido (sin cambio de la entrega anterior) |
| DELETE | `/api/items/{id}` | sin cambios |
| GET | `/api/items/sugerencias-icono?nombre=...` | **nuevo** — retorna hasta 5 `IconCandidate`; requiere sesión (401 sin ella) |

### Web de Items

| Método | Ruta | Cambio |
|---|---|---|
| GET | `/items?q=&esMateriaPrima=` | sin cambios en la ruta; el listado ahora muestra una columna de ícono |
| POST | `/items` | formulario gana campos `fullId` + 9 `slot`; re-renderiza con error en español si el slug ya existe o la receta es inválida (además del error existente de `nombre` en blanco) |
| GET | `/items/{id}/edit` | expone el nuevo atributo de modelo `recetaMatriz` (lista de 9, rellenada con `null`) |
| POST | `/items/{id}/update` | formulario gana campos `fullId` + 9 `slot`; re-renderiza con error en español si la receta es inválida |
| POST | `/items/{id}/delete` | sin cambios |

## Pruebas

**Cobertura de pruebas del proyecto completo**: **95 pruebas** en total a través de 16 clases de prueba, todas pasando (`gradlew.bat test`, corrida completa con `--rerun-tasks` para verificar, no desde caché).

### Clases de Prueba Nuevas o Modificadas para esta Entrega

| Clase de Prueba | Pruebas | Cobertura |
|---|---|---|
| `SlugsTest` | 4 | minúsculas + espacio a `_`; eliminación de tildes; palabra única; colapso de espacios múltiples/extremos |
| `ItemServiceTest` | 10 (6 nuevas sobre la base de 4 existentes) | filtro puro (sin cambio); `validateRecetaMatriz` acepta receta `null`/vacía, rechaza tamaño incorrecto, rechaza receta en materia prima; `computeIngredientes` cuenta ids repetidos en orden, retorna lista vacía en receta `null`/vacía |
| `MinecraftEsEnTest` | 4 | traduce nombre multi-palabra descartando stopwords; traduce palabra única conocida; token desconocido pasa sin cambios; `nombre` vacío/`null` traduce a cadena vacía |
| `IconSuggestionServiceTest` | 3 | namespace `minecraft` rankea sobre otros namespaces para la misma consulta; resultados limitados a 5; `iconUrl` se construye correctamente a partir de `fullId` |
| `ItemControllerTest` | 7 (2 nuevas sobre la base de 3 existentes, más 2 del endpoint de sugerencias) | 400 en nombre en blanco; 404 en PUT/DELETE de id desconocido; 409 en slug duplicado al crear; 400 en receta inválida al crear; 401 sin sesión en sugerencias de ícono; 200 con candidatos del servicio mockeado |
| `WebItemControllerTest` | 10 (3 nuevas sobre la base de 7 existentes) | validación de nombre en blanco en crear/actualizar (sin cambio); redirect en id inexistente (sin cambio); nav `isAdmin` (sin cambio); re-render con error en slug duplicado; re-render con error en receta inválida; `recetaMatriz` expuesta como 9 elementos rellenados con `null` en la vista de edición |
| `ItemApiAuthTest` | 5 | sin cambios de esta entrega — 401 sin sesión en GET/POST/PUT/DELETE; 200 con sesión en GET (`@Import` ahora incluye `AdminAuthInterceptor`, arrastrado desde la entrega de admin) |

Siguiendo la práctica ya establecida en este código (ver `ItemServiceTest`, que solo cubre el método estático `filter`, nunca `create`/`update`/`delete` contra un Firestore real), la validación de forma (`validateRecetaMatriz`), el cómputo de ingredientes (`computeIngredientes`), la traducción (`MinecraftEsEn.translate`) y el ranking (`IconSuggestionService.rank`) se prueban como métodos estáticos puros con JUnit 5 + AssertJ, sin contexto de Spring. La orquestación de Firestore dentro de `create`/`update` (generación del id slug, lectura de verificación de duplicado, la escritura misma) y la llamada HTTP real a `blocksitems.com` no están cubiertas por pruebas automatizadas — consistente con el resto de esta clase y con `FirebaseIdentityService`, cuya llamada HTTP tampoco está testeada.

### Ejecutar Pruebas

```bash
gradlew.bat test        # Ejecutar todas las pruebas unitarias/integración
gradlew.bat build       # Build completo (pruebas + asamblea)
```

Ambos comandos se completan exitosamente con las 95 pruebas pasando.

## Brecha Conocida de Verificación

**Importante**: el checklist completo de prueba manual interactiva del navegador/Docker (Tarea 11 del plan de implementación) **no** fue realizado como parte de esta implementación. Específicamente, lo siguiente **no** fue ejercitado a mano:

- Levantar el contenedor (`docker compose up -d --build`) y abrir `/items` en un navegador real.
- Escribir un nombre en español (p. ej. "Tablones de Roble") en el formulario de creación, hacer click en "Sugerir ícono", y confirmar que aparecen tarjetas de candidatos reales con íconos cargando desde `blocksitems.com`.
- Hacer click en una tarjeta de candidato y confirmar que el campo Full ID se llena correctamente.
- Escribir un nombre sin coincidencias (p. ej. "zzz123") y confirmar que aparece el mensaje "No se encontraron sugerencias..." en lugar de un error.
- Crear un item con una receta completa de 9 casillas a través del formulario real y confirmar que se guarda, aparece en el listado, y que `ingredientesParaCalculo` quedó correctamente calculado en Firestore.
- Intentar crear dos items con el mismo nombre (mismo slug) a través del navegador y confirmar el mensaje "Ya existe un item con ese nombre." en la UI real.
- Confirmar visualmente que un item con `fullId` muestra su ícono de Minecraft en el listado, y que uno sin `fullId` o con un `fullId` inválido no deja un ícono roto visible.

Lo que **sí** se verificó:
- La suite automatizada completa (`gradlew.bat test`, corrida limpia con `--rerun-tasks`): 95 pruebas, todas pasando.
- Chequeos de humo básicos: el contenedor/aplicación arranca (contexto de Spring carga, cubierto por `ProyectominecraftApplicationTests`), el nuevo archivo estático `item-icon-suggest.js` está servido en `/item-icon-suggest.js` (rutas estáticas de Spring Boot, sin configuración adicional necesaria), y el nuevo endpoint `GET /api/items/sugerencias-icono` está correctamente protegido por sesión (confirmado por `ItemControllerTest.sugerenciasIconoWithoutSessionIsUnauthorized`, que retorna 401 vía el `ApiAuthInterceptor` real, no mockeado, importado en la clase de prueba).
- La llamada real a `blocksitems.com` fue verificada manualmente **durante la fase de diseño** (antes de la implementación, según `design.md`): `search=clay`, `search=oak planks` confirmados funcionando. Esto no es lo mismo que verificar el flujo end-to-end completo (dictionary → controller → JS → UI) después de la implementación.

**Recomendación**: antes de considerar esta característica completamente verificada end-to-end, el propietario del proyecto debe caminar a través del checklist de prueba manual en `openspec/changes/item-recipe-schema/plan.md`, Tarea 10 Paso 2 y Tarea 11 Paso 3. Esto es particularmente importante aquí porque el widget de sugerencia de íconos depende de una API externa de terceros (`blocksitems.com`) cuyo comportamiento en vivo (formato de respuesta, disponibilidad, límites de tasa) no está bajo el control de este proyecto y no puede confirmarse completamente solo con pruebas unitarias sobre datos de candidatos armados a mano.

## Explícitamente Fuera de Alcance

Estos elementos fueron explícitamente excluidos por la propuesta original, no son brechas:

- **Grilla 3x3 interactiva** (drag-and-drop) — esta entrega solo agrega 9 inputs de texto plano; la grilla interactiva es su propia especificación futura.
- **Validación profunda de recetas** (¿existen realmente los ids de ingredientes referenciados en el catálogo? ¿hay dependencias circulares?) — esta entrega solo valida la *forma* de `recetaMatriz` (tamaño, compuerta de materia prima). La validación referencial es responsabilidad explícita de la futura característica "Validación y almacenamiento de recetas".
- **Calculadora de crafteo** (agregación de materias primas necesarias para una construcción) — característica futura que consumirá `ingredientesParaCalculo`, no parte de este cambio.
- **Migración de documentos de Firestore existentes** a ids tipo slug — solo los items creados nuevos obtienen un id slug; los documentos creados antes de este cambio conservan su id autogenerado existente y simplemente no tienen valor para los tres campos nuevos.
- **Un framework de JavaScript de propósito general** — el widget de sugerencia de íconos es un `fetch` + manipulación de DOM en vanilla-JS pequeño, no un framework.
- **Una capacidad de traducción español↔inglés de propósito general** — el diccionario solo cubre el vocabulario de items conocido de este proyecto (~46 entradas), no texto arbitrario.

## Qué Sigue

Los siguientes sub-proyectos están diferidos y construirán sobre esta base. Esta entrega es el cimiento compartido que las primeras cuatro necesitan para poder empezar.

### Completado
- **Auth + Perfil + Items CRUD + Búsqueda** (primera entrega)
- **CRUD de Usuarios (Admin)** (segunda entrega)
- **Esquema de Recetas de Items + Íconos** (esta entrega)

### Planeado (en orden de ejecución)

1. **Gestión de Materiales** — UI dedicada para la vista filtrada `esMateriaPrima` y operaciones en lote; actualmente solo disponible como un filtro en el catálogo de items. Ahora puede apoyarse en `fullId` para mostrar íconos en esa vista dedicada.
2. **Gestión de Inventario** — inventario por usuario con cantidades; cálculos de "crafteable desde inventario". Consumirá `ingredientesParaCalculo` para saber cuánto de cada materia prima requiere un item.
3. **Calculadora de crafteo** — UI interactiva para planificar cadenas de crafteo; mostrar cantidades de salida. Consume directamente `recetaMatriz`/`ingredientesParaCalculo` de esta entrega.
4. **Grilla 3x3** — UI de grilla de crafteo estilo Minecraft con drag-and-drop, reemplazando los 9 inputs de texto plano agregados en esta entrega. Reutilizará el patrón de JS vanilla establecido aquí (`item-icon-suggest.js` es el primer precedente de JS en el proyecto).
5. **CRUD de Proyectos de construcción** — crear, leer, actualizar, eliminar proyectos de construcción; asignar items y usuarios.
6. **Estados de Proyecto** — estados de flujo de trabajo (draft, active, completed, archived); transiciones de máquina de estados.
7. **Generar Documento (PDF)** — exportar proyecto/inventario/recetas a PDF.
8. **Modelo 3D** — visor 3D para items/proyectos (modelos Minecraft); integración opcional con un motor 3D. Reutilizará también el patrón de JS establecido en esta entrega.
9. **Notificación de Toast** — notificaciones toast/snackbar ligeras para mensajes success/error/info (actualmente usando redirecciones de Spring y atributos de error de plantilla, incluyendo los nuevos errores de slug duplicado/receta inválida agregados aquí).

Cada sub-proyecto seguirá el mismo proceso de implementación y revisión, con docs de diseño y cobertura de pruebas.

## Notas de Implementación

- **Sin cambios disruptivos al contrato de API REST**: todos los campos nuevos son opcionales y aditivos; ninguna ruta existente cambió de forma, salvo los nuevos códigos de estado (`409` en creación duplicada, `400` en receta inválida) que antes no podían ocurrir porque los campos que los disparan no existían.
- **Documentos de Firestore sin los nuevos campos**: los documentos creados antes de este cambio no tienen `fullId`, `recetaMatriz` ni `ingredientesParaCalculo`, y conservan su id autogenerado por Firestore (no un slug). Se manejan correctamente sin migración: al leerse, los campos ausentes simplemente deserializan a `null`/vacío, y el listado/plantillas ya comprueban `th:if="${i.fullId}"` antes de intentar renderizar un ícono.
- **Dependencia del ranking de íconos de una API de terceros**: `IconSuggestionService` depende completamente de la disponibilidad y el formato de respuesta de `blocksitems.com`, un servicio externo no operado por este proyecto. Si esa API cambia su esquema de respuesta o deja de estar disponible, el endpoint de sugerencias fallará silenciosamente a una lista vacía (dado que `search()` retorna `List.of()` si la respuesta no tiene la forma esperada) en lugar de lanzar un error — esto es intencional (ver el escenario "No matches found" del spec) pero significa que una falla real de la API externa es indistinguible, desde la UI, de simplemente no tener coincidencias.
- **`ItemAlreadyExistsException`/`RecipeValidationException` son `RuntimeException`s sin jerarquía compartida**: cada una se captura por separado en los controladores (`ItemController`/`WebItemController`). No hay un manejador global de excepciones (`@ExceptionHandler`/`@ControllerAdvice`) en este proyecto todavía — cada excepción de dominio nueva requiere un bloque `catch` explícito en cada controlador que la pueda lanzar, siguiendo el patrón ya usado para las excepciones de la entrega anterior.
- **Historial de Git**: todos los cambios de las Tareas 1-10 están commiteados individualmente en la rama `entrega_15_funcionalidades` (de `a85bee0` "feat: add Accents/Slugs utilities for item id generation" a `eb0fe3d` "feat: add icon suggestion widget JS").
