# Spec: Perfil (User Profile, self-service)

## Requirements

### Requirement: Profile read/write endpoints SHALL operate on the caller's own session identity

The system SHALL derive the target user id for profile read and write
operations exclusively from the authenticated session, and MUST NOT accept
it as client-supplied input (path variable, query parameter, or body field).

#### Scenario: Reading own profile

- **GIVEN** an authenticated session with `uid = "abc123"`
- **WHEN** the client sends `GET /api/perfil/me`
- **THEN** the response SHALL be the profile stored at `users/abc123`

#### Scenario: Unauthenticated read is rejected

- **GIVEN** a request with no authenticated session
- **WHEN** the client sends `GET /api/perfil/me`
- **THEN** the response status SHALL be 401

#### Scenario: A user cannot target another user's profile

- **GIVEN** an authenticated session with `uid = "abc123"`
- **WHEN** the client sends `PUT /api/perfil/me` with any body
- **THEN** the write SHALL apply to `users/abc123` only
- **AND** no request parameter or path segment naming a different uid SHALL
  be accepted by this endpoint (the endpoint accepts no uid input at all)

### Requirement: The `rol` field SHALL NOT be self-editable

The system MUST preserve a profile's existing `rol` value across a
self-service save, regardless of any `rol` value submitted by the client.
A brand-new profile (no prior document) SHALL default `rol` to `"USUARIO"`.

#### Scenario: Attempting self-service role escalation

- **GIVEN** a stored profile with `rol = "USUARIO"`
- **WHEN** the owning session sends `PUT /api/perfil/me` with
  `{"rol": "ADMIN", ...}`
- **THEN** the stored `rol` SHALL remain `"USUARIO"` after the request

#### Scenario: First save of a new profile

- **GIVEN** no profile document exists yet for the session's uid
- **WHEN** the owning session saves a profile for the first time
- **THEN** the stored `rol` SHALL be `"USUARIO"`


### Requirement: A user SHALL be able to delete their own account

The system SHALL provide a way for an authenticated user to permanently
delete their own profile document and their Firebase Authentication user
record, and SHALL invalidate their session as part of the same action.

#### Scenario: Deleting own account via the web UI

- **GIVEN** an authenticated session with `uid = "abc123"`
- **WHEN** the client sends `POST /perfil/eliminar`
- **THEN** the `users/abc123` Firestore document SHALL be deleted
- **AND** the Firebase Auth user `abc123` SHALL be deleted
- **AND** the session SHALL be invalidated
- **AND** the response SHALL redirect to `/login`

#### Scenario: Deleting own account via the REST API

- **GIVEN** an authenticated session with `uid = "abc123"`
- **WHEN** the client sends `DELETE /api/perfil/me`
- **THEN** the `users/abc123` Firestore document SHALL be deleted

#### Scenario: Unauthenticated delete is rejected

- **GIVEN** a request with no authenticated session
- **WHEN** the client sends `DELETE /api/perfil/me`
- **THEN** the response status SHALL be 401
