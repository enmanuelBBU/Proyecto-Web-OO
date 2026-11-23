package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.Item;
import cl.grupo5.proyectominecraft.items.ItemService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.HashMap;
import java.util.Map;

@Controller
public class WebHistorialCrafteoController {
  private final HistorialCrafteoService historial;
  private final ItemService items;

  public WebHistorialCrafteoController(HistorialCrafteoService historial, ItemService items) {
    this.historial = historial;
    this.items = items;
  }

  @GetMapping("/historial-crafteo")
  public String view(Model m, HttpSession s) throws Exception {
    String uid = (String) s.getAttribute("uid");
    if (uid == null) return "redirect:/login";
    Map<String, Item> itemMap = new HashMap<>();
    for (var it : items.list(null, null)) itemMap.put(it.getId(), it);
    m.addAttribute("registros", historial.listar(uid));
    m.addAttribute("itemMap", itemMap);
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "historial-crafteo";
  }
}
