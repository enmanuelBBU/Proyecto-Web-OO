# Design: Item Recipe Schema + Icons

## Architecture

No new architectural pattern: this extends the existing `Item` entity and
`ItemService`. A new stateless `Slugs` utility centralizes id derivation.
Recipe shape validation and the `ingredientesParaCalculo` computation live
in `ItemService` (not duplicated across `ItemController` and
`WebItemController`) — both controllers already delegate persistence to
`ItemService`, and centralizing here avoids repeating the kind of
controller-duplicated invariant that `complete-auth-perfil-items` flagged
as a known, deferred cleanup for the `rol`-preservation logic.

## Data Model

### `Item` (add three fields)

| Field | Type | Notes |
|---|---|---|
| `fullId` | String | optional; Minecraft namespaced id (e.g. `minecraft:red_bed`), used as the key for the external icon API |
| `recetaMatriz` | `List<String>` | optional; when present, exactly 9 entries, each either another item's id or `null` (empty grid slot). Empty/absent when `esMateriaPrima = true`. |
| `ingredientesParaCalculo` | `List<Ingrediente>` | computed by `ItemService`, never trusted from client input — see below |

### `Ingrediente` (new type)

| Field | Type | Notes |
|---|---|---|
| `itemId` | String | id of the referenced item |
| `cantidad` | int | count of that id's occurrences in `recetaMatriz` |

### Item id (changed generation strategy)

Today `ItemService.create` lets Firestore autogenerate the document id.
Going forward, `create` derives it instead:
`Slugs.slugify(item.getNombre())` — lowercase, diacritics stripped
(Unicode NFD + strip combining marks), spaces replaced with `_`. This
matches `proyecto-node`'s `scripts/seed-items.js` algorithm, so ids stay
human-readable and stable enough for other items' `recetaMatriz` /
`ingredientesParaCalculo` to reference by id. The id is fixed at creation
time; renaming an item later (`update`) does not re-slugify or move the
document, since other items may already reference the original id.

## Component Changes

### `items` package

- **New `Slugs`** (stateless utility, `Slugs.slugify(String nombre)`):
  lowercase, NFD-normalize, strip combining marks, replace spaces (and
  runs of whitespace) with `_`. Pure function, no dependencies.
- **New `Ingrediente`**: plain data class (`itemId`, `cantidad`), same
  style as `Item` (Lombok `@Data`).
- **`Item`**: add `fullId`, `recetaMatriz`, `ingredientesParaCalculo`
  fields (Lombok `@Data`, all optional — no `@NotBlank`/`@NotNull`).
- **`ItemService`**:
  - `create(Item item)`: compute `id = Slugs.slugify(item.getNombre())`.
    Check `db.collection("items").document(id).get()` first — if it
    already exists, throw a dedicated exception (e.g.
    `ItemAlreadyExistsException`) that the controllers translate to
    409 (REST) / a re-rendered form with a Spanish error (web), instead
    of silently overwriting the existing document (consistent with this
    codebase's established "no silent anything" theme from the prior
    `update`/`delete` 404 fixes).
    Validate `recetaMatriz` shape (see Error Handling); if valid and
    non-empty, compute `ingredientesParaCalculo` by counting non-null
    occurrences in `recetaMatriz`, grouped by id. Persist.
  - `update(String id, Item item)`: same shape validation and
    `ingredientesParaCalculo` computation as `create`, but keeps the
    existing document id — never re-slugifies from a possibly-changed
    `nombre`.

### `items` controllers (`ItemController`, `WebItemController`)

- Both accept the three new fields from their respective request shapes
  (JSON body / form params) and pass them through to `ItemService`.
  `recetaMatriz` arrives as 9 individual form parameters on the web side
  (`slot1`..`slot9`, blank meaning `null`) collected into a `List<String>`
  before calling the service; the REST side accepts a JSON array directly.
- Both catch `ItemAlreadyExistsException` and the shape-validation
  exception and respond accordingly (409 / 400 for REST; re-render with a
  Spanish `error` attribute for web — mirroring the existing blank-`nombre`
  pattern).

### Templates (`items.html`, `item-edit.html`)

- Add a `fullId` text input to the create/edit forms.
- Add 9 plain text inputs (one per grid slot) to the create/edit forms,
  each pre-filled from `recetaMatriz` on edit. No drag-and-drop, no JS —
  the interactive grid is a separate future spec.
- In the item listing (`items.html`), render
  `<img th:src="${'https://blocksitems.com/api/v1/items/' + item.fullId + '/icon?size=64'}" onerror="this.src='/img/item-fallback.png'">`
  when `fullId` is present; otherwise show the existing text-only row.

### Icon suggestion (`items` package + a small JS asset)

