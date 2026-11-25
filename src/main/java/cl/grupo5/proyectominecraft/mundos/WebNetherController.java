package cl.grupo5.proyectominecraft.mundos;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

@Controller
public class WebNetherController {

  @GetMapping("/calculadora-nether")
  public String view(@RequestParam(required = false) String dimension,
                     @RequestParam(required = false) Integer x,
                     @RequestParam(required = false) Integer y,
                     @RequestParam(required = false) Integer z,
                     Model m, HttpSession s) {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    exponerConversion(m, dimension, x, y, z);
    return "calculadora-nether";
  }

  /**
   * Deja en el modelo el formulario (netherDimension/X/Y/Z) y, si vienen todos los datos,
   * el resultado ({@code conversion}) o el error ({@code netherError}). Lo reutiliza el detalle de mundo.
   */
  static void exponerConversion(Model m, String dimension, Integer x, Integer y, Integer z) {
    m.addAttribute("netherDimension", dimension == null || dimension.isBlank() ? Dimension.OVERWORLD.name() : dimension.trim().toUpperCase());
    m.addAttribute("netherX", x);
    m.addAttribute("netherY", y);
    m.addAttribute("netherZ", z);
    m.addAttribute("dimensiones", Dimension.values());
    if (dimension == null || dimension.isBlank() || x == null || y == null || z == null) return;
    try {
      m.addAttribute("conversion", NetherCalculator.convertir(NetherCalculator.dimension(dimension), x, y, z));
    } catch (MundoValidationException e) {
      m.addAttribute("netherError", e.getMessage());
    }
  }
}
