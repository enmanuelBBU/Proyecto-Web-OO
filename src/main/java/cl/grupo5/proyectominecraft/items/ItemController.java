package cl.grupo5.proyectominecraft.items;

import cl.grupo5.proyectominecraft.auditoria.AuditService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/items")
public class ItemController {
  private final ItemService service;
  private final IconSuggestionService iconSuggestionService;
  private final AuditService audit;

  public ItemController(ItemService service, IconSuggestionService iconSuggestionService, AuditService audit) {
    this.service = service;
    this.iconSuggestionService = iconSuggestionService;
    this.audit = audit;
  }

  @GetMapping
  public ResponseEntity<?> list(@RequestParam(required = false) String q,
                                 @RequestParam(required = false) Boolean esMateriaPrima) throws Exception {
    return ResponseEntity.ok(service.list(q, esMateriaPrima));
  }

  @PostMapping
  public ResponseEntity<?> create(@Valid @RequestBody Item item, HttpSession session) throws Exception {
    if (!isAdmin(session)) return ResponseEntity.status(403).build();
    try {
      var creado = service.create(item);
      audit.registrar(session, AuditService.CREAR, AuditService.ITEM, item.getId(), item.getNombre(), null);
      return ResponseEntity.ok(creado);
    } catch (ItemAlreadyExistsException e) {
      return ResponseEntity.status(409).body(e.getMessage());
    } catch (RecipeValidationException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @PutMapping("/{id}")
  public ResponseEntity<?> update(@PathVariable String id, @Valid @RequestBody Item item, HttpSession session) throws Exception {
    if (!isAdmin(session)) return ResponseEntity.status(403).build();
    try {
      var updated = service.update(id, item);
      if (updated == null) return ResponseEntity.notFound().build();
      audit.registrar(session, AuditService.EDITAR, AuditService.ITEM, id, item.getNombre(),
          "HORNO".equals(item.getTipoReceta()) ? "Receta: HORNO" : null);
      return ResponseEntity.ok(updated);
    } catch (RecipeValidationException e) {
      return ResponseEntity.badRequest().body(e.getMessage());
    }
  }

  @DeleteMapping("/{id}")
  public ResponseEntity<?> delete(@PathVariable String id, HttpSession session) throws Exception {
    if (!isAdmin(session)) return ResponseEntity.status(403).build();
    var existente = service.get(id);
    if (!service.delete(id)) return ResponseEntity.notFound().build();
    audit.registrar(session, AuditService.ELIMINAR, AuditService.ITEM, id,
        existente != null ? existente.getNombre() : id, null);
    return ResponseEntity.noContent().build();
  }

  private static boolean isAdmin(HttpSession session) {
    return "ADMIN".equals(session.getAttribute("rol"));
  }

  @GetMapping("/sugerencias-icono")
  public ResponseEntity<?> sugerenciasIcono(@RequestParam String nombre) {
    return ResponseEntity.ok(iconSuggestionService.suggest(nombre));
  }
}
