# Complete Auth + Perfil + Items CRUD + Search Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Bring Autenticación, Perfil de Usuario (self-service CRUD), CRUD de Items, and Búsqueda de Items to a complete, secure state — fixing a role self-escalation bug and an IDOR, and aligning the `Item` model with the project's reference domain model.

**Architecture:** Spring Boot MVC (Thymeleaf web controllers + a parallel `/api/**` REST layer) over Firestore, gated by the existing session-attribute checks (web) and `ApiAuthInterceptor` (REST). No new packages, beans, or dependencies — this plan edits `items`, `perfil`, and `auth` in place.

**Tech Stack:** Java 17, Spring Boot 4.1.1 (webmvc, validation, thymeleaf), Lombok, Firebase Admin SDK 9.4.0 (Firestore + Auth), JUnit 5 + Mockito + AssertJ (via `spring-boot-starter-test`), Gradle wrapper (`gradlew.bat`).

**Spec:** `openspec/changes/complete-auth-perfil-items/{proposal,design}.md` and `specs/{auth,perfil,items}/spec.md` — read these alongside this plan; this plan implements exactly their requirements/scenarios.

## Global Constraints

- No new dependencies, packages, or beans beyond what each task states.
- Keep the existing session-attribute auth pattern (`HttpSession.getAttribute("uid")` for web, `ApiAuthInterceptor` for `/api/**`) — do not introduce Spring Security.
- All user-facing error text is in Spanish and actionable, per `DESIGN-SYSTEM.md` §8 (e.g. "El nombre del ítem es obligatorio.", never "Error 400").
- No Firestore data migration script — documents are fully overwritten (`set()`) the next time they're written; this is a development project with no production data.
- Test pattern: `@WebMvcTest` slices with `@MockitoBean` for collaborators, following the existing `ItemApiAuthTest` style (`@Import({ApiAuthInterceptor.class, CorsConfig.class})` for anything under `/api/**`).
- **Do NOT run `git commit`.** Stage changes only if asked; the project owner commits manually. Every task below ends at "tests pass", not at a commit step.
- Verify command: `gradlew.bat test`. Build command: `gradlew.bat build`.

---

### Task 1: `Item` model + testable search/filter logic

**Files:**
- Modify: `src/main/java/cl/grupo5/proyectominecraft/items/Item.java`
- Modify: `src/main/java/cl/grupo5/proyectominecraft/items/ItemService.java`
- Create: `src/test/java/cl/grupo5/proyectominecraft/items/ItemServiceTest.java`

**Interfaces:**
- Produces: `Item` with fields `id: String`, `nombre: String` (`@NotBlank`), `categoria: String` (nullable), `esMateriaPrima: boolean` (default `false`); getters/setters via Lombok `@Data` (`isEsMateriaPrima()`/`setEsMateriaPrima(boolean)`).
- Produces: `ItemService.filter(List<Item> items, String q, Boolean esMateriaPrima) -> List<Item>` (package-private `static`, pure — no Firestore access) — consumed by `ItemService.list` (Task 1) and directly by tests.
- Produces: `ItemService.list(String q, Boolean esMateriaPrima) throws Exception -> List<Item>` — consumed by `ItemController` (Task 3) and `WebItemController` (Task 4).

- [ ] **Step 1: Write the failing test**

