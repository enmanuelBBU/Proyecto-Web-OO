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
    try {
      auth.deleteUser(uid);
      service.delete(uid);
    } finally {
      session.invalidate();
    }
    return ResponseEntity.noContent().build();
  }

  private String uid(HttpSession session) {
    return (String) session.getAttribute("uid");
  }
}
