# Spec Delta: Admin (User Management)

## ADDED Requirements

### Requirement: Admin routes SHALL be reachable only by a session with rol=ADMIN

`/api/admin/**` MUST reject any request whose session does not carry
`rol = "ADMIN"` with HTTP 403. Web admin routes MUST redirect to `/items`
with an error message under the same condition (or `/login` if there is no
session at all).

#### Scenario: Non-admin session hits an admin REST route

- **GIVEN** an authenticated session with `rol = "USUARIO"`
- **WHEN** the client sends any request to `/api/admin/usuarios`
- **THEN** the response status SHALL be 403

#### Scenario: Unauthenticated request hits an admin REST route

- **GIVEN** a request with no session
- **WHEN** the client sends any request under `/api/admin/**`
- **THEN** the response status SHALL be 401 (from the existing session
  check, before the admin-role check ever runs)

#### Scenario: Admin session reaches an admin route

- **GIVEN** an authenticated session with `rol = "ADMIN"`
- **WHEN** the client sends `GET /api/admin/usuarios`
- **THEN** the request SHALL reach the controller and succeed

### Requirement: Web admin routes SHALL redirect non-admins instead of rendering

`/admin/usuarios` and its sub-routes MUST NOT render for a session that
lacks `rol = "ADMIN"`: a session with no `uid` at all redirects to `/login`;
an authenticated non-admin session redirects to `/items` with an error
message.

#### Scenario: Non-admin web session visits the admin panel

- **GIVEN** an authenticated session with `rol = "USUARIO"`
- **WHEN** the client sends `GET /admin/usuarios`
- **THEN** the response SHALL redirect to `/items`
- **AND** the admin user list SHALL NOT be rendered

#### Scenario: Unauthenticated request visits the admin panel

- **GIVEN** a request with no session
- **WHEN** the client sends `GET /admin/usuarios`
- **THEN** the response SHALL redirect to `/login`

### Requirement: An admin SHALL be able to list, view, update, and delete other users

`GET /api/admin/usuarios` SHALL return all stored profiles (each including
its `uid`). `GET /api/admin/usuarios/{uid}` SHALL return one profile or 404.
`PUT /api/admin/usuarios/{uid}` SHALL update the target's `nombre`, `email`,
and `rol`. `DELETE /api/admin/usuarios/{uid}` SHALL delete the target's
Firestore profile and Firebase Auth account.

#### Scenario: Listing all users

- **GIVEN** an admin session
- **WHEN** the client sends `GET /api/admin/usuarios`
- **THEN** the response SHALL include every stored profile with its `uid`

#### Scenario: Updating another user's role

- **GIVEN** an admin session
- **AND** a target user `uid = "user-2"` with `rol = "USUARIO"`
- **WHEN** the admin sends `PUT /api/admin/usuarios/user-2` with
  `{"rol": "ADMIN", ...}`
- **THEN** the stored profile for `user-2` SHALL have `rol = "ADMIN"`

#### Scenario: Deleting another user removes both records

- **GIVEN** an admin session
- **AND** a target user `uid = "user-2"`
- **WHEN** the admin sends `DELETE /api/admin/usuarios/user-2`
- **THEN** the `users/user-2` Firestore document SHALL be deleted
- **AND** the Firebase Auth user `user-2` SHALL be deleted

### Requirement: A `rol` value outside the fixed set SHALL be rejected

`PUT /api/admin/usuarios/{uid}` MUST reject a `rol` value that is not
exactly `"USUARIO"` or `"ADMIN"` with HTTP 400, and MUST NOT persist it.

#### Scenario: Invalid role value

- **GIVEN** an admin session
- **WHEN** the admin sends `PUT /api/admin/usuarios/user-2` with
  `{"rol": "SUPERADMIN", ...}`
- **THEN** the response status SHALL be 400
- **AND** the stored `rol` for `user-2` SHALL NOT change

### Requirement: An admin SHALL NOT modify or delete their own account through the admin panel

`PUT /api/admin/usuarios/{uid}` and `DELETE /api/admin/usuarios/{uid}` MUST
reject a request where `{uid}` equals the caller's own session uid, with
HTTP 400. Self-service changes to one's own account remain available only
through `/api/perfil/me` and `/perfil`.

#### Scenario: Admin attempts to edit their own account via the admin endpoint

- **GIVEN** an admin session with `uid = "admin-1"`
- **WHEN** the admin sends `PUT /api/admin/usuarios/admin-1` with any body
- **THEN** the response status SHALL be 400
- **AND** no change SHALL be persisted

#### Scenario: Admin attempts to delete their own account via the admin endpoint

- **GIVEN** an admin session with `uid = "admin-1"`
- **WHEN** the admin sends `DELETE /api/admin/usuarios/admin-1`
- **THEN** the response status SHALL be 400
- **AND** neither the Firestore profile nor the Firebase Auth account SHALL
  be deleted
