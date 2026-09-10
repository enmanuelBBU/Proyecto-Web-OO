# Admin User CRUD Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let a configured list of admin emails bootstrap `ADMIN` accounts, gate a new admin-only surface behind that role, and give admins the ability to list, edit, and delete other users' accounts (Firestore profile + Firebase Auth), without ever letting a user touch their own account through that surface.

**Architecture:** Same session-attribute + `HandlerInterceptor` gating style already used by `ApiAuthInterceptor`/web controllers — one new interceptor (`AdminAuthInterceptor`), one new package (`cl.grupo5.proyectominecraft.admin`) with a REST and a web controller, small additions to existing auth/perfil/items/templates files. No Spring Security.

**Tech Stack:** Java 17, Spring Boot 4.1.1 (webmvc, validation, thymeleaf), Lombok, Firebase Admin SDK 9.4.0 (Firestore + Auth), JUnit 5 + Mockito + AssertJ, Gradle wrapper (`gradlew.bat`).

**Spec:** `openspec/changes/admin-user-crud/{proposal,design}.md` and `specs/{auth,perfil,admin}/spec.md` — read these alongside this plan; this plan implements exactly their requirements/scenarios.

## Global Constraints

- No new dependencies, packages, or beans beyond what each task states.
- Keep the existing session-attribute auth pattern — no Spring Security.
- All new user-facing error text is in Spanish and actionable, per `DESIGN-SYSTEM.md` §8.
- **Do NOT run `git commit`.** Stage or leave changes as asked by whoever is running this plan; do not assume permission to commit.
- Test pattern: `@WebMvcTest` slices with `@MockitoBean` for collaborators, following the existing style (`@Import({ApiAuthInterceptor.class, CorsConfig.class})` — extended here with `AdminAuthInterceptor.class` — for anything under `/api/**`).
- Verify command: `gradlew.bat test`. Build command: `gradlew.bat build`.

---

### Task 1: `UserProfile.uid` + `UserProfileService` read/list changes

**Files:**
- Modify: `src/main/java/cl/grupo5/proyectominecraft/perfil/UserProfile.java`
- Modify: `src/main/java/cl/grupo5/proyectominecraft/perfil/UserProfileService.java`

**Interfaces:**
- Produces: `UserProfile.uid: String` (getter/setter via Lombok `@Data`, not `@NotBlank` — mirrors `Item.id`).
- Produces: `UserProfileService.list() throws Exception -> List<UserProfile>` (each with `uid` populated) — consumed by `AdminUserController` and `WebAdminUserController` (Tasks 5-6).
- Produces: `UserProfileService.get(String)` now populates `uid` on the returned object (previously did not) — consumed by Tasks 4-6.

There is no automated test for this task's `get`/`list` changes: setting a field from a Firestore document id has no precedent for unit-testing without a live/mocked `Firestore` in this codebase (the equivalent gap exists for `ItemService.update`/`delete`'s existence checks in the prior plan). Unlike that prior case, this one-line addition (`p.setUid(uid)` / `p.setUid(d.getId())`) is **not** exercised even indirectly by Tasks 5-6's tests, since those mock `UserProfileService` entirely and construct `UserProfile` objects with `uid` already set by hand — the real Firestore-backed implementation only gets manual code review. This is a known, accepted gap consistent with this codebase's practice; flag it if a future change adds a Firestore test-double.

- [ ] **Step 1: Add the `uid` field**

Replace `UserProfile.java` entirely with:

```java
package cl.grupo5.proyectominecraft.perfil;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class UserProfile {
  private String uid;
  @NotBlank
  private String nombre;
  @Email
  @NotBlank
  private String email;
  private String rol = "USUARIO";
}
```

- [ ] **Step 2: Populate `uid` on read, and add `list()`**

Replace `UserProfileService.java` entirely with:

```java
package cl.grupo5.proyectominecraft.perfil;

import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserProfileService {
  private final Firestore db;
  public UserProfileService(Firestore db) { this.db = db; }

  public UserProfile get(String uid) throws Exception {
    var snap = db.collection("users").document(uid).get().get();
    if (!snap.exists()) return null;
    var p = snap.toObject(UserProfile.class);
    p.setUid(uid);
    return p;
  }

  public List<UserProfile> list() throws Exception {
    var docs = db.collection("users").get().get().getDocuments();
    return docs.stream().map(d -> {
      var p = d.toObject(UserProfile.class);
      p.setUid(d.getId());
      return p;
    }).toList();
  }

  public UserProfile save(String uid, UserProfile p) throws Exception {
    db.collection("users").document(uid).set(p).get();
    return p;
  }

  public void delete(String uid) throws Exception {
    db.collection("users").document(uid).delete().get();
  }
}
```

- [ ] **Step 3: Compile-check**

Run: `gradlew.bat compileJava`
Expected: BUILD SUCCESSFUL (this task only adds a field and a method; nothing yet calls `list()`, so no behavioral test applies until Task 5/6).

---

### Task 2: `AdminEmails` bootstrap-list bean

**Files:**
- Create: `src/main/java/cl/grupo5/proyectominecraft/config/AdminEmails.java`
- Modify: `src/main/resources/application.properties`
- Create: `src/test/java/cl/grupo5/proyectominecraft/config/AdminEmailsTest.java`

