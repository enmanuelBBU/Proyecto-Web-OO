package cl.grupo5.proyectominecraft.inventario;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/crafteos")
public class HistorialCrafteoController {
  private final HistorialCrafteoService historial;

  public HistorialCrafteoController(HistorialCrafteoService historial) {
    this.historial = historial;
  }

  @GetMapping
  public ResponseEntity<?> list(HttpSession s) throws Exception {
    return ResponseEntity.ok(historial.listar((String) s.getAttribute("uid")));
  }
}
