package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.auditoria.AuditService;
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
  private final AuditService audit;

  public ProyectoController(ProyectoService service, AuditService audit) {
    this.service = service;
    this.audit = audit;
  }

  @GetMapping
  public ResponseEntity<?> list(
      @RequestParam(required = false) String q,
      @RequestParam(required = false) String estado,
      @RequestParam(required = false) String creadorUid,
      HttpSession session) throws Exception {
    return ResponseEntity.ok(service.list(q, estado, creadorUid,
        (String) session.getAttribute("uid"), (String) session.getAttribute("rol")));
  }

  @GetMapping("/{id}")
  public ResponseEntity<?> get(@PathVariable String id, HttpSession session) throws Exception {
    var proyecto = service.getVisible(id, uid(session), rol(session));
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
      audit.registrar(session, AuditService.CREAR, AuditService.PROYECTO, proyecto.getId(), proyecto.getNombre(), null);
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
      audit.registrar(session, AuditService.EDITAR, AuditService.PROYECTO, id, proyecto.getNombre(), null);
      return ResponseEntity.ok(actualizado);
    } catch (ProyectoAlreadyExistsException e) {
      return ResponseEntity.status(HttpStatus.CONFLICT).body(e.getMessage());
    } catch (ProyectoValidationException e) {
      return validacion(e, id, session);
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable String id, HttpSession session) throws Exception {
    String uid = (String) session.getAttribute("uid");
    String rol = (String) session.getAttribute("rol");

    var existente = service.getVisible(id, uid, rol);
    try {
      boolean eliminado = service.delete(id, uid, rol);
      if (!eliminado) {
        return ResponseEntity.notFound().build();
      }
      audit.registrar(session, AuditService.ELIMINAR, AuditService.PROYECTO, id,
          existente != null ? existente.getNombre() : id, null);
      return ResponseEntity.noContent().build();
    } catch (ProyectoValidationException e) {
      return validacion(e, id, session);
    }
  }

  @GetMapping("/{id}/estados")
  public ResponseEntity<?> getAvailableTransitions(@PathVariable String id, HttpSession session) throws Exception {
    var proyecto = service.getVisible(id, uid(session), rol(session));
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
      audit.registrar(session, AuditService.EDITAR, AuditService.PROYECTO, id, actualizado.getNombre(),
          AuditDetalles.transicion(actualizado));
      return ResponseEntity.ok(actualizado);
    } catch (ProyectoValidationException e) {
      return validacion(e, id, session);
    }
  }

  // ---- Miembros ----

  @GetMapping("/{id}/miembros")
  public ResponseEntity<?> miembros(@PathVariable String id, HttpSession session) throws Exception {
    var proyecto = service.getVisible(id, uid(session), rol(session));
    if (proyecto == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok(proyecto.getMiembros() != null ? proyecto.getMiembros() : java.util.List.of());
  }

  @PostMapping("/{id}/miembros")
  public ResponseEntity<?> invitar(@PathVariable String id, @RequestBody Map<String, String> body, HttpSession session) throws Exception {
    try {
      var p = service.invitar(id, body.get("email"), body.get("permiso"), uid(session), rol(session));
      if (p == null) return ResponseEntity.notFound().build();
      audit.registrar(session, AuditService.EDITAR, AuditService.PROYECTO, id, p.getNombre(),
          AuditDetalles.invitacion(body.get("email"), body.get("permiso")));
      return ResponseEntity.ok(p.getMiembros());
    } catch (ProyectoValidationException e) {
      return validacion(e, id, session);
    }
  }

  @PutMapping("/{id}/miembros/{miembroUid}")
  public ResponseEntity<?> cambiarPermiso(@PathVariable String id, @PathVariable String miembroUid,
                                          @RequestBody Map<String, String> body, HttpSession session) throws Exception {
    try {
      var p = service.cambiarPermisoMiembro(id, miembroUid, body.get("permiso"), uid(session), rol(session));
      if (p == null) return ResponseEntity.notFound().build();
      audit.registrar(session, AuditService.EDITAR, AuditService.PROYECTO, id, p.getNombre(),
          AuditDetalles.cambioPermiso(p, miembroUid, body.get("permiso")));
      return ResponseEntity.ok(p.getMiembros());
    } catch (ProyectoValidationException e) {
      return validacion(e, id, session);
    }
  }

  @DeleteMapping("/{id}/miembros/{miembroUid}")
  public ResponseEntity<?> quitarMiembro(@PathVariable String id, @PathVariable String miembroUid,
                                         HttpSession session) throws Exception {
    var antes = service.getVisible(id, uid(session), rol(session));
    try {
      var p = service.quitarMiembro(id, miembroUid, uid(session), rol(session));
      if (p == null) return ResponseEntity.notFound().build();
      audit.registrar(session, AuditService.EDITAR, AuditService.PROYECTO, id, p.getNombre(),
          AuditDetalles.quitado(antes, miembroUid));
      return ResponseEntity.noContent().build();
    } catch (ProyectoValidationException e) {
      return validacion(e, id, session);
    }
  }

  /**
   * "Sin permisos" responde 403 solo si quien pide puede ver el proyecto; si no lo ve, 404,
   * para no revelar que existe un proyecto privado ajeno.
   */
  private ResponseEntity<?> validacion(ProyectoValidationException e, String id, HttpSession session) throws Exception {
    if (e.getMessage() != null && e.getMessage().contains("permisos")) {
      if (service.getVisible(id, uid(session), rol(session)) == null) return ResponseEntity.notFound().build();
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(e.getMessage());
    }
    return ResponseEntity.badRequest().body(e.getMessage());
  }

  private static String uid(HttpSession session) {
    return (String) session.getAttribute("uid");
  }

  private static String rol(HttpSession session) {
    return (String) session.getAttribute("rol");
  }
}
