package cl.grupo5.proyectominecraft.mundos;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/nether")
public class NetherController {

  @GetMapping("/convertir")
  public ResponseEntity<?> convertir(@RequestParam String dimension, @RequestParam int x,
                                     @RequestParam int y, @RequestParam int z) {
    try {
      return ResponseEntity.ok(NetherCalculator.convertir(NetherCalculator.dimension(dimension), x, y, z));
    } catch (MundoValidationException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }
}
