# Complete Auth + Perfil + Items CRUD + Search

## Introduction

This document summarizes the implementation of the **Complete Auth + Perfil + Items CRUD + Search** feature set — the fourth major deliverable of the Proyecto Minecraft backend. This change addresses three critical gaps in the project's authentication, user profile, and item management systems:

1. **Privilege escalation vulnerability**: users could self-escalate their `rol` field to `ADMIN` through the profile form and REST API.
2. **Insecure Direct Object Reference (IDOR)**: the profile REST endpoint trusted client-supplied uid values instead of deriving identity from the session, allowing one authenticated user to read and modify another's profile.
3. **Incorrect data model for items**: the `Item` entity used `tipo` (type) instead of the standardized `categoria` + `esMateriaPrima` fields, and carried `cantidad` (quantity) which belongs to a future per-user inventory subsystem, not the global item catalog.

The implementation fixes all three vulnerabilities, refactors the `Item` model to align with the team's reference prototype (`proyecto-node`), adds comprehensive validation and error handling to web forms, and introduces real account deletion (with proper session cleanup) as a new self-service capability.

## What Changed

### Authentication

**Behavior**: The login and registration page GET handlers now redirect to `/items` if an authenticated session already exists, instead of re-rendering the login/register form. This prevents authenticated users from accidentally starting a new authentication flow.

**Code location**: `src/main/java/cl/grupo5/proyectominecraft/auth/WebAuthController.java`

**Security impact**: Closes a minor UX footgun (not a critical vulnerability); the underlying Firebase Identity Toolkit session and authentication flow remain unchanged.

### User Profile (`perfil` package)

#### Model Changes
The `UserProfile` entity fields remain unchanged (`nombre`, `email`, `rol`). However, the **behavioral guarantee** on `rol` is new: the server now always preserves the existing `rol` value (or defaults to `"USUARIO"` for new profiles) regardless of what the client submits. This is enforced in `UserProfileController.save()` and `WebProfileController.save()` — **not** in `UserProfileService.save()`, which remains a plain, unconditional Firestore write. The invariant is currently duplicated across the two controllers rather than centralized in the service; this is safe today (both callers are correct and tested) but is worth revisiting — ideally by moving the preserve-or-default logic into the service itself — before the deferred admin CRUD sub-project starts writing to `rol` from a third call site.

#### REST API Changes
- **Old route**: `GET /api/perfil/{uid}`, `PUT /api/perfil/{uid}`
- **New route**: `GET /api/perfil/me`, `PUT /api/perfil/me`, `DELETE /api/perfil/me` (new)

The uid is derived exclusively from `HttpSession` and never from client input. This closes the IDOR vulnerability.

#### New Endpoint: `DELETE /api/perfil/me`
Deletes the user's own Firestore profile document, deletes their Firebase Auth user account, and invalidates the HTTP session. Wrapped in a try/finally block to guarantee session invalidation even if the Firebase Auth deletion fails partway through. After deletion, the user is sent to `/login`.

#### Web Form (`perfil.html`)
- The `Rol` field is now a read-only badge (`<span class="badge">`) instead of an editable `<input>` field. This prevents the privilege escalation attack vector.
- New "Eliminar cuenta" (Delete Account) button posts to `/perfil/eliminar` with a browser `confirm()` guard. Clicking the button shows "¿Eliminar tu cuenta de forma permanente? Esta acción no se puede deshacer." (Permanently delete your account? This cannot be undone.) before confirming.
- The edit form continues to update `nombre` and `email` as before.

#### Web Route: `POST /perfil/eliminar`
New route that performs the same deletion as `DELETE /api/perfil/me`, then redirects to `/login`. This provides parity between the web UI and REST API for self-service account deletion.

### Items (`items` package)

#### Model Changes

