package cl.grupo5.proyectominecraft.perfil;

import cl.grupo5.proyectominecraft.auditoria.AuditService;
import cl.grupo5.proyectominecraft.auth.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebProfileController {
  private final UserProfileService service;
  private final AuthService auth;
  private final AuditService audit;

  public WebProfileController(UserProfileService service, AuthService auth, AuditService audit) {
    this.service = service;
    this.auth = auth;
    this.audit = audit;
  }

  @GetMapping("/perfil")
  public String show(Model m, HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    var p = service.get(uid);
    if (p == null) {
      p = new UserProfile();
      p.setNombre(String.valueOf(s.getAttribute("email")));
      p.setEmail(String.valueOf(s.getAttribute("email")));
    }
    m.addAttribute("p", p);
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "perfil";
  }

  @PostMapping("/perfil")
  public String save(@RequestParam String nombre, @RequestParam String email, HttpSession s,
                     RedirectAttributes ra) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    var existing = service.get(uid);
    var p = new UserProfile();
    p.setNombre(nombre);
    p.setEmail(email);
    p.setRol(existing != null ? existing.getRol() : "USUARIO");
    service.save(uid, p);
    audit.registrar(s, AuditService.EDITAR, AuditService.USUARIO, uid, email, null);
    ra.addFlashAttribute("toastSuccess", "Perfil actualizado correctamente");
    return "redirect:/perfil";
  }

  @PostMapping("/perfil/eliminar")
  public String eliminar(HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    var perfil = service.get(uid);
    try {
      auth.deleteUser(uid);
      service.delete(uid);
      audit.registrar(s, AuditService.ELIMINAR, AuditService.USUARIO, uid,
          perfil != null ? perfil.getEmail() : (String) s.getAttribute("email"), null);
    } finally {
      s.invalidate();
    }
    return "redirect:/login";
  }
}
