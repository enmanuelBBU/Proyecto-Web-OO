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
    if (body.getRol() == null || !ROLES_VALIDOS.contains(body.getRol())) {
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