```java
package cl.grupo5.proyectominecraft.items;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThat;

class ItemServiceTest {

  private Item item(String nombre, String categoria, boolean esMateriaPrima) {
    var i = new Item();
    i.setNombre(nombre);
    i.setCategoria(categoria);
    i.setEsMateriaPrima(esMateriaPrima);
    return i;
  }

  @Test
  void filterMatchesQueryAgainstNombreOrCategoria() {
    var items = List.of(
        item("Hierro", "Mineral", true),
        item("Pico de hierro", "Herramienta", false),
        item("Tabla", "Madera", true)
    );

    var result = ItemService.filter(items, "miner", null);

    assertThat(result).extracting(Item::getNombre).containsExactly("Hierro");
  }

  @Test
  void filterByEsMateriaPrima() {
    var items = List.of(
        item("Hierro", "Mineral", true),
        item("Pico de hierro", "Herramienta", false)
    );

    var result = ItemService.filter(items, null, true);

    assertThat(result).extracting(Item::getNombre).containsExactly("Hierro");
  }

  @Test
  void filterWithNoCriteriaReturnsAllItems() {
    var items = List.of(item("Hierro", "Mineral", true));

    var result = ItemService.filter(items, null, null);

    assertThat(result).hasSize(1);
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.items.ItemServiceTest"`
Expected: FAIL to compile — `Item.setCategoria`/`setEsMateriaPrima` and `ItemService.filter` don't exist yet.

- [ ] **Step 3: Write the implementation**

Replace `Item.java` entirely with:

```java
package cl.grupo5.proyectominecraft.items;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class Item {
  private String id;
  @NotBlank
  private String nombre;
  private String categoria;
  private boolean esMateriaPrima;
}
```

Replace `ItemService.java` entirely with:

```java
package cl.grupo5.proyectominecraft.items;

import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class ItemService {
  private final Firestore db;
  public ItemService(Firestore db) { this.db = db; }

  public List<Item> list(String q, Boolean esMateriaPrima) throws Exception {
    var docs = db.collection("items").get().get().getDocuments();
    var items = docs.stream().map(d -> {
      var it = d.toObject(Item.class);
      it.setId(d.getId());
      return it;
    }).toList();
    return filter(items, q, esMateriaPrima);
  }

  static List<Item> filter(List<Item> items, String q, Boolean esMateriaPrima) {
    var result = items;
    if (q != null && !q.isBlank()) {
      var needle = q.toLowerCase();
      result = result.stream().filter(i ->
          (i.getNombre() != null && i.getNombre().toLowerCase().contains(needle)) ||
          (i.getCategoria() != null && i.getCategoria().toLowerCase().contains(needle))
      ).toList();
    }
    if (esMateriaPrima != null) {
      result = result.stream().filter(i -> i.isEsMateriaPrima() == esMateriaPrima).toList();
    }
    return result;
  }

  public Item get(String id) throws Exception {
    var snap = db.collection("items").document(id).get().get();
    if (!snap.exists()) return null;
    var it = snap.toObject(Item.class);
    it.setId(id);
    return it;
  }

  public Item create(Item item) throws Exception {
    var ref = db.collection("items").add(item).get();
    item.setId(ref.getId());
    return item;
  }

  public Item update(String id, Item item) throws Exception {
    var snap = db.collection("items").document(id).get().get();
    if (!snap.exists()) return null;
    db.collection("items").document(id).set(item).get();
    item.setId(id);
    return item;
  }

  public boolean delete(String id) throws Exception {
    var snap = db.collection("items").document(id).get().get();
    if (!snap.exists()) return false;
    db.collection("items").document(id).delete().get();
    return true;
  }
}
```

(This step also implements Task 2's `update`/`delete` existence checks — they're in the same file and cheaper to land together; Task 2 below only adds the tests that pin this behavior down.)

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.items.ItemServiceTest"`
Expected: PASS (3 tests).

---

### Task 2: `ItemService.update`/`delete` not-found behavior (pinning tests)

**Files:**
- Modify: `src/test/java/cl/grupo5/proyectominecraft/items/ItemServiceTest.java`

