# Proposal: Complete Auth + Perfil + Items CRUD + Search

## Why

The project currently ships partial, insecure implementations of authentication,
user profile management, and item CRUD/search — the four capabilities the
project owner identified as "should already be complete" among the 15 required
features. Three concrete problems block calling any of them done:

1. **Privilege escalation**: the self-service profile form lets any
   authenticated user set their own `rol` field, including to an admin value.
2. **IDOR (insecure direct object reference)**: `GET/PUT /api/perfil/{uid}`
   trusts the `uid` path segment instead of the session, so any authenticated
   user can read or overwrite another user's profile.
3. **Wrong data shape for `Item`**: the current model carries `cantidad`
   (a per-item global stock number) and an unused `icono` field, and uses
   `tipo` where the reference domain model (see `proyecto-node`, the team's
   parallel prototype) uses `categoria` + `esMateriaPrima`. Quantity belongs
   to a future per-user inventory, not the item catalog — leaving it here now
   would force a breaking migration later.

## What Changes

- **Auth**: redirect an already-authenticated session away from `/login` and
  `/register`; no other behavioral change (Firebase Identity Toolkit sign-in/
  sign-up/session flow already works).
- **Perfil (self-service CRUD)**: replace `/api/perfil/{uid}` with
  `/api/perfil/me` (uid derived from session); add `DELETE /api/perfil/me`
  and a web "eliminar cuenta" action that removes the Firestore profile, the
  Firebase Auth user, and invalidates the session; remove the editable `rol`
  input from the self-service form (role editing moves to the future admin
  CRUD).
- **Items (CRUD + search)**: rename `tipo` → `categoria`, add
  `esMateriaPrima` (boolean, default false), remove `cantidad` and `icono`;
  add real validation error handling to the web forms (today failures are
  silently swallowed); make `PUT/DELETE /api/items/{id}` return 404 for a
  non-existent id instead of silently upserting; extend search (`q`) to match
  `categoria` as well as `nombre`, and add an `esMateriaPrima` filter — this
  is how "gestión de materiales" is satisfied for now (filtered view of the
  same catalog, not a new entity).

## Non-Goals (deferred to later sub-projects)

- Per-user inventory (quantities), construction projects, recipes/crafting
  calculator/3x3 grid, 3D model viewer, toast notifications, admin user CRUD.
- Any change to Firestore security rules or a move to Spring Security — out
  of scope; the existing session-attribute + `HandlerInterceptor` pattern is
  kept.

## Rollback Plan

All changes are additive-or-renaming on a single Firestore collection each
(`items`, `users`) with no production data (development project). Rollback is
a plain `git revert` of the implementation commit(s); no data migration script
is required in either direction since documents are fully overwritten on next
write (Firestore `set()` replaces the whole document).

## Impact

- Code: `items/*`, `perfil/*`, `auth/WebAuthController` (login/register
  redirect only), templates `items.html`, `item-edit.html`, `perfil.html`.
- Tests: new/updated `@WebMvcTest` slices for `ItemController` and a new one
  for `UserProfileController`.
- No new dependencies.
