package cl.grupo5.proyectominecraft.mundos;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mundos")
public class MundoController {
  private final MundoService mundos;

  public MundoController(MundoService mundos) {
    this.mundos = mundos;
  }

  @GetMapping
  public ResponseEntity<?> list(HttpSession s) throws Exception {
    return ResponseEntity.ok(mundos.listar(uid(s)));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable String id, HttpSession s) throws Exception {
    var mundo = mundos.get(id);
    if (!MundoService.puedeGestionar(mundo, uid(s), rol(s))) return ResponseEntity.notFound().build();
    return ResponseEntity.ok(mundo);
  }

  @PostMapping
  public ResponseEntity<?> create(@RequestBody Mundo body, HttpSession s) throws Exception {
    try {
      return ResponseEntity.status(HttpStatus.CREATED).body(mundos.crear(body, uid(s)));
    } catch (MundoValidationException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable String id, @RequestBody Mundo body, HttpSession s) throws Exception {
    if (!MundoService.puedeGestionar(mundos.get(id), uid(s), rol(s))) return ResponseEntity.notFound().build();
    try {
      var actualizado = mundos.actualizar(id, body, uid(s), rol(s));
      return actualizado == null ? ResponseEntity.notFound().build() : ResponseEntity.ok(actualizado);
    } catch (MundoValidationException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable String id, HttpSession s) throws Exception {
    if (!MundoService.puedeGestionar(mundos.get(id), uid(s), rol(s))) return ResponseEntity.notFound().build();
    try {
      return mundos.eliminar(id, uid(s), rol(s))
          ? ResponseEntity.noContent().build()
          : ResponseEntity.notFound().build();
    } catch (MundoValidationException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    }
  }

  private static String uid(HttpSession s) {
    return (String) s.getAttribute("uid");
  }

  private static String rol(HttpSession s) {
    return (String) s.getAttribute("rol");
  }
}
