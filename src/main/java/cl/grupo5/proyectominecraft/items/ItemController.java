package cl.grupo5.proyectominecraft.items;

import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/items")
public class ItemController {
  private final ItemService service;
  public ItemController(ItemService service) { this.service = service; }

  @GetMapping
  public ResponseEntity<?> list(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) Boolean esMateriaPrima) throws Exception {
    return ResponseEntity.ok(service.list(q, esMateriaPrima));
  }

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody Item item) throws Exception {
    try {
      return ResponseEntity.ok(service.create(item));
    } catch (ItemAlreadyExistsException e) {
      return ResponseEntity.status(409).body(e.getMessage());
    } catch (RecipeValidationException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable String id, @Valid @RequestBody Item item) throws Exception {
    try {
      var updated = service.update(id, item);
      if (updated == null) return ResponseEntity.notFound().build();
      return ResponseEntity.ok(updated);
    } catch (RecipeValidationException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable String id) throws Exception {
    if (!service.delete(id)) return ResponseEntity.notFound().build();
    return ResponseEntity.noContent().build();
  }
}
