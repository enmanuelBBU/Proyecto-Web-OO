# Design: Complete Auth + Perfil + Items CRUD + Search

## Architecture

No structural change: Spring Boot MVC + Thymeleaf (web layer) alongside a
parallel REST API (`/api/**`), both backed by Firestore, both gated by the
existing `ApiAuthInterceptor` (REST) and per-method `HttpSession` checks
(web). This change edits the `items` and `perfil` packages only; no new
package, bean, or dependency is introduced.

## Data Model

### `Item` (Firestore collection `items`)

| Field | Type | Notes |
|---|---|---|
| `id` | String | Firestore-managed, unchanged |
| `nombre` | String | `@NotBlank`, unchanged |
| `categoria` | String | optional; renamed from `tipo`; no forced default (previously defaulted to `"BLOQUE"`) |
| `esMateriaPrima` | boolean | new; default `false` |

Removed: `tipo` (renamed), `cantidad` (belongs to a future per-user
inventory), `icono` (unused in any template today).

**Rationale**: mirrors the field set of the team's reference prototype
(`proyecto-node`: `nombre`, `categoria`, `es_materia_prima`), adapted to Java
camelCase naming — there is no requirement to match the prototype's Firestore
field names byte-for-byte, since the two backends do not share a database.

### `UserProfile` (Firestore collection `users`, doc id = Firebase uid)

No field changes. Behavioral change only: `rol` is preserved as-is on
self-service save (see below) instead of being overwritten from request
input.

## Component Changes

### `auth` package

`WebAuthController.login()` / `.register()` (GET handlers): if
`session.getAttribute("uid") != null`, return `"redirect:/items"` before
rendering the template. No change to `AuthController`, `AuthService`,
`FirebaseIdentityService`, or `ApiAuthInterceptor`.

### `perfil` package

- `UserProfileController`: routes change from `/api/perfil/{uid}` to
  `/api/perfil/me` for GET and PUT; the uid is read from `HttpSession`
  (inject `HttpServletRequest` or `HttpSession`), never from client input.
  Add `DELETE /api/perfil/me`.
- `UserProfileService`: add `delete(String uid)` — deletes the Firestore doc
  at `users/{uid}`. Deleting the Firebase Auth user itself
  (`FirebaseAuth.getInstance().deleteUser(uid)`) happens in the controller/a
  new small method in `AuthService`, since `UserProfileService` has no
  Firebase Auth dependency today and shouldn't gain one for a single call.
- `UserProfileController.save()` / `WebProfileController.save()`: the
  incoming `rol` parameter is dropped; the service loads the existing profile
  (if any) and preserves its `rol`, defaulting to `"USUARIO"` only when no
  profile exists yet (first save). This is what "rol not self-editable"
  means in practice — the field still exists and is still displayed, it just
  can't be changed through this path.
- `WebProfileController`: add `POST /perfil/eliminar` → calls delete, then
  `session.invalidate()`, redirects to `/login`.
- `perfil.html`: replace the `Rol` `<input>` with a read-only display
  (e.g. `<span class="badge">`); add a "Eliminar cuenta" form/button
  (`.btn-danger`) posting to `/perfil/eliminar`, with a plain browser
  `confirm()` guard (no new modal component — out of scope for this change;
  toasts/modals are a separate later sub-project).

### `items` package

- `Item.java`: field changes as in Data Model above.
- `ItemService`:
  - `list(String q, Boolean esMateriaPrima)`: filter predicate extended to
    match `q` against `nombre` OR `categoria` (case-insensitive contains,
    same O(n) scan as today — still fine at this data volume per the existing
    code comment); when `esMateriaPrima` is non-null, additionally filter by
    that flag.
  - `update(String id, Item item)`: check existence first
    (`db.collection("items").document(id).get().get().exists()`); return
    `null` if missing. `delete(String id)`: same existence check.
