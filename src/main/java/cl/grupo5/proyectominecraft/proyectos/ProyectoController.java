package cl.grupo5.proyectominecraft.proyectos;

import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/api/proyectos")
public class ProyectoController {
  private final ProyectoService service;

  public ProyectoController(ProyectoService service) {
    this.service = service;
  }

  @GetMapping
  public ResponseEntity<?> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String estado,
      @RequestParam(required = false) String creadorUid) throws Exception {
    return ResponseEntity.ok(service.list(q, estado, creadorUid));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable String id) throws Exception {
    var proyecto = service.get(id);
    if (proyecto == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.ok(proyecto);
  }

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody Proyecto proyecto, HttpSession session) throws Exception {
    String uid = (String) session.getAttribute("uid");
    String nombreUsuario = (String) session.getAttribute("nombre");
    if (nombreUsuario == null) {
      nombreUsuario = (String) session.getAttribute("usuario");
    }

    try {
      var creado = service.create(proyecto, uid, nombreUsuario);
      return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    } catch (ProyectoAlreadyExistsException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    } catch (ProyectoValidationException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable String id, @Valid @RequestBody Proyecto proyecto, HttpSession session) throws Exception {
    String uid = (String) session.getAttribute("uid");
    String rol = (String) session.getAttribute("rol");

    try {
      var actualizado = service.update(id, proyecto, uid, rol);
      if (actualizado == null) {
        return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(actualizado);
    } catch (ProyectoValidationException e) {
      if (e.getMessage().contains("permisos")) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
      }
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable String id, HttpSession session) throws Exception {
    String uid = (String) session.getAttribute("uid");
    String rol = (String) session.getAttribute("rol");

    try {
      boolean eliminado = service.delete(id, uid, rol);
      if (!eliminado) {
        return ResponseEntity.notFound().build();
      }
      return ResponseEntity.noContent().build();
    } catch (ProyectoValidationException e) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
  }

  @GetMapping("/{id}/estados")
  public ResponseEntity<?> getAvailableTransitions(@PathVariable String id) throws Exception {
    var proyecto = service.get(id);
    if (proyecto == null) {
      return ResponseEntity.notFound().build();
    }
    String estado = proyecto.getEstado() != null ? proyecto.getEstado() : "PLANIFICACION";
    Set<String> transiciones = EstadoProyecto.fromString(estado).getTransicionesValidas();
    return ResponseEntity.ok(Map.of("estadoActual", estado, "transiciones", transiciones));
  }

  @PostMapping("/{id}/estado")
  public ResponseEntity<?> updateState(@PathVariable String id, @RequestBody Map<String, String> body, HttpSession session) throws Exception {
    String uid = (String) session.getAttribute("uid");
    String rol = (String) session.getAttribute("rol");
    String nombre = (String) session.getAttribute("nombre");
    if (nombre == null) nombre = (String) session.getAttribute("usuario");

    String nuevoEstado = body.get("estado");
    String motivo = body.get("motivo");

    if (nuevoEstado == null || nuevoEstado.isBlank()) {
      return ResponseEntity.badRequest().body("El estado es obligatorio.");
    }

    try {
      var actualizado = service.transicionarEstado(id, nuevoEstado, motivo, uid, nombre, rol);
      if (actualizado == null) {
        return ResponseEntity.notFound().build();
      }
      return ResponseEntity.ok(actualizado);
    } catch (ProyectoValidationException e) {
      if (e.getMessage().contains("permisos")) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
      }
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
