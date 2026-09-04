package cl.grupo5.proyectominecraft.perfil;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/perfil")
public class UserProfileController {
  private final UserProfileService service;
  public UserProfileController(UserProfileService service) { this.service = service; }

  @GetMapping("/{uid}")
  public ResponseEntity<?> get(@PathVariable String uid) throws Exception {
    var p = service.get(uid);
    return p == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(p);
  }

  @PutMapping("/{uid}")
  public ResponseEntity<?> save(@PathVariable String uid, @Valid @RequestBody UserProfile p) throws Exception {
    return ResponseEntity.ok(service.save(uid, p));
  }
}