**Interfaces:**
- Produces: `AdminEmails.isAdmin(String email) -> boolean` — consumed by `WebAuthController` (Task 4).

- [ ] **Step 1: Write the failing test**

```java
package cl.grupo5.proyectominecraft.config;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class AdminEmailsTest {

  @Test
  void isAdminTrueForListedEmailCaseInsensitive() {
    var emails = new AdminEmails("root@example.com, other@example.com");
    assertThat(emails.isAdmin("ROOT@example.com")).isTrue();
  }

  @Test
  void isAdminFalseForUnlistedEmail() {
    var emails = new AdminEmails("root@example.com");
    assertThat(emails.isAdmin("someone@example.com")).isFalse();
  }

  @Test
  void isAdminFalseWhenPropertyIsEmpty() {
    var emails = new AdminEmails("");
    assertThat(emails.isAdmin("root@example.com")).isFalse();
  }

  @Test
  void isAdminFalseForNullEmail() {
    var emails = new AdminEmails("root@example.com");
    assertThat(emails.isAdmin(null)).isFalse();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.config.AdminEmailsTest"`
Expected: FAIL to compile — `AdminEmails` doesn't exist yet.

- [ ] **Step 3: Implement**

Create `AdminEmails.java`:

```java
package cl.grupo5.proyectominecraft.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import java.util.Arrays;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class AdminEmails {
  private final Set<String> emails;

  public AdminEmails(@Value("${admin.emails:}") String raw) {
    this.emails = Arrays.stream(raw.split(","))
        .map(String::trim)
        .filter(s -> !s.isBlank())
        .map(String::toLowerCase)
        .collect(Collectors.toSet());
  }

  public boolean isAdmin(String email) {
    return email != null && emails.contains(email.trim().toLowerCase());
  }
}
```

Add a new line to `application.properties` (after the existing `firebase.web-api-key` line):

```properties
admin.emails=
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.config.AdminEmailsTest"`
Expected: PASS (4 tests).

---

### Task 3: `AdminAuthInterceptor` + registration

**Files:**
- Create: `src/main/java/cl/grupo5/proyectominecraft/config/AdminAuthInterceptor.java`
- Modify: `src/main/java/cl/grupo5/proyectominecraft/config/CorsConfig.java`
- Create: `src/test/java/cl/grupo5/proyectominecraft/config/AdminAuthInterceptorTest.java`
- Modify: `src/test/java/cl/grupo5/proyectominecraft/items/ItemApiAuthTest.java` (import fix, see Step 3b)
- Modify: `src/test/java/cl/grupo5/proyectominecraft/items/ItemControllerTest.java` (import fix, see Step 3b)
- Modify: `src/test/java/cl/grupo5/proyectominecraft/perfil/UserProfileControllerTest.java` (import fix, see Step 3b)

**Cross-task note:** `CorsConfig`'s constructor gains a required `AdminAuthInterceptor` parameter in this task. Three existing test classes instantiate `CorsConfig` via `@Import({ApiAuthInterceptor.class, CorsConfig.class})` (no `AdminAuthInterceptor` in that list) — after this task's change, their Spring context would fail to load with a missing-bean error unless their `@Import` list also names `AdminAuthInterceptor.class`. Step 3b below is a one-line fix in each of the three files; it must land in the same task as the `CorsConfig` change, not later, or the existing suite goes red.

