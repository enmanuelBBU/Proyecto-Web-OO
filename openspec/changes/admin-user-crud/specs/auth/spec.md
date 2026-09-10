# Spec Delta: Auth

## ADDED Requirements

### Requirement: A configured list of admin emails SHALL bootstrap the first admin accounts

The system SHALL read a configurable, comma-separated list of email addresses
(`admin.emails`). Any account whose email is in that list SHALL have its
profile's `rol` set to `"ADMIN"`, either at registration time or the next
time it logs in.

#### Scenario: Registering with a listed email

- **GIVEN** `admin.emails` contains `"root@example.com"`
- **WHEN** a client registers with `email = "root@example.com"`
- **THEN** the created profile's `rol` SHALL be `"ADMIN"`

#### Scenario: Logging in with a listed email promotes an existing profile

- **GIVEN** `admin.emails` contains `"root@example.com"`
- **AND** a stored profile for that email has `rol = "USUARIO"`
- **WHEN** the account logs in successfully
- **THEN** the stored profile's `rol` SHALL be updated to `"ADMIN"`

#### Scenario: Logging in with a non-listed email leaves the role untouched

- **GIVEN** `admin.emails` does not contain the logging-in account's email
- **WHEN** the account logs in successfully
- **THEN** the stored profile's `rol` SHALL NOT be changed

### Requirement: The session SHALL cache the authenticated profile's role

Login and registration SHALL store the resolved `rol` (after any bootstrap
promotion) as a session attribute, for use by authorization checks that
should not require a Firestore read per request.

#### Scenario: Session carries the role after login

- **GIVEN** a successful login for a profile with `rol = "ADMIN"`
- **WHEN** the login completes
- **THEN** the session SHALL carry a `rol` attribute equal to `"ADMIN"`