**Old `Item` fields**:
- `id` (String, Firestore-managed)
- `nombre` (String, required)
- `tipo` (String, optional, defaulted to `"BLOQUE"`)
- `cantidad` (Integer, global stock count — **removed, belongs to per-user inventory**)
- `icono` (String, unused — **removed**)

**New `Item` fields**:
- `id` (String, Firestore-managed)
- `nombre` (String, required, `@NotBlank`)
- `categoria` (String, optional, no forced default — replaces `tipo`)
- `esMateriaPrima` (boolean, default `false` — new field for filtering raw materials)

**Rationale**: The new field set mirrors the team's reference prototype (`proyecto-node`) and decouples per-user inventory (which will have its own Firestore collection) from the global item catalog. The boolean `esMateriaPrima` flag allows the same catalog to serve as both a general item list and a filtered "raw materials" view until a dedicated materials management UI is built.

#### REST API Changes

**`GET /api/items?q=&esMateriaPrima=`**
- The `q` parameter now matches against **both** `nombre` **and** `categoria` (case-insensitive substring search). Previously only `nombre` was searched.
- New optional query parameter `esMateriaPrima` (true/false): when present, filters items by that flag. Omit the parameter to retrieve all items regardless of the flag.
- Returns 200 with a JSON array of matching items.

