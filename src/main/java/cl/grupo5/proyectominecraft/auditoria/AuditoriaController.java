package cl.grupo5.proyectominecraft.auditoria;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Protegido por AdminAuthInterceptor (/api/admin/**). */
@RestController
@RequestMapping("/api/admin/auditoria")
public class AuditoriaController {
  private final AuditService audit;

  public AuditoriaController(AuditService audit) {
    this.audit = audit;
  }

  @GetMapping
  public ResponseEntity<?> list(@RequestParam(required = false) String entidad,
                                @RequestParam(required = false) String accion,
                                @RequestParam(required = false) String q) throws Exception {
    return ResponseEntity.ok(audit.listar(entidad, accion, q));
  }
}
