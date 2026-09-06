# Tasks: Complete Auth + Perfil + Items CRUD + Search

Each task is TDD-first per `openspec/config.yaml` (`apply.tdd: true`,
`testing.strict_tdd: true`): write/adjust the failing test before the
production code that makes it pass, then run `gradlew.bat test`.

## 1. Items — data model

- 1.1 Update `Item.java`: rename `tipo` → `categoria`, add `esMateriaPrima`
      (boolean, default `false`), remove `cantidad` and `icono`.

## 2. Items — service layer

- 2.1 Test: `ItemService.list` matches `q` against `nombre` OR `categoria`.
- 2.2 Test: `ItemService.list` filters by `esMateriaPrima` when provided.
- 2.3 Implement `list(String q, Boolean esMateriaPrima)` to satisfy 2.1–2.2.
- 2.4 Test: `ItemService.update` on an unknown id returns `null`
      (does not create a document).
- 2.5 Test: `ItemService.delete` on an unknown id is a no-op / signals
      not-found (matches whatever contract 2.4 established).
- 2.6 Implement existence checks in `update`/`delete` to satisfy 2.4–2.5.

## 3. Items — REST API

- 3.1 Test (`ItemApiAuthTest` or new class): 401 without session on
      `POST/PUT/DELETE /api/items*`.
- 3.2 Test: `POST /api/items` with blank `nombre` → 400.
- 3.3 Test: `PUT /api/items/{id}` / `DELETE /api/items/{id}` with an unknown
      id → 404.
- 3.4 Implement `ItemController` changes (404 mapping) to satisfy 3.1–3.3.

## 4. Items — web UI

- 4.1 Update `items.html`: search form gains `esMateriaPrima` checkbox;
      create form uses `categoria` + `esMateriaPrima`, drops `tipo`/`cantidad`;
      table drops `Cantidad` column, relabels `Tipo` → `Categoría`.
- 4.2 Update `item-edit.html`: same field changes as 4.1.
- 4.3 Update `WebItemController`: pass `esMateriaPrima` through create/update;
      add validation branch (blank `nombre` → re-render with `error`).

## 5. Perfil — self-service API

- 5.1 Test: `GET/PUT/DELETE /api/perfil/me` → 401 without session.
- 5.2 Test: `PUT /api/perfil/me` with a `rol` field in the request body does
      not change the stored `rol`.
- 5.3 Implement `UserProfileController` (`/api/perfil/me`, uid from session)
      and `UserProfileService.save`/`delete` changes to satisfy 5.1–5.2.

## 6. Perfil — account deletion

- 6.1 Test: deleting the account removes the Firestore `users/{uid}` doc.
- 6.2 Implement `UserProfileService.delete` and wire the Firebase Auth user
      deletion (`AuthService` or equivalent) to satisfy 6.1.
- 6.3 Update `WebProfileController`: add `POST /perfil/eliminar` →
      delete + `session.invalidate()` + redirect to `/login`.
- 6.4 Update `perfil.html`: replace editable `Rol` input with read-only
      display; add "Eliminar cuenta" button/form with a `confirm()` guard.

## 7. Auth — redirect if already authenticated

- 7.1 Update `WebAuthController.login()`/`register()`: redirect to `/items`
      when a session `uid` already exists.

## 8. Verification

- 8.1 Run `gradlew.bat test` (full suite) and `gradlew.bat build`; confirm
      green before handing off.
- 8.2 Manual smoke check in-browser: login/register redirect-if-authenticated,
      item create/search/materiales-filter/edit/delete, perfil update
      (rol unaffected by tampering with the form), perfil delete-account flow.

## 9. Documentation

- 9.1 Write a Markdown doc (path decided at hand-off time, e.g.
      `docs/complete-auth-perfil-items.md`) summarizing what changed, the new
      API surface, and the data-model migration notes from the proposal —
      requested by the project owner for after implementation completes.
      No commit is made; the user commits themselves.
