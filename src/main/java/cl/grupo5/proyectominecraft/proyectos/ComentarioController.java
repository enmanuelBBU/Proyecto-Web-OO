package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.auditoria.AuditService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/proyectos/{id}/comentarios")
public class ComentarioController {
  private final ProyectoService proyectos;
  private final ComentarioService comentarios;
  private final AuditService audit;

  public ComentarioController(ProyectoService proyectos, ComentarioService comentarios, AuditService audit) {
    this.proyectos = proyectos;
    this.comentarios = comentarios;
    this.audit = audit;
  }

  @GetMapping
  public ResponseEntity<?> listar(@PathVariable String id, HttpSession s) throws Exception {
    if (proyectos.getVisible(id, uid(s), rol(s)) == null) return ResponseEntity.notFound().build();
    return ResponseEntity.ok(comentarios.listar(id));
  }

  @PostMapping
  public ResponseEntity<?> crear(@PathVariable String id, @RequestBody(required = false) Map<String, String> body,
                                 HttpSession s) throws Exception {
    if (proyectos.getVisible(id, uid(s), rol(s)) == null) return ResponseEntity.notFound().build();
    try {
      return ResponseEntity.status(HttpStatus.CREATED).body(comentarios.crear(id, uid(s), nombre(s), texto(body)));
    } catch (ComentarioValidationException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PutMapping("/{cid}")
  public ResponseEntity<?> editar(@PathVariable String id, @PathVariable String cid,
                                  @RequestBody(required = false) Map<String, String> body, HttpSession s) throws Exception {
    if (proyectos.getVisible(id, uid(s), rol(s)) == null) return ResponseEntity.notFound().build();
    var c = comentarios.get(id, cid);
    if (c == null) return ResponseEntity.notFound().build();
    if (!ComentarioService.puedeEditar(c, uid(s))) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(WebComentarioController.SOLO_AUTOR);
    }
    try {
      return ResponseEntity.ok(comentarios.editar(id, c, texto(body)));
    } catch (ComentarioValidationException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @DeleteMapping("/{cid}")
  public ResponseEntity<?> eliminar(@PathVariable String id, @PathVariable String cid, HttpSession s) throws Exception {
    var p = proyectos.getVisible(id, uid(s), rol(s));
    if (p == null) return ResponseEntity.notFound().build();
    var c = comentarios.get(id, cid);
    if (c == null) return ResponseEntity.notFound().build();
    if (!ComentarioService.puedeBorrar(c, p, uid(s), rol(s))) {
      return ResponseEntity.status(HttpStatus.FORBIDDEN).body(WebComentarioController.SIN_PERMISO_BORRAR);
    }
    comentarios.eliminar(id, cid);
    if (ComentarioService.esModeracion(c, uid(s))) {
      audit.registrar(s, AuditService.ELIMINAR, AuditService.COMENTARIO, cid, p.getNombre(),
          WebComentarioController.detalleModeracion(c));
    }
    return ResponseEntity.noContent().build();
  }

  private static String texto(Map<String, String> body) {
    return body == null ? null : body.get("texto");
  }

  private static String uid(HttpSession s) {
    return (String) s.getAttribute("uid");
  }

  private static String rol(HttpSession s) {
    return (String) s.getAttribute("rol");
  }

  private static String nombre(HttpSession s) {
    String nombre = (String) s.getAttribute("nombre");
    return nombre != null ? nombre : (String) s.getAttribute("usuario");
  }
}