**Interfaces:**
- Consumes: `ItemService.update(String, Item) -> Item` (returns `null` when `id` doesn't exist — already implemented in Task 1's Step 3), `ItemService.delete(String) -> boolean` (returns `false` when `id` doesn't exist — already implemented).
- These behaviors are exercised end-to-end through `ItemController` in Task 3; this task only documents the contract at the unit level via a Firestore-independent note (see below) since `update`/`delete` need a live `Firestore` to unit-test directly, and this codebase has no Firestore test-double today.

Since mocking the `Firestore`/`DocumentReference`/`ApiFuture` chain has no precedent in this codebase and would add a new test-infrastructure dependency for a single behavior, this contract is verified instead at the controller layer in Task 3 (`updateUnknownIdIsNotFound`, `deleteUnknownIdIsNotFound`), where `ItemService` is mocked directly. No separate step needed here — this task exists in the plan only to record that decision; skip to Task 3.

---

### Task 3: `ItemController` — search params, 404s, and its tests

**Files:**
- Modify: `src/main/java/cl/grupo5/proyectominecraft/items/ItemController.java`
- Modify: `src/test/java/cl/grupo5/proyectominecraft/items/ItemApiAuthTest.java`
- Create: `src/test/java/cl/grupo5/proyectominecraft/items/ItemControllerTest.java`

**Interfaces:**
- Consumes: `ItemService.list(String, Boolean)`, `ItemService.update(String, Item) -> Item|null`, `ItemService.delete(String) -> boolean` (from Task 1).
- Produces: `GET /api/items?q=&esMateriaPrima=`, `POST /api/items` (400 on invalid body), `PUT /api/items/{id}` (404 if missing), `DELETE /api/items/{id}` (404 if missing) — consumed by `WebItemController` only indirectly (it calls `ItemService` directly, not this REST layer).

- [ ] **Step 1: Write the failing tests**

Append to `ItemApiAuthTest.java` (inside the existing class, after `listWithSessionIsOk`):

```java
  @Test
  void createWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/api/items")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Piedra\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.put("/api/items/abc")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Piedra\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.delete("/api/items/abc"))
        .andExpect(status().isUnauthorized());
  }
```

Create `ItemControllerTest.java`:

```java
package cl.grupo5.proyectominecraft.items;

import cl.grupo5.proyectominecraft.config.ApiAuthInterceptor;
import cl.grupo5.proyectominecraft.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ItemController.class)
@Import({ApiAuthInterceptor.class, CorsConfig.class})
class ItemControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private ItemService itemService;

  private MockHttpSession authenticated() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");
    return session;
  }

  @Test
  void createWithBlankNombreIsBadRequest() throws Exception {
    mvc.perform(post("/api/items")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateUnknownIdIsNotFound() throws Exception {
    when(itemService.update(eq("missing-1"), any())).thenReturn(null);

    mvc.perform(put("/api/items/missing-1")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Piedra\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteUnknownIdIsNotFound() throws Exception {
    when(itemService.delete("missing-1")).thenReturn(false);

    mvc.perform(delete("/api/items/missing-1").session(authenticated()))
        .andExpect(status().isNotFound());
  }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.items.ItemApiAuthTest" --tests "cl.grupo5.proyectominecraft.items.ItemControllerTest"`
Expected: `updateUnknownIdIsNotFound`/`deleteUnknownIdIsNotFound` FAIL (controller still returns 200/204); the three new auth tests should already PASS (interceptor already covers them) — confirm that, then continue.

- [ ] **Step 3: Implement**

Replace `ItemController.java` entirely with:

```java
package cl.grupo5.proyectominecraft.items;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/items")
public class ItemController {
  private final ItemService service;
  public ItemController(ItemService service) { this.service = service; }

  @GetMapping
  public ResponseEntity<?> list(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) Boolean esMateriaPrima) throws Exception {
    return ResponseEntity.ok(service.list(q, esMateriaPrima));
  }

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody Item item) throws Exception {
    return ResponseEntity.ok(service.create(item));
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable String id, @Valid @RequestBody Item item) throws Exception {
    var updated = service.update(id, item);
    if (updated == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok(updated);
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable String id) throws Exception {
    if (!service.delete(id)) return ResponseEntity.notFound().build();
    return ResponseEntity.noContent().build();
  }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.items.*"`
Expected: PASS (all `Item*Test` classes).

---

### Task 4: `WebItemController` + `items.html` + `item-edit.html`

**Files:**
- Modify: `src/main/java/cl/grupo5/proyectominecraft/items/WebItemController.java`
- Modify: `src/main/resources/templates/items.html`
- Modify: `src/main/resources/templates/item-edit.html`
- Create: `src/test/java/cl/grupo5/proyectominecraft/items/WebItemControllerTest.java`

**Interfaces:**
- Consumes: `ItemService.list(String, Boolean)`, `ItemService.get(String)`, `ItemService.create(Item)`, `ItemService.update(String, Item)` (Task 1).
- Produces: `GET/POST /items`, `GET /items/{id}/edit`, `POST /items/{id}/update`, `POST /items/{id}/delete` — no other task depends on these signatures.

- [ ] **Step 1: Write the failing test**

```java
package cl.grupo5.proyectominecraft.items;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = WebItemController.class)
class WebItemControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private ItemService itemService;

  @Test
  void createWithBlankNombreRerendersItemsWithError() throws Exception {
    when(itemService.list(null, null)).thenReturn(List.of());
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");

    mvc.perform(post("/items").session(session).param("nombre", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("items"))
        .andExpect(model().attributeExists("error"));
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.items.WebItemControllerTest"`
Expected: FAIL — controller currently redirects instead of re-rendering with an error.

- [ ] **Step 3: Implement**

Replace `WebItemController.java` entirely with:

```java
package cl.grupo5.proyectominecraft.items;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebItemController {
  private final ItemService items;

  public WebItemController(ItemService items) {
    this.items = items;
  }

  @GetMapping("/items")
  public String list(@RequestParam(required = false) String q,
                      @RequestParam(required = false) Boolean esMateriaPrima,
                      Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    m.addAttribute("items", items.list(q, esMateriaPrima));
    m.addAttribute("q", q);
    m.addAttribute("esMateriaPrima", esMateriaPrima);
    return "items";
  }

  @PostMapping("/items")
  public String create(@RequestParam String nombre, @RequestParam(required = false) String categoria,
                       @RequestParam(defaultValue = "false") boolean esMateriaPrima,
                       Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    if (nombre == null || nombre.isBlank()) {
      m.addAttribute("error", "El nombre del ítem es obligatorio.");
      m.addAttribute("items", items.list(null, null));
      return "items";
    }
    var it = new Item();
    it.setNombre(nombre);
    it.setCategoria(categoria);
    it.setEsMateriaPrima(esMateriaPrima);
    items.create(it);
    return "redirect:/items";
  }

  @GetMapping("/items/{id}/edit")
  public String edit(@PathVariable String id, Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    m.addAttribute("item", items.get(id));
    return "item-edit";
  }

  @PostMapping("/items/{id}/update")
  public String update(@PathVariable String id, @RequestParam String nombre,
                       @RequestParam(required = false) String categoria,
                       @RequestParam(defaultValue = "false") boolean esMateriaPrima,
                       Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    if (nombre == null || nombre.isBlank()) {
      m.addAttribute("item", items.get(id));
      m.addAttribute("error", "El nombre del ítem es obligatorio.");
      return "item-edit";
    }
    var it = items.get(id);
    it.setNombre(nombre);
    it.setCategoria(categoria);
    it.setEsMateriaPrima(esMateriaPrima);
    items.update(id, it);
    return "redirect:/items";
  }

  @PostMapping("/items/{id}/delete")
  public String delete(@PathVariable String id, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    items.delete(id);
    return "redirect:/items";
  }
}
```

Replace `items.html` entirely with:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head><meta charset="UTF-8"><title>Items · Ore & Basalt</title>
<link rel="stylesheet" href="/style.css">
<link href="https://fonts.googleapis.com/css2?family=Big+Shoulders+Display:wght@700&family=Karla:wght@400;700&family=IBM+Plex+Mono:wght@400;700&display=swap" rel="stylesheet">
</head>
<body>
<h1>⛏️ Items</h1>
<nav>
<a href="/perfil">Perfil</a>
<form method="post" action="/logout"><button class="btn-ghost btn">Salir</button></form>
</nav>

<div class="card">
<form method="get" action="/items">
  <input type="text" name="q" th:value="${q}" placeholder="Buscar por nombre o categoría">
  <label><input type="checkbox" name="esMateriaPrima" value="true" th:checked="${esMateriaPrima != null and esMateriaPrima}"> Solo materiales</label>
  <button class="btn-info btn" type="submit">Buscar</button>
</form>
</div>

<div class="card">
<h2>Crear item</h2>
<p class="error" th:if="${error}" th:text="${error}" role="alert"></p>
<form method="post" action="/items">
  Nombre: <input name="nombre" required>
  Categoría: <input name="categoria">
  <label><input type="checkbox" name="esMateriaPrima" value="true"> Es materia prima</label>
  <button type="submit">Crear</button>
</form>
</div>

<table>
  <tr><th>Nombre</th><th>Categoría</th><th>Materia prima</th><th>Acciones</th></tr>
  <tr th:each="i : ${items}">
    <td th:text="${i.nombre}"></td>
    <td><span class="badge badge-pendiente" th:text="${i.categoria}"></span></td>
    <td class="mono" th:text="${i.esMateriaPrima} ? 'Sí' : 'No'"></td>
    <td>
      <a th:href="@{/items/{id}/edit(id=${i.id})}">Editar</a>
      <form method="post" th:action="@{/items/{id}/delete(id=${i.id})}" style="display:inline">
        <button class="btn-danger btn">Eliminar</button>
      </form>
    </td>
  </tr>
</table>
</body>
</html>
```

Replace `item-edit.html` entirely with:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head><meta charset="UTF-8"><title>Editar Item · Ore & Basalt</title>
<link rel="stylesheet" href="/style.css">
<link href="https://fonts.googleapis.com/css2?family=Big+Shoulders+Display:wght@700&family=Karla:wght@400;700&family=IBM+Plex+Mono:wght@400;700&display=swap" rel="stylesheet">
</head>
<body>
<h1>Editar item</h1>
<nav><a href="/items">Volver</a></nav>
<div class="card">
<p class="error" th:if="${error}" th:text="${error}" role="alert"></p>
<form method="post" th:action="@{/items/{id}/update(id=${item.id})}">
  Nombre:<br><input name="nombre" th:value="${item.nombre}" required><br><br>
  Categoría:<br><input name="categoria" th:value="${item.categoria}"><br><br>
  <label><input type="checkbox" name="esMateriaPrima" value="true" th:checked="${item.esMateriaPrima}"> Es materia prima</label><br><br>
  <button type="submit">Guardar</button>
</form>
</div>
</body>
</html>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.items.*"`
Expected: PASS (all classes in the `items` package, including the untouched `ItemServiceTest`/`ItemApiAuthTest`/`ItemControllerTest` from Tasks 1 and 3).

---

### Task 5: Perfil self-service completion — `AuthService.deleteUser`, `UserProfileService.delete`, `/api/perfil/me`, `/perfil/eliminar`

This task lands `AuthService`, `UserProfileService`, `UserProfileController`, `WebProfileController`, and `perfil.html` together: the REST controller needs `UserProfileService.delete` and `AuthService.deleteUser`, and the web controller needs both of those plus the REST controller's route rename doesn't affect it (it calls the service directly) — but since all five pieces are small and mutually referential, splitting them into separate tasks would leave the codebase non-compiling between tasks. This is a "reviewer accepts or rejects the whole slice" unit.

**Files:**
- Modify: `src/main/java/cl/grupo5/proyectominecraft/auth/AuthService.java`
- Modify: `src/main/java/cl/grupo5/proyectominecraft/perfil/UserProfileService.java`
- Modify: `src/main/java/cl/grupo5/proyectominecraft/perfil/UserProfileController.java`
- Modify: `src/main/java/cl/grupo5/proyectominecraft/perfil/WebProfileController.java`
- Modify: `src/main/resources/templates/perfil.html`
- Create: `src/test/java/cl/grupo5/proyectominecraft/perfil/UserProfileControllerTest.java`

**Interfaces:**
- Consumes: `UserProfileService.get(String uid)`, `UserProfileService.save(String, UserProfile)` (already exist, unchanged signatures).
- Produces: `AuthService.deleteUser(String uid) throws Exception` — consumed by both `UserProfileController` and `WebProfileController` in this same task.
- Produces: `UserProfileService.delete(String uid) throws Exception` — consumed by both controllers in this same task.
- Produces: `GET/PUT/DELETE /api/perfil/me` (replacing `/api/perfil/{uid}`) and `POST /perfil/eliminar` — no other task depends on these routes.

- [ ] **Step 1: Write the failing test**

```java
package cl.grupo5.proyectominecraft.perfil;

import cl.grupo5.proyectominecraft.auth.AuthService;
import cl.grupo5.proyectominecraft.config.ApiAuthInterceptor;
import cl.grupo5.proyectominecraft.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserProfileController.class)
@Import({ApiAuthInterceptor.class, CorsConfig.class})
class UserProfileControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private UserProfileService profileService;

  @MockitoBean
  private AuthService authService;

  private MockHttpSession authenticated(String uid) {
    var session = new MockHttpSession();
    session.setAttribute("uid", uid);
    return session;
  }

  @Test
  void getWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(get("/api/perfil/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void putWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(put("/api/perfil/me")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"x\",\"email\":\"x@x.com\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(delete("/api/perfil/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void saveDefaultsRoleToUsuarioWhenNoExistingProfile() throws Exception {
    when(profileService.get("uid-2")).thenReturn(null);
    when(profileService.save(eq("uid-2"), any())).thenAnswer(inv -> inv.getArgument(1));

    mvc.perform(put("/api/perfil/me")
            .session(authenticated("uid-2"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Nuevo\",\"email\":\"nuevo@x.com\"}"))
        .andExpect(status().isOk());

    var captor = org.mockito.ArgumentCaptor.forClass(UserProfile.class);
    verify(profileService).save(eq("uid-2"), captor.capture());
    assertThat(captor.getValue().getRol()).isEqualTo("USUARIO");
  }

  @Test
  void savePreservesExistingRoleIgnoringSubmittedValue() throws Exception {
    var existing = new UserProfile();
    existing.setNombre("Ana");
    existing.setEmail("ana@x.com");
    existing.setRol("USUARIO");
    when(profileService.get("uid-1")).thenReturn(existing);
    when(profileService.save(eq("uid-1"), any())).thenAnswer(inv -> inv.getArgument(1));

    mvc.perform(put("/api/perfil/me")
            .session(authenticated("uid-1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Ana\",\"email\":\"ana@x.com\",\"rol\":\"ADMIN\"}"))
        .andExpect(status().isOk());

    var captor = org.mockito.ArgumentCaptor.forClass(UserProfile.class);
    verify(profileService).save(eq("uid-1"), captor.capture());
    assertThat(captor.getValue().getRol()).isEqualTo("USUARIO");
  }

  @Test
  void deleteInvalidatesSessionAndDeletesAuthUser() throws Exception {
    var session = authenticated("uid-1");

    mvc.perform(delete("/api/perfil/me").session(session))
        .andExpect(status().isNoContent());

    verify(profileService).delete("uid-1");
    verify(authService).deleteUser("uid-1");
    assertThat(session.isInvalid()).isTrue();
  }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.perfil.UserProfileControllerTest"`
Expected: FAIL to compile — `/api/perfil/me` routes, `UserProfileService.delete`, and `AuthService.deleteUser` don't exist yet.

- [ ] **Step 3: Implement all five pieces together**

Add to `AuthService.java` (inside the existing class, after `verify`):

```java
  public void deleteUser(String uid) throws Exception {
    com.google.firebase.auth.FirebaseAuth.getInstance().deleteUser(uid);
  }
```

Add to `UserProfileService.java` (inside the existing class, after `save`):

```java
  public void delete(String uid) throws Exception {
    db.collection("users").document(uid).delete().get();
  }
```

Replace `UserProfileController.java` entirely with:

```java
package cl.grupo5.proyectominecraft.perfil;

import cl.grupo5.proyectominecraft.auth.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/perfil")
public class UserProfileController {
  private final UserProfileService service;
  private final AuthService auth;

  public UserProfileController(UserProfileService service, AuthService auth) {
    this.service = service;
    this.auth = auth;
  }

  @GetMapping("/me")
  public ResponseEntity<?> get(HttpSession session) throws Exception {
    var p = service.get(uid(session));
    return p == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(p);
  }

  @PutMapping("/me")
  public ResponseEntity<?> save(@Valid @RequestBody UserProfile p, HttpSession session) throws Exception {
    String uid = uid(session);
    var existing = service.get(uid);
    p.setRol(existing != null ? existing.getRol() : "USUARIO");
    return ResponseEntity.ok(service.save(uid, p));
  }

  @DeleteMapping("/me")
  public ResponseEntity<?> delete(HttpSession session) throws Exception {
    String uid = uid(session);
    service.delete(uid);
    auth.deleteUser(uid);
    session.invalidate();
    return ResponseEntity.noContent().build();
  }

  private String uid(HttpSession session) {
    return (String) session.getAttribute("uid");
  }
}
```

Replace `WebProfileController.java` entirely with:

```java
package cl.grupo5.proyectominecraft.perfil;

import cl.grupo5.proyectominecraft.auth.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebProfileController {
  private final UserProfileService service;
  private final AuthService auth;

  public WebProfileController(UserProfileService service, AuthService auth) {
    this.service = service;
    this.auth = auth;
  }

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
    return "perfil";
  }

  @PostMapping("/perfil")
  public String save(@RequestParam String nombre, @RequestParam String email, HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    var existing = service.get(uid);
    var p = new UserProfile();
    p.setNombre(nombre);
    p.setEmail(email);
    p.setRol(existing != null ? existing.getRol() : "USUARIO");
    service.save(uid, p);
    return "redirect:/perfil";
  }

  @PostMapping("/perfil/eliminar")
  public String eliminar(HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    service.delete(uid);
    auth.deleteUser(uid);
    s.invalidate();
    return "redirect:/login";
  }
}
```

Replace `perfil.html` entirely with:

```html
<!DOCTYPE html>
<html xmlns:th="http://www.thymeleaf.org">
<head><meta charset="UTF-8"><title>Perfil · Ore & Basalt</title>
<link rel="stylesheet" href="/style.css">
<link href="https://fonts.googleapis.com/css2?family=Big+Shoulders+Display:wght@700&family=Karla:wght@400;700&family=IBM+Plex+Mono:wght@400;700&display=swap" rel="stylesheet">
</head>
<body>
<h1>Perfil</h1>
<nav>
<a href="/items">Items</a>
<form method="post" action="/logout"><button class="btn-ghost btn">Salir</button></form>
</nav>
<div class="card">
<form method="post" action="/perfil">
  Nombre:<br><input name="nombre" th:value="${p.nombre}" required><br><br>
  Email:<br><input type="email" name="email" th:value="${p.email}" required><br><br>
  Rol:<br><span class="badge" th:text="${p.rol}"></span><br><br>
  <button type="submit">Guardar</button>
</form>
</div>
<div class="card">
<h2>Eliminar cuenta</h2>
<p>Esta acción borra tu perfil y tu cuenta de acceso de forma permanente.</p>
<form method="post" action="/perfil/eliminar" onsubmit="return confirm('¿Eliminar tu cuenta de forma permanente? Esta acción no se puede deshacer.');">
  <button class="btn-danger btn" type="submit">Eliminar cuenta</button>
</form>
</div>
</body>
</html>
```

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.perfil.UserProfileControllerTest"`
Expected: PASS (6 tests).

---

### Task 6: Redirect already-authenticated sessions away from `/login` and `/register`

**Files:**
- Modify: `src/main/java/cl/grupo5/proyectominecraft/auth/WebAuthController.java`
- Create: `src/test/java/cl/grupo5/proyectominecraft/auth/WebAuthControllerTest.java`

**Interfaces:**
- No new interfaces produced or consumed — this task only changes the two `@GetMapping` handlers' logic.

- [ ] **Step 1: Write the failing test**

```java
package cl.grupo5.proyectominecraft.auth;

import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebAuthController.class)
class WebAuthControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private FirebaseIdentityService identity;

  @MockitoBean
  private UserProfileService profiles;

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
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.auth.WebAuthControllerTest"`
Expected: FAIL on the two redirect tests (current handlers always render the form).

- [ ] **Step 3: Implement**

In `WebAuthController.java`, replace:

```java
  @GetMapping("/login")
  public String login() { return "login"; }
```

with:

```java
  @GetMapping("/login")
  public String login(HttpSession s) {
    if (s.getAttribute("uid") != null) return "redirect:/items";
    return "login";
  }
```

And replace:

```java
  @GetMapping("/register")
  public String register() { return "register"; }
```

with:

```java
  @GetMapping("/register")
  public String register(HttpSession s) {
    if (s.getAttribute("uid") != null) return "redirect:/items";
    return "register";
  }
```

(`HttpSession` is already imported in this file.)

- [ ] **Step 4: Run test to verify it passes**

Run: `gradlew.bat test --tests "cl.grupo5.proyectominecraft.auth.WebAuthControllerTest"`
Expected: PASS (3 tests).

---

### Task 7: Full verification

**Files:** none (verification only).

- [ ] **Step 1: Run the full test suite**

Run: `gradlew.bat test`
Expected: PASS, including the pre-existing `ProyectominecraftApplicationTests` context-load test (unaffected — `AuthService`, `UserProfileService`, and `UserProfileController`/`WebProfileController`'s new constructor dependencies are all existing beans, no new Spring configuration needed).

- [ ] **Step 2: Run the full build**

Run: `gradlew.bat build`
Expected: PASS.

- [ ] **Step 3: Manual smoke check**

Start the app (`gradlew.bat bootRun`) and in a browser:
1. Log out if logged in; visit `/login` and `/register` — confirm both render normally.
2. Log in; visit `/login` again while authenticated — confirm it redirects straight to `/items`.
3. On `/items`: create an item with `categoria` set and `esMateriaPrima` checked; confirm it appears with the right badge/column values.
4. Search by a substring of the `categoria` value — confirm it matches. Check "Solo materiales" — confirm only `esMateriaPrima=true` items show.
5. Try creating an item with a blank name — confirm the page re-renders with a Spanish error and no new row appears.
6. Edit an item, clear its name, submit — confirm the edit page re-renders with the error.
7. On `/perfil`: confirm "Rol" shows as read-only text (no input), update nombre/email, confirm it saves and the role is unchanged.
8. Click "Eliminar cuenta", confirm the browser `confirm()` prompt, accept it, confirm you land on `/login` and can no longer log in with that account.

---

### Task 8: Documentation

**Files:**
- Create: `docs/complete-auth-perfil-items.md`

Write a Markdown summary covering: what changed (auth redirect, perfil self-service CRUD incl. account deletion, items CRUD + search + materiales filter), the security fixes (role self-escalation, profile IDOR), the final `Item`/`UserProfile` field lists, the full REST/web route table (old → new for anything renamed), and a short "what's next" pointer to the remaining sub-projects (materiales/admin CRUD, inventario, recetas+calculadora+grilla, proyectos+estados+documento, modelo 3D, toast). Base it on `openspec/changes/complete-auth-perfil-items/{proposal,design}.md` plus whatever changed during implementation. This is a deliverable the project owner asked for after implementation finishes — **do not commit it**, per the Global Constraints above.
