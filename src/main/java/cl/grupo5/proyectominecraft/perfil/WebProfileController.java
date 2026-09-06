package cl.grupo5.proyectominecraft.perfil;

import cl.grupo5.proyectominecraft.auth.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebProfileController {
  private final UserProfileService service;
  private final AuthService auth;

  public WebProfileController(UserProfileService service, AuthService auth) {
    this.service = service;
    this.auth = auth;
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
  public String save(@RequestParam String nombre, @RequestParam String email, HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    var existing = service.get(uid);
    var p = new UserProfile();
    p.setNombre(nombre);
    p.setEmail(email);
    p.setRol(existing != null ? existing.getRol() : "USUARIO");
    service.save(uid, p);
    return "redirect:/perfil";
  }

  @PostMapping("/perfil/eliminar")
  public String eliminar(HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    try {
      auth.deleteUser(uid);
      service.delete(uid);
    } finally {
      s.invalidate();
    }
    return "redirect:/login";
  }
}
