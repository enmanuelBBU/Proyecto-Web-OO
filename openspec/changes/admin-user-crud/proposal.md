# Proposal: Admin User CRUD

## Why

The project now has a real `rol` field (`USUARIO`/`ADMIN`) on every profile, correctly protected against client-side tampering (see `complete-auth-perfil-items`) — but nothing in the system can ever read that field to make a decision, and nothing can set it to `ADMIN` at all. Two gaps block the "CRUD Usuario (Admin)" feature entirely:

1. **No way to become an admin.** Self-registration always sets `rol = "USUARIO"`, and self-service save now correctly refuses to let a client change its own `rol`. There is currently no path — automated or manual-in-code — to produce the first `ADMIN` account.
2. **No admin-only surface exists.** There is no authorization check anywhere in the codebase beyond "is there a session" (`ApiAuthInterceptor`, and per-method `HttpSession` checks in web controllers). Nothing distinguishes an `ADMIN` session from a `USUARIO` session.

## What Changes

- **Admin bootstrap**: a new `admin.emails` config property (comma-separated) names accounts that should be `ADMIN`. Registering with a listed email creates the profile as `ADMIN` directly; logging in with a listed email upgrades an existing `USUARIO` profile to `ADMIN` if it isn't already. The session gains a cached `rol` attribute (set at login/register) so authorization checks don't need a Firestore read per request.
- **Admin authorization**: a new `AdminAuthInterceptor` gates `/api/admin/**`, returning `403` unless the session's cached `rol` is `ADMIN`. Web admin routes use the same manual per-method check already used elsewhere in this codebase (no Spring Security introduced).
- **Admin user management**: new REST (`/api/admin/usuarios`) and web (`/admin/usuarios`) surfaces to list all users, view/edit one user's `nombre`/`email`/`rol`, and delete a user (Firestore profile + Firebase Auth account, mirroring the existing self-delete flow). This is the one place in the system where `rol` is legitimately settable by a human, and it is validated against the fixed set `{USUARIO, ADMIN}`.
- **Self-protection guard**: an admin cannot edit or delete their own account through the admin panel (they still have `/perfil` for that) — prevents an admin routing around their own account through a second, differently-behaved path and avoids self-lockout confusion.
- **Nav visibility**: an "Admin" link appears in `items.html`/`perfil.html`'s nav only when the session's cached `rol` is `ADMIN`.

## Non-Goals

- Creating brand-new user accounts from the admin panel (Create) — registration via `/register` remains the only account-creation path; this change is Read + Update + Delete of already-registered users.
- A "last admin" safety check (preventing the only admin from being demoted/deleted by another admin) — out of scope for this project's size; the self-protection guard (can't touch your own account) is the only lockout mitigation.
- Any change to `ApiAuthInterceptor`, the existing self-service perfil/items endpoints, or Spring Security adoption.

## Rollback Plan

Additive only: one new Firestore-collection consumer (`list()` reads the existing `users` collection, no new collection), one new config property (defaults to empty — no admin emails configured means the feature is inert, no existing account is affected), two new controllers, one new interceptor, one new field on `UserProfile` (`uid`, not persisted as a required field). Rollback is a plain `git revert`; no data migration in either direction.

## Impact

- Code: new `admin` package (`AdminUserController`, `WebAdminUserController`), `config/AdminEmails`, `config/AdminAuthInterceptor`; modifications to `WebAuthController` (bootstrap + session `rol`), `UserProfileService`/`UserProfile` (uid field, `list()`), `CorsConfig` (register new interceptor), `WebItemController`/`WebProfileController` (expose `isAdmin` to the nav), `items.html`/`perfil.html` (conditional nav link).
- Tests: new interceptor tests, bootstrap tests, admin CRUD tests, self-protection tests.
- No new dependencies.
