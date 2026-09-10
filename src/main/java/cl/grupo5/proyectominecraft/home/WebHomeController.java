package cl.grupo5.proyectominecraft.home;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebHomeController {

  @GetMapping("/")
  public String root() {
    return "redirect:/inicio";
  }

  @GetMapping("/inicio")
  public String inicio(Model m, HttpSession s) {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "inicio";
  }
}
