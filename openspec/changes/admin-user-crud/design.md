# Design: Admin User CRUD

## Architecture

No new architectural pattern: the existing session-attribute + `HandlerInterceptor` gating style (established in `complete-auth-perfil-items`) is extended, not replaced. One new package (`cl.grupo5.proyectominecraft.admin`) holds the admin-only controllers; everything else is a small, targeted change to existing files.

## Data Model

### `UserProfile` (add one field)

| Field | Type | Notes |
|---|---|---|
| `uid` | String | new; not `@NotBlank`, not required at construction — populated by `UserProfileService` on read, mirrors how `Item.id` works today (identity lives in the Firestore document id, this field just carries it back to callers that need to act on a specific user, like the admin list view) |

No other `UserProfile` field changes.

### Session attributes (add one)

| Attribute | Type | Set by | Notes |
|---|---|---|---|
| `rol` | String | `WebAuthController.doLogin`/`doRegister` | Cached copy of the profile's `rol` at login time, so per-request authorization doesn't need a Firestore read. Goes stale if an admin changes another user's role while that user has an active session — accepted for this project's scope (matches the existing pattern where `uid`/`email` are also session-cached, not re-verified per request). |

## Component Changes

### `config` package (new + modified)

- **New `AdminEmails`**: a `@Component` reading `@Value("${admin.emails:}") String raw`, exposing `boolean isAdmin(String email)` — splits `raw` on commas, trims, lowercases, and does a case-insensitive membership check. Empty property → always `false` (feature is inert until configured).
- **New `AdminAuthInterceptor`**: implements `HandlerInterceptor`, mirrors `ApiAuthInterceptor`'s shape. `preHandle` checks `session.getAttribute("rol")` equals `"ADMIN"`; if not, `response.sendError(403)` and returns `false`.
- **`CorsConfig`**: registers `AdminAuthInterceptor` with `addPathPatterns("/api/admin/**")`, added *after* the existing `apiAuthInterceptor.addPathPatterns("/api/**")` registration so both interceptors run on an `/api/admin/**` request, in this order: session-exists check, then admin-role check. A request with no session gets `401` from the first interceptor before the second ever runs.

### `auth` package (modified)

- **`WebAuthController`**: gains a constructor dependency on `AdminEmails`.
  - `doRegister`: after building the new `UserProfile`, `if (adminEmails.isAdmin(email)) p.setRol("ADMIN");` before `profiles.save(uid, p)`. Session gains `s.setAttribute("rol", p.getRol());`.
  - `doLogin`: after a successful sign-in, `profiles.get(uid)` to fetch the profile. If it exists, is not already `ADMIN`, and `adminEmails.isAdmin(email)` is true, set `rol = "ADMIN"` and `profiles.save(uid, profile)`. Either way, `s.setAttribute("rol", profile.getRol())` (using the resolved profile's `rol`, post-promotion if it happened). If no profile exists yet (shouldn't normally happen — registration always creates one — but defensive), fall back to `"USUARIO"` for the session attribute without creating a profile; the existing `WebProfileController.show()` already handles a missing profile by synthesizing a blank one on first visit to `/perfil`.

### `perfil` package (modified)