- `ItemController`: `update`/`delete` return `404` when the service returns
  `null` / reports not-found, instead of the current silent-upsert behavior.
- `WebItemController`: create/update handlers gain a validation branch (blank
  `nombre` → re-render `items`/`item-edit` with `error`, mirroring the
  `login`/`register` pattern) instead of relying solely on the HTML
  `required` attribute; forms pass `categoria` and `esMateriaPrima` (checkbox)
  instead of `tipo`/`cantidad`.
- `items.html` / `item-edit.html`: field updates (`categoria` text input,
  `esMateriaPrima` checkbox), search form gains the materiales filter
  checkbox, table drops the `Cantidad` column and relabels `Tipo` → `Categoría`.

## Sequence: Delete Own Account

**Revised during final review.** The order below is Auth-first, then
Firestore — the reverse of this document's original proposal. Rationale
follows the diagram.

```
Constructor          WebProfileController      AuthService/FirebaseAuth     UserProfileService      HttpSession
    |  POST /perfil/eliminar   |                       |                        |                       |
    |-------------------------->|                       |                        |                       |
    |                          | uid = session.uid     |                        |                       |
    |                          |----------------------->| deleteUser(uid)        |                       |
    |                          |                       |  (Auth user gone)      |                       |
    |                          |----------------------------------------------->| delete(uid)            |
    |                          |                       |                       |  (Firestore doc gone)  |
    |                          |<-----------------------------------------------|                        |
    |                          | session.invalidate() (always, via finally)    |                       |
    |                          |------------------------------------------------------------------------>|
    |  redirect: /login         |                       |                        |                       |
    |<--------------------------|                       |                        |                       |
```

Both calls are wrapped in a single `try { ... } finally { session.invalidate(); }`
block, so the session is always cleaned up regardless of which step fails.

**Why Auth-first, not Firestore-first (original text corrected):** this
document originally claimed that if the Auth deletion failed after the
Firestore doc was already deleted, "they can no longer authenticate as that
Firestore profile is gone" — that claim is wrong. Login goes through
Firebase Identity Toolkit (`FirebaseIdentityService.signIn`), which has no
dependency on the Firestore `users` collection at all; `WebAuthController`
sets the session purely from the sign-in response. So Firestore-first would
have left, on a failed Auth deletion, a **live Firebase Auth account with no
profile** — the user could still log back in, just into a broken,
profile-less state (`WebProfileController.show()` would synthesize a blank
one from the session email). Auth-first means a failed Firestore deletion
instead leaves an **orphaned, unreachable Firestore document** — the Auth
account is already gone, so nobody can authenticate as that uid again to
reach it. That is the safer partial-failure state, achieved with the exact
same no-retry/no-compensation simplicity this document originally intended:
no compensating transaction is added either way, only the order of two
already-independent calls changed.

## Error Handling

- Web: validation and not-found failures re-render the originating template
  with a Spanish, actionable `error` message (per `DESIGN-SYSTEM.md` §8
  voice/tone rules), preserving user-entered values where practical.
- REST: bean validation failures keep relying on Spring's default 400
  response (unchanged pattern); not-found on update/delete now explicitly
  returns 404 instead of masking the bug as a successful upsert.

## Testing

Extend the existing `@WebMvcTest` + `MockitoBean` slice-test pattern
(`ItemApiAuthTest`):

- `ItemController`: 401 without session on create/update/delete (list is
  already covered); 400 on blank `nombre`; 404 on update/delete of an unknown
  id.
- New `UserProfileControllerAuthTest`: 401 without session on
  `GET/PUT/DELETE /api/perfil/me`; 200 with session for get/update; a session
  for user A must not be able to affect user B's data — since the endpoint no
  longer takes a uid param at all, this is structurally guaranteed rather
  than tested via a specific attack payload.

No test framework or dependency changes; `gradlew.bat test` remains the
verify command per `openspec/config.yaml`.