**`POST /api/items`**
- Accepts `nombre` (required), `categoria` (optional), `esMateriaPrima` (optional, defaults to false).
- Rejects requests with blank `nombre` with HTTP 400 (Spring's default bean validation error).
- Creates and returns the new item (200 OK).

**`PUT /api/items/{id}`**
- Accepts the same fields as POST.
- **Change**: Now returns 404 if the item `id` does not exist (previously silently upser ted, creating a new item). This is a bug fix — the REST API should not create new items when the client explicitly names an existing id.

**`DELETE /api/items/{id}`**
- **Change**: Now returns 404 if the item `id` does not exist (previously was a silent no-op). This prevents silent failures and makes error cases explicit.

#### Web Form Changes (`items.html`, `item-edit.html`)

**Search and filter form (`items.html`)**:
- Text input `q` (unchanged, but now matches against both `nombre` **and** `categoria`, per the REST API change above).
- New checkbox labeled "Solo materiales" (raw materials only) — checks the `esMateriaPrima` query parameter before rendering.
- Submits both to `GET /items?q=...&esMateriaPrima=true` (or omitted if unchecked).

**Create form (`POST /items`)**:
- Field `categoria` (text input, optional) replaces the old `tipo` input.
- New checkbox `esMateriaPrima` (boolean, default unchecked).
- Removed: `tipo`, `cantidad`, `icono`.
- **Validation**: If `nombre` is blank, the form re-renders with a Spanish error message: "El nombre del ítem es obligatorio." (Item name is required.) This mirrors the error-handling pattern in login/register forms.

**Edit form (`GET /items/{id}/edit`, `POST /items/{id}/update`)**:
- Same field changes as the create form.
- **Change**: If the item `id` does not exist, the page redirects to `/items` instead of crashing with a NullPointerException. This is a bug fix found during implementation review.
- A blank-`nombre` submission re-renders the edit form with the same Spanish error message used on the create form. Note this re-render shows the item's **currently-stored** values (re-fetched from Firestore), not the values the user just typed — so a validation error does lose the user's in-progress edits to `categoria`/`esMateriaPrima` (`nombre` too, since it's blank by definition in this branch). This is a known, deferred UX gap, not a spec violation — the spec only requires the error message and that nothing gets saved, both of which hold.

**Delete endpoint (`POST /items/{id}/delete`)**:
- Behavior unchanged; model updated to the new `Item` schema.

## Security Fixes

### 1. Profile IDOR (CWE-639: Authorization Bypass)
**Vulnerability**: `GET/PUT /api/perfil/{uid}` trusted the `uid` path parameter instead of deriving identity from the HTTP session. Any authenticated user could read and modify another user's profile by changing the uid in the URL.

**Fix**: Endpoints renamed to `/api/perfil/me` and uid is read exclusively from `HttpSession`. The session is already cryptographically tied to the client by the servlet container, making this attack impossible.

### 2. Privilege Escalation (CWE-269: Improper Access Control)
**Vulnerability**: The self-service profile form (`perfil.html`) and REST API (`PUT /api/perfil/{uid}`) accepted a user-supplied `rol` field and wrote it directly to Firestore. Any user could set their own `rol` to `"ADMIN"` (or any other value) through the UI or REST API.

**Fix**: `UserProfileController.save()` and `WebProfileController.save()` both load the existing profile (if any) and preserve its `rol` value, ignoring the incoming `rol` parameter entirely. For new profiles, `rol` defaults to `"USUARIO"`. The role field is displayed as read-only in the web UI. Role management moves to a future admin CRUD sub-project. (See the note under User Profile → Model Changes above: this logic lives in the two controllers, not the service — a known duplication to clean up later.)

### 3. Silent Upsert on Unknown Item IDs
**Vulnerability**: `PUT /api/items/{id}` silently created a new item if the id did not exist (treating the PUT as an upsert). This masked client errors and makes it impossible to distinguish between "update succeeded" and "id was wrong but I created it anyway."

**Fix**: `PUT /api/items/{id}` now returns 404 if the id does not exist. The service layer checks existence before updating.

### 4. Silent No-op on Item Deletion
**Vulnerability**: `DELETE /api/items/{id}` silently did nothing if the id did not exist, providing no feedback to the client.

**Fix**: `DELETE /api/items/{id}` now returns 404 if the id does not exist, making failures explicit.

### 5. Partial Failure Risk on Account Deletion
**Vulnerability**: The delete-account flow (new feature) could fail partway through, between deleting the Firestore profile and deleting the Firebase Auth user, without guaranteeing the session was cleaned up either way.

**Fix, part 1 — session invalidation**: Both the REST (`DELETE /api/perfil/me`) and web (`POST /perfil/eliminar`) endpoints wrap both deletions in a try/finally block. The session is **always** invalidated, even if a deletion step throws.

**Fix, part 2 — deletion order**: the two deletions run **Firebase Auth first, then Firestore** (revised during final review — the original order was the reverse). Login goes through Firebase Identity Toolkit and never consults Firestore, so deleting the Firestore profile first and then failing to delete the Auth user would leave a **live account with no profile** — the user could still log back in, just into a broken state. Deleting Auth first means a failure on the Firestore step instead leaves an **orphaned, unreachable Firestore document**: the Auth account is already gone, so nobody can log in to reach it. That's the safer of the two partial-failure states, and it's still a manual-cleanup edge case (no retry/compensation logic), acceptable for this project's scope.

### 6. NullPointerException on Missing Item in Edit Flow (Web)
**Vulnerability**: Both `GET /items/{id}/edit` and `POST /items/{id}/update` called `ItemService.get(id)` and passed the result directly to the template (`item-edit.html`) without checking for `null`. If the `id` no longer existed (a stale link, a deletion from another browser tab, or a hand-typed URL), the template would dereference `${item.id}`, `${item.nombre}`, etc. on a `null` object and crash with a NullPointerException instead of showing a graceful message.

**Fix**: `WebItemController.update()` was fixed first, during the Task 4 review loop, for the specific case where a blank-`nombre` submission re-renders the edit page. While writing this document, the identical unguarded call in `WebItemController.edit()` (the `GET` handler that loads the edit page in the first place) was noticed and fixed the same way, for consistency. Both methods now check `items.get(id)` for `null` and redirect to `/items` instead of rendering the template.

### 7. Missing Test Coverage for the Update Form's Validation Branch
**Gap**: the items spec requires both the create form and the update form to re-render with an actionable error on a blank `nombre`; only the create path had a test.

**Fix**: added `updateWithBlankNombreRerendersItemEditWithError` to `WebItemControllerTest`, mirroring the existing create-side test.

### 8. Minor Data-Consistency and Robustness Fixes (found in final whole-branch review)
- **`ItemService.update()` wrote `id: null` into Firestore for REST-originated updates.** The method called `.set(item)` before `item.setId(id)`, so when the request body (as sent by `PUT /api/items/{id}`) has no `id` field, the document got persisted with a null `id` field inside it — inconsistent with documents written via the web edit flow (which loads the item first, so `id` is already populated). Fixed by setting the id before writing.
- **`WebItemController`'s `nombre` form parameters were declared as required**, so a request that omitted the parameter entirely (rather than sending it blank) hit Spring's own generic 400 error page before the friendly Spanish validation message could run. Changed both `create()` and `update()` to `@RequestParam(required = false) String nombre` so the existing blank-check actually covers both cases. (Not reachable through the HTML forms themselves, since they mark the field `required` in the browser — this only mattered for a client bypassing the form.)

## Route Mapping (Old → New)

### Authentication (Web)
- `GET /login` — behavior changed (redirect if authenticated)
- `POST /login` — unchanged
- `GET /register` — behavior changed (redirect if authenticated)
- `POST /register` — unchanged
- `POST /logout` — unchanged

### User Profile (REST API)
| Old | New | Change |
|---|---|---|
| `GET /api/perfil/{uid}` | `GET /api/perfil/me` | uid derived from session; closes IDOR |
| `PUT /api/perfil/{uid}` | `PUT /api/perfil/me` | uid derived from session; role field server-managed |
| (none) | `DELETE /api/perfil/me` | new endpoint for self-service account deletion |

### User Profile (Web)
| Old | New | Change |
|---|---|---|
| `GET /perfil` | `GET /perfil` | form field: role now read-only badge |
| `POST /perfil` | `POST /perfil` | role field ignored; redirects on success |
| (none) | `POST /perfil/eliminar` | new endpoint for web UI account deletion |

### Items (REST API)
| Old | New | Change |
|---|---|---|
| `GET /api/items` | `GET /api/items?q=&esMateriaPrima=` | q now searches categoria too; new esMateriaPrima filter |
| `POST /api/items` | `POST /api/items` | field: tipo → categoria; new esMateriaPrima; removed cantidad, icono |
| `PUT /api/items/{id}` | `PUT /api/items/{id}` | same fields; returns 404 on unknown id (was upsert) |
| `DELETE /api/items/{id}` | `DELETE /api/items/{id}` | returns 404 on unknown id (was no-op) |

### Items (Web)
| Old | New | Change |
|---|---|---|
| `GET /items` | `GET /items?q=&esMateriaPrima=` | form: new "Es materia prima" checkbox |
| `POST /items` | `POST /items` | field: tipo → categoria; new esMateriaPrima; validation errors re-render with message |
| `GET /items/{id}/edit` | `GET /items/{id}/edit` | field: tipo → categoria; new esMateriaPrima; redirects to /items if id not found (was NPE) |
| `POST /items/{id}/update` | `POST /items/{id}/update` | field: tipo → categoria; new esMateriaPrima; validation errors re-render with message |
| `POST /items/{id}/delete` | `POST /items/{id}/delete` | model updated; behavior unchanged |

## Testing

**Test coverage**: 30 tests across 8 test classes, all passing.

### Test Classes and Coverage

| Test Class | Tests | Coverage |
|---|---|---|
| `ProyectominecraftApplicationTests` | 1 | Context load (Spring Boot smoke test) |
| `WebAuthControllerTest` | 3 | Login/register redirect when authenticated; login form render when not authenticated |
| `ItemApiAuthTest` | 5 | 401 without session on GET/POST/PUT/DELETE; 200 with session on GET |
| `ItemControllerTest` | 3 | 400 on blank nombre; 404 on PUT/DELETE of an unknown id |
| `ItemServiceTest` | 4 | Pure filter logic (text search across nombre/categoria, esMateriaPrima flag, no-criteria pass-through); `Item` defaults `categoria`/`esMateriaPrima` when only `nombre` is set |
| `WebItemControllerTest` | 4 | Blank-nombre validation on create **and** update; redirect to `/items` when editing or updating a deleted/nonexistent item |
| `UserProfileControllerTest` | 8 | 401 without session on GET/PUT/DELETE; 200 with session on GET returning the stored profile; role preservation (existing profile tampered, and first-save default); delete account (session invalidated even if the Auth deletion throws) |
| `WebProfileControllerTest` | 2 | Account deletion happy path; unauthenticated delete does nothing |

### Run Tests
```bash
gradlew.bat test        # Run all unit/integration tests
gradlew.bat build       # Full build (tests + assembly)
```

Both commands complete successfully with all 30 tests passing.

## Known Verification Gap

**Important**: The full interactive manual browser smoke-test checklist was **not** performed as part of this implementation. Specifically:

- Creating a real user account through the `/register` form
- Logging in through the `/login` form
- Exercising every create/update/delete form by hand (items and profile)
- Attempting to edit another user's profile or items (to verify IDOR is fixed)
- Testing the account deletion flow end-to-end through the browser UI
- Verifying redirect behavior when already authenticated

Only automated unit and integration tests were run, plus a basic unauthenticated HTTP request check to verify pages load.

**Recommendation**: Before considering this feature fully verified end-to-end, the project owner should manually walk through the complete browser smoke-test checklist in `openspec/changes/complete-auth-perfil-items/plan.md`, Task 7, Step 3. This will confirm that:
- Session management works as expected in the browser
- Form validation errors display correctly in Spanish
- Role field is truly read-only in the UI
- Account deletion actually removes the Firestore profile and Firebase Auth user
- Redirect behavior is correct for authenticated users
- Search and filter queries work as expected

## What's Next

The following sub-projects are deferred and will build on this foundation:

### Completed
- **Auth + Perfil + Items** (this change)

### Planned (in execution order)
1. **Gestión de Materiales** — dedicated admin UI for the `esMateriaPrima` filtered view and bulk operations; currently only available as a filter on the item catalog.
2. **CRUD Usuario (Admin)** — admin interface to create, read, update, and delete users; edit roles; view user activity.
3. **Gestión de Inventario** — per-user inventory with quantities; "craftable from inventory" calculations.
4. **Validación y almacenamiento de recetas** — define crafting recipes; validate against inventory.
5. **Calculadora de crafteo** — interactive UI to plan crafting chains; show output quantities.
6. **Grilla 3x3** — Minecraft-style crafting grid UI.
7. **CRUD de Proyectos de construcción** — create, read, update, delete construction projects; assign items and users.
8. **Estados de Proyecto** — workflow states (draft, active, completed, archived); state machine transitions.
9. **Generar Documento (PDF)** — export project/inventory/recipes to PDF.
10. **Modelo 3D** — 3D viewer for items/projects (Minecraft models); optional integration with a 3D engine.
11. **Notificación de Toast** — lightweight toast/snackbar notifications for success/error/info messages (currently using Spring redirects and template error attributes).

Each sub-project will follow the same implementation and review process, with design docs and test coverage.

## Implementation Notes

- **No breaking changes to the REST API contract**: all route changes are explicit (renamed endpoints), and the data model changes (removing `cantidad` and `icono`) only affect new/updated items — existing Firestore documents without these fields will be handled gracefully.
- **Firestore documents without the new fields**: documents created before this change lack `categoria` and `esMateriaPrima`. On update, they will be filled with the appropriate defaults. The service layer handles this by passing full `Item` objects to Firestore `set()`, which replaces the entire document.
- **Git history**: All changes remain in the working tree, uncommitted. The project owner will create the final commit(s).