- **New `MinecraftEsEn`** (stateless utility): a curated `Map<String,String>`
  of Spanish→English root words covering this project's known item
  vocabulary (e.g. `roble`→`oak`, `lana`→`wool`, `hierro`→`iron`,
  `tablones`→`planks`, `pico`→`pickaxe`, `piedra`→`stone` — roughly 30
  entries cover every item in `proyecto-node`'s seed list). `translate(String
  nombre)`: lowercase, strip diacritics (same NFD-normalize-and-strip
  approach as `Slugs.slugify`, applied here without the space-to-`_`
  step), split on whitespace, drop a short Spanish stopword list
  (`de`, `la`, `el`, `los`, `las`), map each remaining token through the
  dictionary (a token with no entry passes through unchanged — covers
  already-English or brand-name words like `TNT`), and join the result
  into a search phrase.
- **New `IconSuggestionService`**: `List<IconCandidate> suggest(String
  nombre)`. Calls
  `GET https://blocksitems.com/api/v1/items?search={translated phrase}`
  via `RestTemplate` (same client already used by
  `FirebaseIdentityService`, no new dependency). Sorts the response's
  `data` array with `namespace == "minecraft"` first, then by a simple
  token-overlap score between the translated phrase and each candidate's
  `display_name`/`path` (no external similarity library — set
  intersection over lowercased tokens is enough for this vocabulary size).
  Returns the top 5 as `IconCandidate{fullId, displayName, iconUrl}`,
  where `iconUrl` follows the same pattern used for item listing icons.
  If the translated phrase returns nothing, retries once with the raw
  (untranslated) `nombre` before giving up and returning an empty list.
- **New endpoint**: `GET /api/items/sugerencias-icono?nombre=...` on
  `ItemController`, gated by the same session check as the rest of
  `/api/items` (existing `ApiAuthInterceptor`). Returns the `IconCandidate`
  list as JSON.
- **New JS asset** (`src/main/resources/static/item-icon-suggest.js`,
  loaded only by `items.html`'s create/edit forms): a "Sugerir ícono"
  button next to the `nombre` field triggers
  `fetch('/api/items/sugerencias-icono?nombre=' + encodeURIComponent(...))`,
  renders up to 5 clickable candidate cards (icon `<img>` + `displayName`)
  in a container below the button, and a click on a card sets the
  `fullId` text input's value. An empty result renders
  `"No se encontraron sugerencias, ingresa el id manualmente."` instead of
  an empty list. This is the first JavaScript in the project — accepted
  because Grilla 3x3 and Modelo 3D will require it regardless; it stays a
  small vanilla-JS file, no framework or build step introduced.

## Sequence: Creating an Elaborated Item

```
Client          WebItemController/ItemController      ItemService              Firestore
  |  POST (nombre="Cama", fullId=..., slots=[...])    |                          |
  |---------------------------------------------------->|                        |
  |                                                    | id = slugify(nombre)   |
  |                                                    | exists(id)? ---------->| GET items/cama
  |                                                    |<------------------------| not found
  |                                                    | validate recetaMatriz  |
  |                                                    | compute ingredientes   |
  |                                                    | set(items/cama, item) ->| write
  |  200 / redirect                                    |                        |
  |<----------------------------------------------------|                        |
```

If `exists(id)` returns a document, `ItemService` throws instead of
reaching the write step; the controller responds 409 / re-renders with an
error, and no write happens.

## Error Handling

- Blank `nombre`: unchanged (existing 400 / re-render behavior).
- `recetaMatriz` present but not exactly 9 entries: 400 (REST) /
  re-render with `"La receta debe tener exactamente 9 casillas."` (web).
- `esMateriaPrima = true` with a non-empty `recetaMatriz`: 400 / re-render
  with `"Una materia prima no puede tener receta."`.
- Duplicate slug on create (an item with the derived id already exists):
  409 (REST) / re-render the create form with
  `"Ya existe un item con ese nombre."` (web). `update` is unaffected — it
  targets an existing id directly and never re-slugifies.

## Testing

This codebase's existing practice (see `ItemServiceTest`, which only
covers the static `filter` method, never `create`/`update`/`delete`
against real Firestore) is to unit-test pure logic and leave
Firestore/HTTP orchestration to manual verification plus the full
`gradlew.bat test`/`build` run. This change follows the same split:

- `Slugs`: unit tests for lowercasing, accent stripping, space-to-`_`,
  and multiple/leading/trailing spaces.
- `ItemService.validateRecetaMatriz` and `ItemService.computeIngredientes`
  (package-private static methods, same style as `filter`): unit tests
  for wrong-size rejection, materia-prima-with-recipe rejection,
  ingredient counting over a populated `recetaMatriz`, and the empty/null
  case. The Firestore orchestration inside `create`/`update` (slug id
  generation, duplicate-check read, the write itself) is exercised via
  manual verification (curl/browser against the real Firestore project)
  plus the controller tests below, consistent with this class's existing
  untested `create`/`update`/`delete` Firestore calls.
- `ItemController`/`WebItemController`: 400/409/re-render for each new
  validation failure, using a mocked `ItemService` (existing
  `@WebMvcTest` + `@MockitoBean` pattern) — these don't touch Firestore.
- `MinecraftEsEn.translate`: unit tests translating known multi-word
  names (e.g. `"Tablones de Roble"` → contains `oak` and `planks`,
  stopword `de` dropped) and an untranslatable token passing through
  unchanged. Pure function, no HTTP.
- `IconSuggestionService.rank` (package-private static method, same
  style as `ItemService.filter`): unit tests over hand-built candidate
  data — namespace prioritization (a `minecraft:` result ranks above a
  modded one for the same query) and top-5 truncation. The live call to
  `blocksitems.com` is verified manually (already done during design:
  `search=clay`, `search=oak planks` confirmed working), consistent with
  `FirebaseIdentityService`'s HTTP call also being untested.
- `ItemController`'s new endpoint: 401 without a session (existing
  `ApiAuthInterceptor` behavior); 200 with the mocked service's
  candidates when authenticated.

No new test framework or dependency; `gradlew.bat test` remains the
verify command.
