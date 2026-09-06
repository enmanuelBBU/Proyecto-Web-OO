# Spec Delta: Items (CRUD + Search)

## MODIFIED Requirements

### Requirement: Item data model SHALL represent a catalog entry, not per-user stock

An `Item` SHALL carry `id`, `nombre` (required), `categoria` (optional
free text), and `esMateriaPrima` (boolean, default `false`). It SHALL NOT
carry a quantity/stock field or an unused icon field.

#### Scenario: Creating an item without a categoria

- **WHEN** a client creates an item with only `nombre` set
- **THEN** the created item SHALL have `categoria = null` and
  `esMateriaPrima = false`

#### Scenario: Creating an item without a nombre is rejected

- **WHEN** a client sends `POST /api/items` with a blank or missing `nombre`
- **THEN** the response status SHALL be 400
- **AND** no document SHALL be created

### Requirement: Updating or deleting a non-existent item SHALL report not-found

`PUT /api/items/{id}` and `DELETE /api/items/{id}` MUST NOT silently create
or no-op on an id that does not exist in the `items` collection; they SHALL
respond with 404.

#### Scenario: Updating an unknown id

- **GIVEN** no item exists with id `"missing-1"`
- **WHEN** the client sends `PUT /api/items/missing-1` with a valid body
- **THEN** the response status SHALL be 404
- **AND** no document SHALL be created at that id

#### Scenario: Deleting an unknown id

- **GIVEN** no item exists with id `"missing-1"`
- **WHEN** the client sends `DELETE /api/items/missing-1`
- **THEN** the response status SHALL be 404

### Requirement: Search SHALL match name or category, with an optional materials filter

`GET /api/items` SHALL accept an optional `q` parameter matched
case-insensitively as a substring of `nombre` OR `categoria`, and an optional
`esMateriaPrima` boolean parameter that, when present, restricts results to
items whose `esMateriaPrima` equals the given value.

#### Scenario: Searching by category text

- **GIVEN** items exist with `categoria` values `"Mineral"` and `"Herramienta"`
- **WHEN** the client sends `GET /api/items?q=miner`
- **THEN** only items whose `nombre` or `categoria` contains `"miner"`
  (case-insensitive) SHALL be returned

#### Scenario: Filtering to raw materials only

- **GIVEN** items exist with `esMateriaPrima = true` and `esMateriaPrima = false`
- **WHEN** the client sends `GET /api/items?esMateriaPrima=true`
- **THEN** only items with `esMateriaPrima = true` SHALL be returned

## ADDED Requirements

### Requirement: The web item forms SHALL surface validation failures

The `/items` create form and the `/items/{id}/edit` update form MUST
re-render with an actionable error message when submission fails validation,
instead of silently discarding the failure.

#### Scenario: Submitting a blank name on the web create form

- **WHEN** an authenticated user submits `POST /items` with a blank `nombre`
- **THEN** the response SHALL re-render the items page with an `error`
  message
- **AND** no item SHALL be created
