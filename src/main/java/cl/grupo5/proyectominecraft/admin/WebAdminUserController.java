package cl.grupo5.proyectominecraft.admin;

import cl.grupo5.proyectominecraft.auth.AuthService;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.Set;

@Controller
@RequestMapping("/admin/usuarios")
public class WebAdminUserController {
  private static final Set<String> ROLES_VALIDOS = Set.of("USUARIO", "ADMIN");

  private final UserProfileService service;
  private final AuthService auth;

  public WebAdminUserController(UserProfileService service, AuthService auth) {
    this.service = service;
    this.auth = auth;
  }

  @GetMapping
  public String list(Model m, HttpSession s) throws Exception {
    var guard = guardAdmin(s);
    if (guard != null) return guard;
    m.addAttribute("usuarios", service.list());
    return "admin-usuarios";
  }

  @GetMapping("/{uid}/edit")
  public String edit(@PathVariable String uid, Model m, HttpSession s) throws Exception {
    var guard = guardAdmin(s);
    if (guard != null) return guard;
    if (uid.equals(s.getAttribute("uid"))) return "redirect:/admin/usuarios";
    var target = service.get(uid);
    if (target == null) return "redirect:/admin/usuarios";
    m.addAttribute("usuario", target);
    return "admin-usuario-edit";
  }

  @PostMapping("/{uid}/update")
  public String update(@PathVariable String uid, @RequestParam String nombre, @RequestParam String email,
                       @RequestParam String rol, Model m, HttpSession s) throws Exception {
    var guard = guardAdmin(s);
    if (guard != null) return guard;
    if (uid.equals(s.getAttribute("uid"))) return "redirect:/admin/usuarios";
    var target = service.get(uid);
    if (target == null) return "redirect:/admin/usuarios";
    if (nombre == null || nombre.isBlank()) {
      m.addAttribute("usuario", target);
      m.addAttribute("error", "El nombre es obligatorio.");
      return "admin-usuario-edit";
    }
    if (email == null || !email.matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
      m.addAttribute("usuario", target);
      m.addAttribute("error", "El email no es válido.");
      return "admin-usuario-edit";
    }
    if (!ROLES_VALIDOS.contains(rol)) {
      m.addAttribute("usuario", target);
      m.addAttribute("error", "Rol inválido.");
      return "admin-usuario-edit";
    }
    target.setNombre(nombre);
    target.setEmail(email);
    target.setRol(rol);
    service.save(uid, target);
    return "redirect:/admin/usuarios";
  }

  @PostMapping("/{uid}/delete")
  public String delete(@PathVariable String uid, HttpSession s) throws Exception {
    var guard = guardAdmin(s);
    if (guard != null) return guard;
    if (uid.equals(s.getAttribute("uid"))) return "redirect:/admin/usuarios";
    auth.deleteUser(uid);
    service.delete(uid);
    return "redirect:/admin/usuarios";
  }

  private String guardAdmin(HttpSession s) {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    if (!"ADMIN".equals(s.getAttribute("rol"))) return "redirect:/items";
    return null;
  }
}