**Interfaces:**
- Produces: `AdminAuthInterceptor` (a `HandlerInterceptor`) registered on `/api/admin/**` — consumed structurally by any controller mapped under that path (Task 5's `AdminUserController`).

- [ ] **Step 1: Write the failing test**

```java
package cl.grupo5.proyectominecraft.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

class AdminAuthInterceptorTest {

  private final AdminAuthInterceptor interceptor = new AdminAuthInterceptor();

  @Test
  void rejectsRequestWithNoSession() throws Exception {
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    when(request.getSession(false)).thenReturn(null);

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
  }

  @Test
  void rejectsSessionWithNonAdminRole() throws Exception {
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    var session = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute("rol")).thenReturn("USUARIO");

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isFalse();
    verify(response).sendError(HttpServletResponse.SC_FORBIDDEN);
  }

  @Test
  void allowsSessionWithAdminRole() throws Exception {
    var request = mock(HttpServletRequest.class);
    var response = mock(HttpServletResponse.class);
    var session = mock(HttpSession.class);
    when(request.getSession(false)).thenReturn(session);
    when(session.getAttribute("rol")).thenReturn("ADMIN");

    boolean result = interceptor.preHandle(request, response, new Object());

    assertThat(result).isTrue();
    verify(response, never()).sendError(anyInt());
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.config.AdminAuthInterceptorTest"`
Expected: FAIL to compile — `AdminAuthInterceptor` doesn't exist yet.

- [ ] **Step 3: Implement**

Create `AdminAuthInterceptor.java`:

```java
package cl.grupo5.proyectominecraft.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminAuthInterceptor implements HandlerInterceptor {
  @Override
  public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
    var session = request.getSession(false);
    var rol = session == null ? null : session.getAttribute("rol");
    if (!"ADMIN".equals(rol)) {
      response.sendError(HttpServletResponse.SC_FORBIDDEN);
      return false;
    }
    return true;
  }
}
```

Replace `CorsConfig.java` entirely with:

```java
package cl.grupo5.proyectominecraft.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig {
  private final ApiAuthInterceptor apiAuthInterceptor;
  private final AdminAuthInterceptor adminAuthInterceptor;

  public CorsConfig(ApiAuthInterceptor apiAuthInterceptor, AdminAuthInterceptor adminAuthInterceptor) {
    this.apiAuthInterceptor = apiAuthInterceptor;
    this.adminAuthInterceptor = adminAuthInterceptor;
  }

  @Bean
  public WebMvcConfigurer cors() {
    return new WebMvcConfigurer() {
      @Override
      public void addCorsMappings(CorsRegistry r) {
        r.addMapping("/api/**").allowedOrigins("http://localhost:5173").allowedMethods("GET", "POST", "PUT", "DELETE");
      }

      @Override
      public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(apiAuthInterceptor).addPathPatterns("/api/**").excludePathPatterns("/api/auth/**");
        registry.addInterceptor(adminAuthInterceptor).addPathPatterns("/api/admin/**");
      }
    };
  }
}
```

- [ ] **Step 3b: Fix the three existing tests that `@Import(CorsConfig.class)`**

In each of `ItemApiAuthTest.java`, `ItemControllerTest.java`, and `UserProfileControllerTest.java`, change:

```java
@Import({ApiAuthInterceptor.class, CorsConfig.class})
```

to:

```java
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
```

and add the import `cl.grupo5.proyectominecraft.config.AdminAuthInterceptor` to each file (it's already in the `config` package alongside `ApiAuthInterceptor`, which each file already imports — add the sibling import next to it).

- [ ] **Step 4: Run the full test suite to verify nothing broke**

Run: `gradlew.bat test`
Expected: PASS — every existing test class still loads its Spring context correctly, plus the 7 new tests (4 from `AdminEmailsTest`, 3 from `AdminAuthInterceptorTest`).

---

### Task 4: `WebAuthController` admin bootstrap

**Files:**
- Modify: `src/main/java/cl/grupo5/proyectominecraft/auth/WebAuthController.java`
- Modify: `src/test/java/cl/grupo5/proyectominecraft/auth/WebAuthControllerTest.java`

**Interfaces:**
- Consumes: `AdminEmails.isAdmin(String)` (Task 2).
- Produces: session attribute `rol` set at the end of both `doLogin` and `doRegister` — consumed by `AdminAuthInterceptor` (Task 3, structurally) and by Task 7's nav-visibility checks.

- [ ] **Step 1: Write the failing tests**

Replace `WebAuthControllerTest.java` entirely with:

```java
package cl.grupo5.proyectominecraft.auth;

import cl.grupo5.proyectominecraft.config.AdminEmails;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebAuthController.class)
class WebAuthControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private FirebaseIdentityService identity;

  @MockitoBean
  private UserProfileService profiles;

  @MockitoBean
  private AdminEmails adminEmails;

  @Test
  void loginRendersFormWithoutSession() throws Exception {
    mvc.perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(view().name("login"));
  }

  @Test
  void loginRedirectsToItemsWhenAlreadyAuthenticated() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "uid-1");

    mvc.perform(get("/login").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));
  }

  @Test
  void registerRedirectsToItemsWhenAlreadyAuthenticated() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "uid-1");

    mvc.perform(get("/register").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));
  }

  @Test
  void registerWithAdminEmailCreatesAdminProfile() throws Exception {
    when(identity.signUp("root@example.com", "secret1")).thenReturn(Map.of("localId", "uid-9"));
    when(adminEmails.isAdmin("root@example.com")).thenReturn(true);

    mvc.perform(post("/register").param("email", "root@example.com").param("password", "secret1"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));

    var captor = org.mockito.ArgumentCaptor.forClass(UserProfile.class);
    verify(profiles).save(eq("uid-9"), captor.capture());
    assertThat(captor.getValue().getRol()).isEqualTo("ADMIN");
  }

  @Test
  void loginWithAdminEmailPromotesExistingProfileToAdmin() throws Exception {
    when(identity.signIn("root@example.com", "secret1")).thenReturn(Map.of("localId", "uid-9"));
    var existing = new UserProfile();
    existing.setNombre("root");
    existing.setEmail("root@example.com");
    existing.setRol("USUARIO");
    when(profiles.get("uid-9")).thenReturn(existing);
    when(adminEmails.isAdmin("root@example.com")).thenReturn(true);

    var session = new MockHttpSession();
    mvc.perform(post("/login").session(session).param("email", "root@example.com").param("password", "secret1"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));

    var captor = org.mockito.ArgumentCaptor.forClass(UserProfile.class);
    verify(profiles).save(eq("uid-9"), captor.capture());
    assertThat(captor.getValue().getRol()).isEqualTo("ADMIN");
    assertThat(session.getAttribute("rol")).isEqualTo("ADMIN");
  }

  @Test
  void loginWithNonAdminEmailLeavesRoleUnchanged() throws Exception {
    when(identity.signIn("user@example.com", "secret1")).thenReturn(Map.of("localId", "uid-5"));
    var existing = new UserProfile();
    existing.setNombre("user");
    existing.setEmail("user@example.com");
    existing.setRol("USUARIO");
    when(profiles.get("uid-5")).thenReturn(existing);
    when(adminEmails.isAdmin("user@example.com")).thenReturn(false);

    var session = new MockHttpSession();
    mvc.perform(post("/login").session(session).param("email", "user@example.com").param("password", "secret1"))
        .andExpect(status().is3xxRedirection());

    verify(profiles, never()).save(any(), any());
    assertThat(session.getAttribute("rol")).isEqualTo("USUARIO");
  }
}
```

- [ ] **Step 2: Run tests to verify the new ones fail**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.auth.WebAuthControllerTest"`
Expected: FAIL to compile (constructor signature changed) — then, once it compiles against the old controller, the three new tests fail because no promotion logic exists yet.

- [ ] **Step 3: Implement**

Replace `WebAuthController.java` entirely with:

```java
package cl.grupo5.proyectominecraft.auth;

import cl.grupo5.proyectominecraft.config.AdminEmails;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;

@Controller
public class WebAuthController {
  private final FirebaseIdentityService identity;
  private final UserProfileService profiles;
  private final AdminEmails adminEmails;

  public WebAuthController(FirebaseIdentityService identity, UserProfileService profiles, AdminEmails adminEmails) {
    this.identity = identity;
    this.profiles = profiles;
    this.adminEmails = adminEmails;
  }

  @GetMapping("/login")
  public String login(HttpSession s) {
    if (s.getAttribute("uid") != null) return "redirect:/items";
    return "login";
  }

  @PostMapping("/login")
  public String doLogin(@RequestParam String email, @RequestParam String password, HttpSession s, Model m) {
    try {
      var r = identity.signIn(email, password);
      String uid = String.valueOf(r.get("localId"));
      var profile = resolveRoleOnLogin(uid, email);
      s.setAttribute("uid", uid);
      s.setAttribute("email", email);
      s.setAttribute("rol", profile != null ? profile.getRol() : "USUARIO");
      return "redirect:/items";
    } catch (Exception e) {
      m.addAttribute("error", "Login: " + causa(e));
      return "login";
    }
  }

  private UserProfile resolveRoleOnLogin(String uid, String email) throws Exception {
    var profile = profiles.get(uid);
    if (profile != null && adminEmails.isAdmin(email) && !"ADMIN".equals(profile.getRol())) {
      profile.setRol("ADMIN");
      profiles.save(uid, profile);
    }
    return profile;
  }

  @GetMapping("/register")
  public String register(HttpSession s) {
    if (s.getAttribute("uid") != null) return "redirect:/items";
    return "register";
  }

  @PostMapping("/register")
  public String doRegister(@RequestParam String email, @RequestParam String password, HttpSession s, Model m) {
    try {
      var r = identity.signUp(email, password);
      String uid = String.valueOf(r.get("localId"));
      var p = new UserProfile();
      p.setNombre(email.split("@")[0]);
      p.setEmail(email);
      if (adminEmails.isAdmin(email)) p.setRol("ADMIN");
      profiles.save(uid, p);
      s.setAttribute("uid", uid);
      s.setAttribute("email", email);
      s.setAttribute("rol", p.getRol());
      return "redirect:/items";
    } catch (Exception e) {
      m.addAttribute("error", "Registro: " + causa(e));
      return "register";
    }
  }

  private String causa(Exception e) {
    if (e instanceof HttpClientErrorException h) return h.getResponseBodyAsString();
    return String.valueOf(e.getMessage());
  }

  @PostMapping("/logout")
  public String logout(HttpSession s) {
    s.invalidate();
    return "redirect:/login";
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.auth.WebAuthControllerTest"`
Expected: PASS (6 tests).

---

### Task 5: `AdminUserController` (REST)

**Files:**
- Create: `src/main/java/cl/grupo5/proyectominecraft/admin/AdminUserController.java`
- Create: `src/test/java/cl/grupo5/proyectominecraft/admin/AdminUserControllerTest.java`

**Interfaces:**
- Consumes: `UserProfileService.list/get/save/delete` (existing + Task 1), `AuthService.deleteUser` (existing).
- Produces: `GET/PUT/DELETE /api/admin/usuarios[/{uid}]` — no other task depends on this REST surface's Java signatures (only its HTTP contract, used by Task 6's web controller only insofar as they share the same service layer, not this controller).

- [ ] **Step 1: Write the failing tests**

```java
package cl.grupo5.proyectominecraft.admin;

import cl.grupo5.proyectominecraft.auth.AuthService;
import cl.grupo5.proyectominecraft.config.AdminAuthInterceptor;
import cl.grupo5.proyectominecraft.config.ApiAuthInterceptor;
import cl.grupo5.proyectominecraft.config.CorsConfig;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminUserController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class AdminUserControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private UserProfileService profileService;

  @MockitoBean
  private AuthService authService;

  private MockHttpSession adminSession() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "admin-1");
    session.setAttribute("rol", "ADMIN");
    return session;
  }

  private MockHttpSession userSession() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "user-1");
    session.setAttribute("rol", "USUARIO");
    return session;
  }

  @Test
  void listWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(get("/api/admin/usuarios")).andExpect(status().isUnauthorized());
  }

  @Test
  void listWithNonAdminSessionIsForbidden() throws Exception {
    mvc.perform(get("/api/admin/usuarios").session(userSession())).andExpect(status().isForbidden());
  }

  @Test
  void listWithAdminSessionSucceeds() throws Exception {
    when(profileService.list()).thenReturn(List.of());
    mvc.perform(get("/api/admin/usuarios").session(adminSession())).andExpect(status().isOk());
  }

  @Test
  void getReturnsRequestedUserProfile() throws Exception {
    var target = new UserProfile();
    target.setUid("user-2");
    target.setNombre("Ana");
    target.setEmail("ana@x.com");
    target.setRol("USUARIO");
    when(profileService.get("user-2")).thenReturn(target);

    mvc.perform(get("/api/admin/usuarios/user-2").session(adminSession()))
        .andExpect(status().isOk());
  }

  @Test
  void updateRejectsInvalidRole() throws Exception {
    var target = new UserProfile();
    target.setUid("user-2");
    target.setNombre("Ana");
    target.setEmail("ana@x.com");
    target.setRol("USUARIO");
    when(profileService.get("user-2")).thenReturn(target);

    mvc.perform(put("/api/admin/usuarios/user-2")
            .session(adminSession())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Ana\",\"email\":\"ana@x.com\",\"rol\":\"SUPERADMIN\"}"))
        .andExpect(status().isBadRequest());

    verify(profileService, never()).save(any(), any());
  }

  @Test
  void updateChangesTargetRole() throws Exception {
    var target = new UserProfile();
    target.setUid("user-2");
    target.setNombre("Ana");
    target.setEmail("ana@x.com");
    target.setRol("USUARIO");
    when(profileService.get("user-2")).thenReturn(target);
    when(profileService.save(eq("user-2"), any())).thenAnswer(inv -> inv.getArgument(1));

    mvc.perform(put("/api/admin/usuarios/user-2")
            .session(adminSession())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Ana\",\"email\":\"ana@x.com\",\"rol\":\"ADMIN\"}"))
        .andExpect(status().isOk());

    var captor = org.mockito.ArgumentCaptor.forClass(UserProfile.class);
    verify(profileService).save(eq("user-2"), captor.capture());
    assertThat(captor.getValue().getRol()).isEqualTo("ADMIN");
  }

  @Test
  void updateRejectsSelfTargeting() throws Exception {
    mvc.perform(put("/api/admin/usuarios/admin-1")
            .session(adminSession())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Admin\",\"email\":\"admin@x.com\",\"rol\":\"ADMIN\"}"))
        .andExpect(status().isBadRequest());

    verify(profileService, never()).save(any(), any());
  }

  @Test
  void deleteRejectsSelfTargeting() throws Exception {
    mvc.perform(delete("/api/admin/usuarios/admin-1").session(adminSession()))
        .andExpect(status().isBadRequest());

    verify(authService, never()).deleteUser(any());
    verify(profileService, never()).delete(any());
  }

  @Test
  void deleteRemovesTargetAuthAndProfileInSafeOrder() throws Exception {
    mvc.perform(delete("/api/admin/usuarios/user-2").session(adminSession()))
        .andExpect(status().isNoContent());

    var order = inOrder(authService, profileService);
    order.verify(authService).deleteUser("user-2");
    order.verify(profileService).delete("user-2");
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.admin.AdminUserControllerTest"`
Expected: FAIL to compile — `AdminUserController` doesn't exist yet.

- [ ] **Step 3: Implement**

Create `AdminUserController.java`:

```java
package cl.grupo5.proyectominecraft.admin;

import cl.grupo5.proyectominecraft.auth.AuthService;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@RestController
@RequestMapping("/api/admin/usuarios")
public class AdminUserController {
  private static final Set<String> ROLES_VALIDOS = Set.of("USUARIO", "ADMIN");

  private final UserProfileService service;
  private final AuthService auth;

  public AdminUserController(UserProfileService service, AuthService auth) {
    this.service = service;
    this.auth = auth;
  }

  @GetMapping
  public ResponseEntity<?> list() throws Exception {
    return ResponseEntity.ok(service.list());
  }

  @GetMapping("/{uid}")
  public ResponseEntity<?> get(@PathVariable String uid) throws Exception {
    var p = service.get(uid);
    return p == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(p);
  }

  @PutMapping("/{uid}")
  public ResponseEntity<?> update(@PathVariable String uid, @Valid @RequestBody UserProfile body, HttpSession session) throws Exception {
    if (uid.equals(self(session))) {
      return ResponseEntity.badRequest().body("No puedes editar tu propia cuenta desde el panel admin.");
    }
    if (!ROLES_VALIDOS.contains(body.getRol())) {
      return ResponseEntity.badRequest().body("Rol inválido.");
    }
    var target = service.get(uid);
    if (target == null) return ResponseEntity.notFound().build();
    target.setNombre(body.getNombre());
    target.setEmail(body.getEmail());
    target.setRol(body.getRol());
    return ResponseEntity.ok(service.save(uid, target));
  }

  @DeleteMapping("/{uid}")
  public ResponseEntity<?> delete(@PathVariable String uid, HttpSession session) throws Exception {
    if (uid.equals(self(session))) {
      return ResponseEntity.badRequest().body("No puedes eliminar tu propia cuenta desde el panel admin.");
    }
    auth.deleteUser(uid);
    service.delete(uid);
    return ResponseEntity.noContent().build();
  }

  private String self(HttpSession session) {
    return (String) session.getAttribute("uid");
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.admin.AdminUserControllerTest"`
Expected: PASS (9 tests).

---

### Task 6: `WebAdminUserController` + templates

**Files:**
- Create: `src/main/java/cl/grupo5/proyectominecraft/admin/WebAdminUserController.java`
- Create: `src/main/resources/templates/admin-usuarios.html`
- Create: `src/main/resources/templates/admin-usuario-edit.html`
- Create: `src/test/java/cl/grupo5/proyectominecraft/admin/WebAdminUserControllerTest.java`

**Interfaces:**
- Consumes: `UserProfileService.list/get/save/delete`, `AuthService.deleteUser` (same as Task 5, independent controller).
- Produces: `/admin/usuarios`, `/admin/usuarios/{uid}/edit`, `/admin/usuarios/{uid}/update`, `/admin/usuarios/{uid}/delete` — consumed by Task 7's nav link (`href="/admin/usuarios"`) only as a URL string, no Java interface.

- [ ] **Step 1: Write the failing tests**

```java
package cl.grupo5.proyectominecraft.admin;

import cl.grupo5.proyectominecraft.auth.AuthService;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebAdminUserController.class)
class WebAdminUserControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private UserProfileService profileService;

  @MockitoBean
  private AuthService authService;

  private MockHttpSession adminSession() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "admin-1");
    session.setAttribute("rol", "ADMIN");
    return session;
  }

  @Test
  void listWithoutSessionRedirectsToLogin() throws Exception {
    mvc.perform(get("/admin/usuarios"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void listWithNonAdminSessionRedirectsToItems() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "user-1");
    session.setAttribute("rol", "USUARIO");

    mvc.perform(get("/admin/usuarios").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));
  }

  @Test
  void listWithAdminSessionRendersUserList() throws Exception {
    when(profileService.list()).thenReturn(List.of());

    mvc.perform(get("/admin/usuarios").session(adminSession()))
        .andExpect(status().isOk())
        .andExpect(view().name("admin-usuarios"));
  }

  @Test
  void editOwnAccountRedirectsToList() throws Exception {
    mvc.perform(get("/admin/usuarios/admin-1/edit").session(adminSession()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/usuarios"));
  }

  @Test
  void deleteOwnAccountRedirectsWithoutDeleting() throws Exception {
    mvc.perform(post("/admin/usuarios/admin-1/delete").session(adminSession()))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/usuarios"));

    verify(authService, never()).deleteUser(any());
    verify(profileService, never()).delete(any());
  }

  @Test
  void updateOwnAccountRedirectsWithoutChanging() throws Exception {
    mvc.perform(post("/admin/usuarios/admin-1/update")
            .session(adminSession())
            .param("nombre", "Nuevo Nombre")
            .param("email", "nuevo@x.com")
            .param("rol", "ADMIN"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/admin/usuarios"));

    verify(profileService, never()).save(any(), any());
  }

  @Test
  void updateWithInvalidRoleRerendersWithError() throws Exception {
    var target = new UserProfile();
    target.setUid("user-2");
    target.setNombre("Ana");
    target.setEmail("ana@x.com");
    target.setRol("USUARIO");
    when(profileService.get("user-2")).thenReturn(target);

    mvc.perform(post("/admin/usuarios/user-2/update")
            .session(adminSession())
            .param("nombre", "Ana")
            .param("email", "ana@x.com")
            .param("rol", "SUPERADMIN"))
        .andExpect(status().isOk())
        .andExpect(view().name("admin-usuario-edit"))
        .andExpect(model().attributeExists("error"));

    verify(profileService, never()).save(any(), any());
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.admin.WebAdminUserControllerTest"`
Expected: FAIL to compile — `WebAdminUserController` and the two templates don't exist yet.

- [ ] **Step 3: Implement**

Create `WebAdminUserController.java`:

```java
package cl.grupo5.proyectominecraft.admin;

import cl.grupo5.proyectominecraft.auth.AuthService;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Controller
@RequestMapping("/admin/usuarios")
public class WebAdminUserController {
  private static final Set<String> ROLES_VALIDOS = Set.of("USUARIO", "ADMIN");

  private final UserProfileService service;
  private final AuthService auth;

  public WebAdminUserController(UserProfileService service, AuthService auth) {
    this.service = service;
    this.auth = auth;
  }

  @GetMapping
  public String list(Model m, HttpSession s) throws Exception {
    var guard = guardAdmin(s);
    if (guard != null) return guard;
    m.addAttribute("usuarios", service.list());
    return "admin-usuarios";
  }

  @GetMapping("/{uid}/edit")
  public String edit(@PathVariable String uid, Model m, HttpSession s) throws Exception {
    var guard = guardAdmin(s);
    if (guard != null) return guard;
    if (uid.equals(s.getAttribute("uid"))) return "redirect:/admin/usuarios";
    var target = service.get(uid);
    if (target == null) return "redirect:/admin/usuarios";
    m.addAttribute("usuario", target);
    return "admin-usuario-edit";
  }

  @PostMapping("/{uid}/update")
  public String update(@PathVariable String uid, @RequestParam String nombre, @RequestParam String email,
                       @RequestParam String rol, Model m, HttpSession s) throws Exception {
    var guard = guardAdmin(s);
    if (guard != null) return guard;
    if (uid.equals(s.getAttribute("uid"))) return "redirect:/admin/usuarios";
    var target = service.get(uid);
    if (target == null) return "redirect:/admin/usuarios";
    if (!ROLES_VALIDOS.contains(rol)) {
      m.addAttribute("usuario", target);
      m.addAttribute("error", "Rol inválido.");
      return "admin-usuario-edit";
    }
    target.setNombre(nombre);
    target.setEmail(email);
    target.setRol(rol);
    service.save(uid, target);
    return "redirect:/admin/usuarios";
  }

  @PostMapping("/{uid}/delete")
  public String delete(@PathVariable String uid, HttpSession s) throws Exception {
    var guard = guardAdmin(s);
    if (guard != null) return guard;
    if (uid.equals(s.getAttribute("uid"))) return "redirect:/admin/usuarios";
    auth.deleteUser(uid);
    service.delete(uid);
    return "redirect:/admin/usuarios";
  }

  private String guardAdmin(HttpSession s) {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    if (!"ADMIN".equals(s.getAttribute("rol"))) return "redirect:/items";
    return null;
  }
}
```

Create `admin-usuarios.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head><meta charset="UTF-8"><title>Administrar usuarios · Ore & Basalt</title>
<link rel="stylesheet" href="/style.css">
<link href="https://fonts.googleapis.com/css2?family=Big+Shoulders+Display:wght@700&family=Karla:wght@400;700&family=IBM+Plex+Mono:wght@400;700&display=swap" rel="stylesheet">
</head>
<body>
<h1>Administrar usuarios</h1>
<nav>
<a href="/items">Items</a>
<a href="/perfil">Perfil</a>
<form method="post" action="/logout"><button class="btn-ghost btn">Salir</button></form>
</nav>

<table>
  <tr><th>Nombre</th><th>Email</th><th>Rol</th><th>Acciones</th></tr>
  <tr th:each="u : ${usuarios}">
    <td th:text="${u.nombre}"></td>
    <td th:text="${u.email}"></td>
    <td><span class="badge" th:text="${u.rol}"></span></td>
    <td>
      <a th:href="@{/admin/usuarios/{uid}/edit(uid=${u.uid})}">Editar</a>
      <form method="post" th:action="@{/admin/usuarios/{uid}/delete(uid=${u.uid})}" style="display:inline" onsubmit="return confirm('¿Eliminar este usuario de forma permanente? Esta acción no se puede deshacer.');">
        <button class="btn-danger btn">Eliminar</button>
      </form>
    </td>
  </tr>
</table>
</body>
</html>
```

Create `admin-usuario-edit.html`:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head><meta charset="UTF-8"><title>Editar usuario · Ore & Basalt</title>
<link rel="stylesheet" href="/style.css">
<link href="https://fonts.googleapis.com/css2?family=Big+Shoulders+Display:wght@700&family=Karla:wght@400;700&family=IBM+Plex+Mono:wght@400;700&display=swap" rel="stylesheet">
</head>
<body>
<h1>Editar usuario</h1>
<nav><a href="/admin/usuarios">Volver</a></nav>
<div class="card">
<p class="error" th:if="${error}" th:text="${error}" role="alert"></p>
<form method="post" th:action="@{/admin/usuarios/{uid}/update(uid=${usuario.uid})}">
  Nombre:<br><input name="nombre" th:value="${usuario.nombre}" required><br><br>
  Email:<br><input type="email" name="email" th:value="${usuario.email}" required><br><br>
  Rol:<br>
  <select name="rol">
    <option value="USUARIO" th:selected="${usuario.rol == 'USUARIO'}">USUARIO</option>
    <option value="ADMIN" th:selected="${usuario.rol == 'ADMIN'}">ADMIN</option>
  </select><br><br>
  <button type="submit">Guardar</button>
</form>
</div>
</body>
</html>
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.admin.*"`
Expected: PASS (16 tests: 9 from `AdminUserControllerTest`, 7 from `WebAdminUserControllerTest`).

---

### Task 7: Nav visibility for admins

**Files:**
- Modify: `src/main/java/cl/grupo5/proyectominecraft/items/WebItemController.java`
- Modify: `src/main/java/cl/grupo5/proyectominecraft/perfil/WebProfileController.java`
- Modify: `src/main/resources/templates/items.html`
- Modify: `src/main/resources/templates/perfil.html`
- Modify: `src/test/java/cl/grupo5/proyectominecraft/items/WebItemControllerTest.java`
- Modify: `src/test/java/cl/grupo5/proyectominecraft/perfil/WebProfileControllerTest.java`

**Interfaces:**
- Consumes: session attribute `rol` (Task 4).
- No new interfaces produced — this task only adds a model attribute and a conditional nav link.

- [ ] **Step 1: Write the failing tests**

Add to `WebItemControllerTest.java` (inside the existing class, after `editWithMissingItemRedirectsToItems`):

```java
  @Test
  void listExposesIsAdminTrueForAdminSession() throws Exception {
    when(itemService.list(null, null)).thenReturn(List.of());
    var session = new MockHttpSession();
    session.setAttribute("uid", "admin-1");
    session.setAttribute("rol", "ADMIN");

    mvc.perform(get("/items").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("isAdmin", true));
  }

  @Test
  void listExposesIsAdminFalseForRegularSession() throws Exception {
    when(itemService.list(null, null)).thenReturn(List.of());
    var session = new MockHttpSession();
    session.setAttribute("uid", "user-1");
    session.setAttribute("rol", "USUARIO");

    mvc.perform(get("/items").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("isAdmin", false));
  }
```

Add to `WebProfileControllerTest.java` (inside the existing class, after `eliminarWithoutSessionRedirectsToLoginWithoutDeletingAnything`):

```java
  @Test
  void showExposesIsAdminTrueForAdminSession() throws Exception {
    when(profileService.get("admin-1")).thenReturn(null);
    var session = authenticated("admin-1");
    session.setAttribute("email", "admin@x.com");
    session.setAttribute("rol", "ADMIN");

    mvc.perform(get("/perfil").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("isAdmin", true));
  }
```

This requires `get` and `model` static imports in `WebProfileControllerTest.java`, which aren't there yet — add:
```java
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
```
alongside the existing `post`/`redirectedUrl`/`status` imports (don't remove those, they're still used by the other tests in the file).

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.items.WebItemControllerTest" --tests "cl.grupo5.proyectominecraft.perfil.WebProfileControllerTest"`
Expected: FAIL — `isAdmin` model attribute doesn't exist yet.

- [ ] **Step 3: Implement**

In `WebItemController.java`, replace the `list()` method with:

```java
  @GetMapping("/items")
  public String list(@RequestParam(required = false) String q,
                      @RequestParam(required = false) Boolean esMateriaPrima,
                      Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    m.addAttribute("items", items.list(q, esMateriaPrima));
    m.addAttribute("q", q);
    m.addAttribute("esMateriaPrima", esMateriaPrima);
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "items";
  }
```

In `WebProfileController.java`, replace the `show()` method with:

```java
  @GetMapping("/perfil")
  public String show(Model m, HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    var p = service.get(uid);
    if (p == null) {
      p = new UserProfile();
      p.setNombre(String.valueOf(s.getAttribute("email")));
      p.setEmail(String.valueOf(s.getAttribute("email")));
    }
    m.addAttribute("p", p);
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "perfil";
  }
```

In `items.html`, replace the `<nav>` block with:

```html
<nav>
<a href="/perfil">Perfil</a>
<a th:if="${isAdmin}" href="/admin/usuarios">Admin</a>
<form method="post" action="/logout"><button class="btn-ghost btn">Salir</button></form>
</nav>
```

In `perfil.html`, replace the `<nav>` block with:

```html
<nav>
<a href="/items">Items</a>
<a th:if="${isAdmin}" href="/admin/usuarios">Admin</a>
<form method="post" action="/logout"><button class="btn-ghost btn">Salir</button></form>
</nav>
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.items.*" --tests "cl.grupo5.proyectominecraft.perfil.*"`
Expected: PASS (all classes in both packages).

---

### Task 8: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full test suite**

Run: `gradlew.bat test`
Expected: PASS, including every pre-existing test class (unaffected — `WebAuthController`, `CorsConfig`, `WebItemController`, `WebProfileController` all gained constructor dependencies or a model attribute on already-Spring-managed beans, nothing structurally new for the context to fail on).

- [ ] **Step 2: Run the full build**

Run: `gradlew.bat build`
Expected: PASS.

- [ ] **Step 3: Manual smoke check**

Configure a real email in `admin.emails` in `application.properties` (or via `-Dadmin.emails=...`), then in a browser:
1. Register with that email — confirm you land on `/items` and see an "Admin" nav link.
2. Visit `/admin/usuarios` — confirm your own account (and any others) show up.
3. Register a second, non-admin account in a private/incognito window — confirm it has no "Admin" link and gets redirected to `/items` if it tries to visit `/admin/usuarios` directly.
4. As the admin, edit the second account's `rol` to `ADMIN`, save — confirm the change persists (view it again).
5. Try to edit or delete your own admin account from `/admin/usuarios` — confirm both redirect back to the list without changing anything.
6. Delete the second account from the admin panel — confirm it disappears from the list and can no longer log in.

---

### Task 9: Documentation

**Files:**
- Create: `docs/admin-user-crud.md`

Write a Markdown summary covering: what changed (admin bootstrap via config, the new `/api/admin/usuarios` and `/admin/usuarios` surfaces, the self-protection guard, nav visibility), the new `admin.emails` config property and how to use it, the full route table, and an updated "what's next" pointer (remaining: Gestión de Inventario, Recetas+Calculadora+Grilla, Proyectos+Estados+Documento, Modelo 3D, Toast — "Gestión de Materiales" already partially covered by the `esMateriaPrima` filter from the prior change, "CRUD Usuario (Admin)" now done). Base it on `openspec/changes/admin-user-crud/{proposal,design}.md` plus whatever changed during implementation.
