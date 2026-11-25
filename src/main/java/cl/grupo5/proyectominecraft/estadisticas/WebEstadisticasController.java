package cl.grupo5.proyectominecraft.estadisticas;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebEstadisticasController {
  private final EstadisticasService service;

  public WebEstadisticasController(EstadisticasService service) {
    this.service = service;
  }

  @GetMapping("/estadisticas")
  public String view(Model m, HttpSession s) {
    var uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    var est = service.resumen(uid, (String) s.getAttribute("rol"));
    if (est == null) est = new Estadisticas(null, null, null);
    m.addAttribute("estadisticas", est);
    m.addAttribute("totalProyectos", est.porEstado() == null ? 0
        : est.porEstado().values().stream().mapToInt(Integer::intValue).sum());
    m.addAttribute("datosGraficos", EstadisticasService.datosGraficos(est));
    return "estadisticas";
  }
}
