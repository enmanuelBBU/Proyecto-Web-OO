package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.Item;
import cl.grupo5.proyectominecraft.items.ItemService;
import cl.grupo5.proyectominecraft.proyectos.Proyecto;
import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Service
public class CraftingCalculatorService {
  static final int LIMITE_EXPANSIONES = 1000;
  static final String SIN_MATERIALES = "No tienes suficientes materiales.";

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
    var plan = armarPlan(targetItemId, cantidad, items::get, inventory.get(uid));
    plan.setTipoReceta(ItemService.tipoRecetaDe(target));
    return plan;
  }

  public CraftingPlan craft(String uid, String targetItemId, int cantidad) throws Exception {
    var plan = plan(uid, targetItemId, cantidad);
    if (!plan.isCraftable()) throw new InventoryException(SIN_MATERIALES);
    var registro = HistorialCrafteoService.crearRegistro(uid, items.get(targetItemId), plan, Instant.now().toString());
    var ref = db.collection("inventarios").document(uid);
    var registroRef = db.collection(HistorialCrafteoService.COLECCION).document();
    try {
      db.runTransaction(tx -> {
        var current = InventoryService.readItems(tx.get(ref).get());
        var updated = aplicarConsumo(current, plan.getConsumidos(), targetItemId, cantidad);
        tx.set(ref, Map.of("items", updated));
        tx.create(registroRef, registro);
        return null;
      }).get();
    } catch (ExecutionException e) {
      if (e.getCause() instanceof InventoryException ie) throw ie;
      throw e;
    }
    return plan;
  }

  /** Faltantes de un proyecto contra el inventario de {@code uid}. El catálogo se lee una sola vez. */
  public ResultadoFaltantes faltantesProyecto(Proyecto p, String uid) throws Exception {
    var catalogo = new HashMap<String, Item>();
    for (var it : items.list(null, null)) catalogo.put(it.getId(), it);
    return calcular(demandasDe(p), catalogo::get, new LinkedHashMap<>(inventory.get(uid)));
  }

  public static List<Demanda> demandasDe(Proyecto p) {
    var demandas = new ArrayList<Demanda>();
    if (p == null || p.getItemsRequeridos() == null) return demandas;
    for (var ir : p.getItemsRequeridos()) {
      if (ir == null || ir.getItemId() == null || ir.getItemId().isBlank() || ir.getCantidad() <= 0) continue;
      demandas.add(new Demanda(ir.getItemId(), ir.getCantidad()));
    }
    return demandas;
  }

  public static boolean isRaw(Item item) {
    return item.isEsMateriaPrima()
        || item.getIngredientesParaCalculo() == null
        || item.getIngredientesParaCalculo().isEmpty();
  }

  // ---- Motor ----

  /**
   * Desglosa las demandas descontando primero el inventario, incluidos los intermedios.
   * {@code stock} se modifica: es la copia compartida por todo el cálculo.
   */
  public static ResultadoFaltantes calcular(List<Demanda> demandas, RecetaFuente fuente, Map<String, Long> stock)
      throws Exception {
    var ctx = new Contexto(fuente, stock);
    for (var d : demandas) ctx.arbol.add(ctx.expandir(d.itemId(), d.cantidad(), true));
    return ctx.resultado();
  }

  /** Igual que {@link #calcular} pero el objetivo se fabrica entero: no se toma su propio stock. */
  static ResultadoFaltantes calcularCrafteo(String targetId, long cantidad, RecetaFuente fuente, Map<String, Long> stock)
      throws Exception {
    var ctx = new Contexto(fuente, stock);
    ctx.arbol.add(ctx.expandir(targetId, cantidad, false));
    return ctx.resultado();
  }

  static CraftingPlan armarPlan(String targetId, int cantidad, RecetaFuente fuente, Map<String, Long> stock)
      throws Exception {
    var original = new LinkedHashMap<>(stock);
    var r = calcularCrafteo(targetId, cantidad, fuente, new LinkedHashMap<>(stock));
    var plan = new CraftingPlan();
    plan.setTargetItemId(targetId);
    plan.setCantidad(cantidad);
    plan.setArbol(r.getArbol());
    plan.setConsumidos(r.getConsumidosDeInventario());
    plan.setFaltantes(r.getFaltantes());
    plan.setUsaIntermedios(r.isUsaIntermedios());
    plan.setCraftable(r.getFaltantes().isEmpty());

    var nombres = new LinkedHashMap<String, String>();
    var usados = new HashMap<String, Long>();
    var faltan = new HashMap<String, Long>();
    for (var c : r.getConsumidosDeInventario()) { nombres.putIfAbsent(c.getItemId(), c.getNombre()); usados.put(c.getItemId(), c.getCantidad()); }
    for (var f : r.getFaltantes()) { nombres.putIfAbsent(f.getItemId(), f.getNombre()); faltan.put(f.getItemId(), f.getCantidad()); }
    var filas = new ArrayList<MaterialRequirement>();
    for (var e : nombres.entrySet()) {
      var fila = new MaterialRequirement();
      fila.setItemId(e.getKey());
      fila.setNombre(e.getValue());
      fila.setFaltante(faltan.getOrDefault(e.getKey(), 0L));
      fila.setRequerido(usados.getOrDefault(e.getKey(), 0L) + fila.getFaltante());
      fila.setDisponible(original.getOrDefault(e.getKey(), 0L));
      filas.add(fila);
    }
    plan.setMateriales(filas);
    return plan;
  }

  /** Descuenta {@code consumidos} de {@code actual} (revalidando) y suma el objetivo. No modifica {@code actual}. */
  static Map<String, Long> aplicarConsumo(Map<String, Long> actual, List<MaterialCantidad> consumidos,
                                          String targetId, long cantidad) {
    var updated = new LinkedHashMap<>(actual);
    for (var c : consumidos) {
      long disponible = updated.getOrDefault(c.getItemId(), 0L);
      if (disponible < c.getCantidad()) throw new InventoryException(SIN_MATERIALES);
      long next = disponible - c.getCantidad();
      if (next <= 0) updated.remove(c.getItemId());
      else updated.put(c.getItemId(), next);
    }
    updated.merge(targetId, cantidad, Long::sum);
    return updated;
  }

  private static final class Contexto {
    final RecetaFuente fuente;
    final Map<String, Long> stock;
    final List<NodoCrafteo> arbol = new ArrayList<>();
    final Map<String, Long> consumidos = new LinkedHashMap<>();
    final Map<String, Long> faltantes = new LinkedHashMap<>();
    final Map<String, String> nombres = new HashMap<>();
    boolean usaIntermedios;
    int expansiones;

    Contexto(RecetaFuente fuente, Map<String, Long> stock) {
      this.fuente = fuente;
      this.stock = stock;
    }

    NodoCrafteo expandir(String itemId, long necesario, boolean usarStock) throws Exception {
      if (++expansiones > LIMITE_EXPANSIONES) throw new InventoryException("La receta excede el límite de expansión.");
      var item = fuente.receta(itemId);
      if (item == null) throw new InventoryException("El ítem '" + itemId + "' no existe en el catálogo.");
      var nombre = item.getNombre() != null ? item.getNombre() : itemId;
      nombres.put(itemId, nombre);

      var nodo = new NodoCrafteo();
      nodo.setItemId(itemId);
      nodo.setNombre(nombre);
      nodo.setRequerido(necesario);

      long usar = 0;
      if (usarStock) {
        usar = Math.min(Math.max(stock.getOrDefault(itemId, 0L), 0L), necesario);
        if (usar > 0) {
          long queda = stock.get(itemId) - usar;
          if (queda <= 0) stock.remove(itemId);
          else stock.put(itemId, queda);
          consumidos.merge(itemId, usar, Long::sum);
        }
      }
      nodo.setUsadoDeInventario(usar);
      long resto = necesario - usar;

      if (isRaw(item)) {
        nodo.setTipo(NodoCrafteo.MATERIA_PRIMA);
        nodo.setFaltante(resto);
        if (resto > 0) faltantes.merge(itemId, resto, Long::sum);
        return nodo;
      }
      nodo.setTipo(ItemService.tipoRecetaDe(item).equals(NodoCrafteo.HORNO) ? NodoCrafteo.HORNO : NodoCrafteo.CRAFTEO);
      if (usar > 0) usaIntermedios = true;
      nodo.setAFabricar(resto);
      if (resto > 0) {
        for (var ing : item.getIngredientesParaCalculo()) {
          nodo.getHijos().add(expandir(ing.getItemId(), resto * ing.getCantidad(), true));
        }
      }
      return nodo;
    }

    ResultadoFaltantes resultado() {
      var r = new ResultadoFaltantes();
      r.setArbol(arbol);
      r.setConsumidosDeInventario(lista(consumidos));
      r.setFaltantes(lista(faltantes));
      r.setUsaIntermedios(usaIntermedios);
      return r;
    }

    private List<MaterialCantidad> lista(Map<String, Long> m) {
      var out = new ArrayList<MaterialCantidad>();
      for (var e : m.entrySet()) out.add(new MaterialCantidad(e.getKey(), nombres.get(e.getKey()), e.getValue()));
      return out;
    }
  }

  @FunctionalInterface
  public interface RecetaFuente {
    Item receta(String itemId) throws Exception;
  }
}