- **`UserProfileService`**:
  - `get(uid)`: after `snap.toObject(UserProfile.class)`, additionally `p.setUid(uid)` before returning (currently it doesn't set this — `UserProfile` had no `uid` field before this change).
  - New `list()`: `db.collection("users").get().get().getDocuments()` mapped the same way as `ItemService.list` — `toObject(UserProfile.class)` then `p.setUid(doc.getId())`.

### `admin` package (new)

- **`AdminUserController`** (`@RestController`, `/api/admin/usuarios`):
  - `GET` → `service.list()`.
  - `GET /{uid}` → `service.get(uid)`, `404` if missing.
  - `PUT /{uid}` → validates `rol` is one of `{"USUARIO", "ADMIN"}` (`400` otherwise); rejects if `uid` equals the caller's own session uid (`400`, "no puedes editar tu propia cuenta desde el panel admin" — self-protection guard); otherwise loads the target, applies `nombre`/`email`/`rol`, saves.
  - `DELETE /{uid}` → rejects self-targeting the same way; otherwise deletes the Firestore profile and the Firebase Auth user (same try/finally-free flow as this is a different account than the caller's own — no session to invalidate for someone else). Order: delete Firebase Auth first, then Firestore (same rationale as the self-delete flow in `complete-auth-perfil-items`'s design: a partial failure should leave an unreachable orphaned Firestore doc, not a live account with no profile).
- **`WebAdminUserController`** (`@Controller`, `/admin/usuarios`):
  - `GET /admin/usuarios`: session must have `uid` and `rol == "ADMIN"` (redirect to `/items` with an error otherwise — reuses the same rendering pattern as other web controllers' auth checks); lists all users.
  - `GET /admin/usuarios/{uid}/edit`: same guard; loads the target user; if `uid` equals the caller's own, redirect to `/admin/usuarios` with an error instead of rendering the edit form.
  - `POST /admin/usuarios/{uid}/update`: same guards (admin session + not self); validates `rol` against the fixed set; saves.
  - `POST /admin/usuarios/{uid}/delete`: same guards; deletes.

### Nav visibility (`items.html`, `perfil.html`, and their controllers)

- `WebItemController.list()` and `WebProfileController.show()` each add `m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));`.
- Both templates' `<nav>` gain `<a th:if="${isAdmin}" href="/admin/usuarios">Admin</a>`.

## Sequence: Admin Bootstrap and Promotion

```
Constructor            WebAuthController          AdminEmails         UserProfileService      HttpSession
    |  POST /login (email in admin.emails)  |                            |                       |
    |---------------------------------------->|                            |                       |
    |                                        | identity.signIn(...)      |                       |
    |                                        | profiles.get(uid)  ------------------------------->|
    |                                        |<------------------------- (existing profile, rol=USUARIO)
    |                                        | adminEmails.isAdmin(email) -->| true                 |
    |                                        | profile.setRol("ADMIN")   |                       |
    |                                        | profiles.save(uid, profile) ---------------------->|
    |                                        | session.setAttribute("rol", "ADMIN")               |
    |  redirect: /items                      |                            |                       |
    |<----------------------------------------|                            |                       |
```

A first-time registration with a listed email skips the "existing profile" branch entirely: the profile is created with `rol = "ADMIN"` from the start.

## Sequence: Admin Deletes Another User

```
Admin                AdminUserController        AuthService/FirebaseAuth     UserProfileService
    |  DELETE /api/admin/usuarios/{target-uid}  |                              |
    |------------------------------------------->|                              |
    |                                            | target-uid == session uid? -> reject (400) if so
    |                                            | auth.deleteUser(target-uid) |
    |                                            |  (Auth account gone)        |
    |                                            |----------------------------->| delete(target-uid)
    |                                            |                              |  (Firestore doc gone)
    |  204 No Content                            |                              |
    |<--------------------------------------------|                              |
```

No session to invalidate here — the target user's session (if any) simply stops working the next time they make a request that needs their `uid`/profile, since their Firebase Auth account is gone (they'll fail to re-authenticate, and any in-flight session dies naturally when the interceptor's presumption of a valid uid is no longer backed by a real account — no special handling added for this, consistent with this project's existing no-token-revocation-list scope).

## Error Handling

- `AdminAuthInterceptor`: `403` with no body (matches `ApiAuthInterceptor`'s existing `401`-with-no-body style) for a non-admin session hitting `/api/admin/**`.
- Web admin routes: redirect to `/items` (not logged in, or logged in but not admin) or to `/admin/usuarios` (self-targeting attempt), each carrying a Spanish `error` flash message consistent with `DESIGN-SYSTEM.md` §8.
- Invalid `rol` value on update: `400` (REST) / re-render the edit form with an error (web) — never silently coerced or ignored.

## Testing

Extend the established `@WebMvcTest` + `MockitoBean` pattern:
- `AdminAuthInterceptor`: `403` for a session with `rol=USUARIO` (or no `rol` attribute at all) hitting an `/api/admin/**` route; `200`-reachable (controller invoked) for `rol=ADMIN`.
- `WebAuthController`: registering with a listed email creates an `ADMIN` profile; logging in with a listed email promotes an existing `USUARIO` profile; logging in with a non-listed email leaves `rol` untouched.
- `AdminUserController`: list, get, update (including the `400` on an invalid `rol` value and on self-targeting), delete (including self-targeting rejection), each gated by the interceptor tests above (no need to re-prove `403` per endpoint — one interceptor test suffices for the gating contract; endpoint tests assume an authorized admin session and focus on the endpoint's own logic).

No new test framework or dependency; `gradlew.bat test` remains the verify command.
