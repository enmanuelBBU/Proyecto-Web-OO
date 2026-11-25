package cl.grupo5.proyectominecraft.mundos;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/mundos/{id}/portales")
public class PortalController {
  private final MundoService mundos;

  public PortalController(MundoService mundos) {
    this.mundos = mundos;
  }

  @GetMapping
  public ResponseEntity<?> listar(@PathVariable String id, HttpSession s) throws Exception {
    var m = gestionable(id, s);
    if (m == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok(PortalService.portales(m));
  }

  @PostMapping
  public ResponseEntity<?> crear(@PathVariable String id, @RequestBody Portal body,
                                 @RequestParam(defaultValue = "false") boolean pareado, HttpSession s) throws Exception {
    var m = gestionable(id, s);
    if (m == null) return ResponseEntity.notFound().build();
    try {
      var p = PortalService.agregar(m, body);
      if (pareado) PortalService.crearPareado(m, p.getId());
      mundos.guardarPortales(m);
      return ResponseEntity.status(HttpStatus.CREATED).body(p);
    } catch (MundoValidationException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PutMapping("/{pid}")
  public ResponseEntity<?> actualizar(@PathVariable String id, @PathVariable String pid, @RequestBody Portal body,
                                      HttpSession s) throws Exception {
    var m = gestionable(id, s);
    if (m == null || PortalService.buscar(m, pid) == null) return ResponseEntity.notFound().build();
    try {
      var p = PortalService.actualizar(m, pid, body);
      mundos.guardarPortales(m);
      return ResponseEntity.ok(p);
    } catch (MundoValidationException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @DeleteMapping("/{pid}")
  public ResponseEntity<?> eliminar(@PathVariable String id, @PathVariable String pid, HttpSession s) throws Exception {
    var m = gestionable(id, s);
    if (m == null || PortalService.buscar(m, pid) == null) return ResponseEntity.notFound().build();
    PortalService.eliminar(m, pid);
    mundos.guardarPortales(m);
    return ResponseEntity.noContent().build();
  }

  /** Mundo si quien pide es dueño o admin; si no, null (la API responde 404 sin revelar que existe). */
  private Mundo gestionable(String id, HttpSession s) throws Exception {
    var m = mundos.get(id);
    return MundoService.puedeGestionar(m, (String) s.getAttribute("uid"), (String) s.getAttribute("rol")) ? m : null;
  }
}
