# Spec Delta: Auth

## MODIFIED Requirements

### Requirement: Login and registration pages SHALL redirect an already-authenticated session

The system SHALL NOT render the login or registration form to a request that
already carries an authenticated session; it SHALL redirect such requests to
the items list instead.

#### Scenario: Authenticated user opens the login page

- **GIVEN** a request carries an `HttpSession` with a non-null `uid` attribute
- **WHEN** the client sends `GET /login`
- **THEN** the response SHALL be a redirect to `/items`
- **AND** the login form SHALL NOT be rendered

#### Scenario: Authenticated user opens the registration page

- **GIVEN** a request carries an `HttpSession` with a non-null `uid` attribute
- **WHEN** the client sends `GET /register`
- **THEN** the response SHALL be a redirect to `/items`
- **AND** the registration form SHALL NOT be rendered

#### Scenario: Unauthenticated user opens the login page

- **GIVEN** a request carries no `uid` session attribute
- **WHEN** the client sends `GET /login`
- **THEN** the login form SHALL be rendered normally
