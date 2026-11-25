package cl.grupo5.proyectominecraft.auditoria;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class WebAuditoriaController {
  private final AuditService audit;

  public WebAuditoriaController(AuditService audit) {
    this.audit = audit;
  }

  @GetMapping("/admin/auditoria")
  public String view(@RequestParam(required = false) String entidad,
                     @RequestParam(required = false) String accion,
                     @RequestParam(required = false) String q,
                     Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    if (!"ADMIN".equals(s.getAttribute("rol"))) return "redirect:/items";
    m.addAttribute("eventos", audit.listar(entidad, accion, q));
    m.addAttribute("entidad", entidad);
    m.addAttribute("accion", accion);
    m.addAttribute("q", q);
    m.addAttribute("entidades", List.of(AuditService.ITEM, AuditService.PROYECTO, AuditService.USUARIO, AuditService.COMENTARIO));
    m.addAttribute("acciones", List.of(AuditService.CREAR, AuditService.EDITAR, AuditService.ELIMINAR));
    m.addAttribute("isAdmin", true);
    return "admin-auditoria";
  }
}
