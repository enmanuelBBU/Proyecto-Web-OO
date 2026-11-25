package cl.grupo5.proyectominecraft.home;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebHomeController {

  private final DashboardService dashboard;

  public WebHomeController(DashboardService dashboard) {
    this.dashboard = dashboard;
  }

  @GetMapping("/")
  public String root() {
    return "redirect:/inicio";
  }

  @GetMapping("/inicio")
  public String inicio(Model m, HttpSession s) {
    Object uid = s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    String rol = (String) s.getAttribute("rol");
    m.addAttribute("isAdmin", "ADMIN".equals(rol));
    m.addAttribute("resumen", dashboard.resumen(uid.toString(), rol));
    return "inicio";
  }
}
