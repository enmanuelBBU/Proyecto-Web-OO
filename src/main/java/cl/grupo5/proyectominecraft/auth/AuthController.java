package cl.grupo5.proyectominecraft.auth;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {
  private final AuthService auth;
  public AuthController(AuthService auth) { this.auth = auth; }

  @PostMapping("/verify")
  public ResponseEntity<?> verify(@RequestBody Map<String, String> body) throws Exception {
    return ResponseEntity.ok(auth.verify(body.get("idToken")));
  }
}
