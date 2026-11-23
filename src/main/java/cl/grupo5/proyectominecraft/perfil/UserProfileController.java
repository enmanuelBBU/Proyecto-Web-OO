package cl.grupo5.proyectominecraft.perfil;

import cl.grupo5.proyectominecraft.auditoria.AuditService;
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
  private final AuditService audit;

  public UserProfileController(UserProfileService service, AuthService auth, AuditService audit) {
    this.service = service;
    this.auth = auth;
    this.audit = audit;
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
    var guardado = service.save(uid, p);
    audit.registrar(session, AuditService.EDITAR, AuditService.USUARIO, uid, p.getEmail(), null);
    return ResponseEntity.ok(guardado);
  }

  @DeleteMapping("/me")
  public ResponseEntity<?> delete(HttpSession session) throws Exception {
    String uid = uid(session);
    var perfil = service.get(uid);
    try {
      auth.deleteUser(uid);
      service.delete(uid);
      audit.registrar(session, AuditService.ELIMINAR, AuditService.USUARIO, uid,
          perfil != null ? perfil.getEmail() : null, null);
    } finally {
      session.invalidate();
    }
    return ResponseEntity.noContent().build();
  }

  private String uid(HttpSession session) {
    return (String) session.getAttribute("uid");
  }
}
