package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.Ingrediente;
import cl.grupo5.proyectominecraft.items.Item;
import cl.grupo5.proyectominecraft.items.ItemService;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class CraftingCalculatorService {
  private final Firestore db;
  private final ItemService items;
  private final InventoryService inventory;

  public CraftingCalculatorService(Firestore db, ItemService items, InventoryService inventory) {
    this.db = db;
    this.items = items;
    this.inventory = inventory;
  }

  public CraftingPlan plan(String uid, String targetItemId, int cantidad) throws Exception {
    if (cantidad <= 0) throw new InventoryException("La cantidad debe ser mayor a cero.");
    var target = items.get(targetItemId);
    if (target == null) throw new InventoryException("El ítem no existe en el catálogo.");
    if (isRaw(target)) throw new InventoryException("Las materias primas no se craftean.");
    var requirements = expandRequirements(targetItemId, cantidad, items::get);
    return toPlan(targetItemId, cantidad, requirements, inventory.get(uid), this::nombreDe);
  }

  public CraftingPlan craft(String uid, String targetItemId, int cantidad) throws Exception {
    var plan = plan(uid, targetItemId, cantidad);
    if (!plan.isCraftable()) throw new InventoryException("No tienes suficientes materiales.");
    var ref = db.collection("inventarios").document(uid);
    try {
      db.runTransaction(tx -> {
        var current = InventoryService.readItems(tx.get(ref).get());
        for (var m : plan.getMateriales()) {
          if (current.getOrDefault(m.getItemId(), 0L) < m.getRequerido()) {
            throw new InventoryException("No tienes suficientes materiales.");
          }
        }
        var updated = new LinkedHashMap<>(current);
        for (var m : plan.getMateriales()) {
          long next = updated.getOrDefault(m.getItemId(), 0L) - m.getRequerido();
          if (next <= 0) updated.remove(m.getItemId());
          else updated.put(m.getItemId(), next);
        }
        updated.merge(targetItemId, (long) cantidad, Long::sum);
        tx.set(ref, Map.of("items", updated));
        return null;
      }).get();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof InventoryException ie) throw ie;
      throw e;
    }
    return plan;
  }

  static boolean isRaw(Item item) {
    return item.isEsMateriaPrima()
        || item.getIngredientesParaCalculo() == null
        || item.getIngredientesParaCalculo().isEmpty();
  }

  static Map<String, Long> expandRequirements(String targetItemId, int cantidad, RecetaFuente fuente) throws Exception {
    var raw = new LinkedHashMap<String, Long>();
    var pila = new ArrayDeque<IngredienteCantidad>();
    pila.push(new IngredienteCantidad(targetItemId, cantidad));
    int expansiones = 0;
    while (!pila.isEmpty()) {
      if (++expansiones > 1000) throw new InventoryException("La receta excede el límite de expansión.");
      var actual = pila.pop();
      var item = fuente.receta(actual.itemId());
      if (item == null) throw new InventoryException("El ítem no existe en el catálogo.");
      var ingredientes = item.getIngredientesParaCalculo();
      if (isRaw(item)) {
        raw.merge(actual.itemId(), (long) actual.cantidad(), Long::sum);
      } else {
        for (var ing : ingredientes) {
          pila.push(new IngredienteCantidad(ing.getItemId(), actual.cantidad() * ing.getCantidad()));
        }
      }
    }
    return raw;
  }

  static CraftingPlan toPlan(String targetItemId, int cantidad, Map<String, Long> requirements,
                             Map<String, Long> stock, NombreFuente nombres) {
    var plan = new CraftingPlan();
    plan.setTargetItemId(targetItemId);
    plan.setCantidad(cantidad);
    var filas = requirements.entrySet().stream().map(e -> {
      var fila = new MaterialRequirement();
      fila.setItemId(e.getKey());
      fila.setNombre(nombres.nombre(e.getKey()));
      fila.setRequerido(e.getValue());
      fila.setDisponible(stock.getOrDefault(e.getKey(), 0L));
      fila.setFaltante(Math.max(0, fila.getRequerido() - fila.getDisponible()));
      return fila;
    }).toList();
    plan.setMateriales(filas);
    plan.setCraftable(filas.stream().allMatch(f -> f.getFaltante() == 0));
    return plan;
  }

  private String nombreDe(String itemId) {
    try {
      var item = items.get(itemId);
      return item != null && item.getNombre() != null ? item.getNombre() : itemId;
    } catch (Exception e) {
      return itemId;
    }
  }

  interface RecetaFuente {
    Item receta(String itemId) throws Exception;
  }

  interface NombreFuente {
    String nombre(String itemId);
  }

  record IngredienteCantidad(String itemId, int cantidad) {}
}
