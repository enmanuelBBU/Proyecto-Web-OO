package cl.grupo5.proyectominecraft.perfil;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

@Controller
public class WebProfileController {
  private final UserProfileService service;

  public WebProfileController(UserProfileService service) {
    this.service = service;
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
    return "perfil";
  }

  @PostMapping("/perfil")
  public String save(@RequestParam String nombre, @RequestParam String email,
                     @RequestParam(defaultValue = "USUARIO") String rol, HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    var p = new UserProfile();
    p.setNombre(nombre);
    p.setEmail(email);
    p.setRol(rol);
    service.save(uid, p);
    return "redirect:/perfil";
  }
}
