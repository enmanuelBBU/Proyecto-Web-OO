package cl.grupo5.proyectominecraft.model3d;

import cl.grupo5.proyectominecraft.items.ItemService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class WebModel3DController {
  private final ItemService items;

  public WebModel3DController(ItemService items) {
    this.items = items;
  }

  @GetMapping("/modelo-3d")
  public String modelo(Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    m.addAttribute("items", items.list(null, null));
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "modelo-3d";
  }
}