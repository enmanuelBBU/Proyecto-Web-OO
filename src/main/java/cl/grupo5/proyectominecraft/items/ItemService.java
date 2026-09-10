package cl.grupo5.proyectominecraft.items;

import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;

@Service
public class ItemService {
  private final Firestore db;
  public ItemService(Firestore db) { this.db = db; }

  public List<Item> list(String q, Boolean esMateriaPrima) throws Exception {
    var docs = db.collection("items").get().get().getDocuments();
    var items = docs.stream().map(d -> {
      var it = d.toObject(Item.class);
      it.setId(d.getId());
      decorar(it);
      return it;
    }).toList();
    return filter(items, q, esMateriaPrima);
  }

  static List<Item> filter(List<Item> items, String q, Boolean esMateriaPrima) {
    var result = items;
    if (q != null && !q.isBlank()) {
      var needle = q.toLowerCase();
      result = result.stream().filter(i ->
          (i.getNombre() != null && i.getNombre().toLowerCase().contains(needle)) ||
          (i.getCategoria() != null && i.getCategoria().toLowerCase().contains(needle))
      ).toList();
    }
    if (esMateriaPrima != null) {
      result = result.stream().filter(i -> i.isEsMateriaPrima() == esMateriaPrima).toList();
    }
    return result;
  }

  public Item get(String id) throws Exception {
    var snap = db.collection("items").document(id).get().get();
    if (!snap.exists()) return null;
    var it = snap.toObject(Item.class);
    it.setId(id);
    decorar(it);
    return it;
  }

  public Item create(Item item) throws Exception {
    String id = Slugs.slugify(item.getNombre());
    var existing = db.collection("items").document(id).get().get();
    if (existing.exists()) {
      throw new ItemAlreadyExistsException(id);
    }
    validateRecetaMatriz(item);
    validarReferenciasYCiclos(id, item.getRecetaMatriz());
    item.setIngredientesParaCalculo(computeIngredientes(item.getRecetaMatriz()));
    item.setId(id);
    decorar(item);
    db.collection("items").document(id).set(item).get();
    return item;
  }

  public Item update(String id, Item item) throws Exception {
    var snap = db.collection("items").document(id).get().get();
    if (!snap.exists()) return null;
    validateRecetaMatriz(item);
    validarReferenciasYCiclos(id, item.getRecetaMatriz());
    item.setIngredientesParaCalculo(computeIngredientes(item.getRecetaMatriz()));
    item.setId(id);
    decorar(item);
    db.collection("items").document(id).set(item).get();
    return item;
  }

  public boolean delete(String id) throws Exception {
    var snap = db.collection("items").document(id).get().get();
    if (!snap.exists()) return false;
    db.collection("items").document(id).delete().get();
    return true;
  }

  static void validateRecetaMatriz(Item item) {
    var receta = item.getRecetaMatriz();
    if (receta == null || receta.isEmpty()) return;
    boolean allBlank = receta.stream().allMatch(s -> s == null || s.isBlank());
    if (allBlank) return;
    if (receta.size() != 9) {
      throw new RecipeValidationException("La receta debe tener exactamente 9 casillas.");
    }
    if (item.isEsMateriaPrima()) {
      throw new RecipeValidationException("Una materia prima no puede tener receta.");
    }
  }

  private void validarReferenciasYCiclos(String id, List<String> recetaMatriz) throws Exception {
    if (recetaMatriz == null || recetaMatriz.isEmpty()) return;
    var referenciados = recetaMatriz.stream().filter(s -> s != null && !s.isBlank()).distinct().toList();
    if (referenciados.isEmpty()) return;
    validarExistencia(referenciados, refId -> db.collection("items").document(refId).get().get().exists());
    validarCiclos(id, referenciados, refId -> {
      var snap = db.collection("items").document(refId).get().get();
      if (!snap.exists()) return List.of();
      var it = snap.toObject(Item.class);
      return it.getRecetaMatriz();
    });
  }

  static void validarExistencia(List<String> referenciados, ExistenceChecker existe) throws Exception {
    for (var id : referenciados) {
      if (!existe.existe(id)) {
        throw new RecipeValidationException("El ingrediente '" + id + "' no existe en el catálogo.");
      }
    }
  }

  static void validarCiclos(String idPropio, List<String> referenciados, RecetaLookup obtenerReceta) throws Exception {
    var visitados = new HashSet<String>();
    var pila = new ArrayDeque<>(referenciados);
    while (!pila.isEmpty()) {
      var actual = pila.pop();
      if (actual.equals(idPropio)) {
        throw new RecipeValidationException("La receta genera una dependencia circular con '" + idPropio + "'.");
      }
      if (!visitados.add(actual)) continue;
      var receta = obtenerReceta.receta(actual);
      if (receta != null) {
        for (var r : receta) {
          if (r != null && !r.isBlank()) pila.push(r);
        }
      }
    }
  }

  static List<Ingrediente> computeIngredientes(List<String> recetaMatriz) {
    if (recetaMatriz == null || recetaMatriz.isEmpty()) return List.of();
    var counts = new LinkedHashMap<String, Integer>();
    for (String slot : recetaMatriz) {
      if (slot == null || slot.isBlank()) continue;
      counts.merge(slot.trim(), 1, Integer::sum);
    }
    return counts.entrySet().stream().map(e -> {
      var ing = new Ingrediente();
      ing.setItemId(e.getKey());
      ing.setCantidad(e.getValue());
      return ing;
    }).toList();
  }

  static void decorar(Item item) {
    if (item.getTipoVisual() == null || item.getTipoVisual().isBlank()) {
      item.setTipoVisual(derivarTipoVisual(item));
    }
    item.setTextura(texturaPara(item));
  }

  static String derivarTipoVisual(Item item) {
    var nombre = norm(item.getNombre());
    var categoria = norm(item.getCategoria());
    String[] itemsPlanos = {
        "pickaxe", "sword", "axe", "shovel", "hoe", "ingot", "nugget", "diamond", "emerald",
        "apple", "stick", "book", "gem", "arrow", "bow", "helmet", "chestplate", "leggings",
        "boots", "bread", "carrot", "pearl", "skull", "cristal", "barrita", "ladrillo", "brick",
        "redstone", "vidrio", "glass", "panel", "arcilla", "clay"
    };
    for (var k : itemsPlanos) {
      if (nombre.contains(k)) return "ITEM_PLANO";
    }
    String[] bloques = {
        "stone", "piedra", "roca", "adoquin", "guijarro", "block", "bloque", "dirt", "tierra",
        "grass", "pasto", "plank", "tabla", "wood", "madera", "log", "tronco", "roble",
        "obsidian", "obsidiana", "bedrock", "cobble", "cobblestone", "sand", "arena", "gravel",
        "grava", "ore", "mineral", "wool", "lana", "crafting", "mesa", "furnace", "horno",
        "chest", "cofre", "ice", "snow", "esponja", "hongo"
    };
    for (var k : bloques) {
      if (nombre.contains(k)) return "BLOQUE";
    }
    if (categoria.contains("construccion") || categoria.contains("bloque")) return "BLOQUE";
    return "ITEM_PLANO";
  }

  static String texturaPara(Item item) {
    var nombre = norm(item.getNombre());
    if ("BLOQUE".equals(item.getTipoVisual())) {
      if (nombre.contains("grass") || nombre.contains("pasto")) return "block/grass";
      if (nombre.contains("dirt") || nombre.contains("tierra")) return "block/dirt";
      if (nombre.contains("obsidian") || nombre.contains("obsidiana")) return "block/obsidian";
      if (nombre.contains("bedrock")) return "block/bedrock";
      if (nombre.contains("crafting") || nombre.contains("mesa")) return "block/crafting";
      if (nombre.contains("sand") || nombre.contains("arena")) return "block/sand";
      if (nombre.contains("gravel") || nombre.contains("grava")) return "block/gravel";
      if (nombre.contains("wool") || nombre.contains("lana")) return "block/wool";
      if (nombre.contains("cobblestone") || nombre.contains("roca") || nombre.contains("adoquin") || nombre.contains("cobble")) return "block/cobblestone";
      if (nombre.contains("log") || nombre.contains("tronco")) return "block/log";
      if (nombre.contains("plank") || nombre.contains("tabla") || nombre.contains("wood") || nombre.contains("madera") || nombre.contains("roble")) return "block/oak_planks";
      if (nombre.contains("stone") || nombre.contains("piedra") || nombre.contains("guijarro")
          || nombre.contains("horno")) return "block/stone";
      return "block/stone";
    }
    if (nombre.contains("vidrio") || nombre.contains("cristal") || nombre.contains("glass") || nombre.contains("panel")) return "item/vidrio";
    if (nombre.contains("diamond")) return "item/diamond";
    if (nombre.contains("emerald")) return "item/emerald";
    if (nombre.contains("iron") || nombre.contains("gold") || nombre.contains("hierro")) return "item/iron_ingot";
    if (nombre.contains("apple") || nombre.contains("manzana")) return "item/apple";
    if (nombre.contains("stick") || nombre.contains("palo")) return "item/stick";
    if (nombre.contains("book") || nombre.contains("libro")) return "item/book";
    if (nombre.contains("pickaxe")) {
      if (nombre.contains("diamond")) return "item/diamond_pickaxe";
      return "item/stone_pickaxe";
    }
    if (nombre.contains("stone") || nombre.contains("piedra")) return "item/stone";
    if (nombre.contains("crafting") || nombre.contains("mesa")) return "item/crafting_table";
    return null;
  }

  static String norm(String s) {
    if (s == null) return "";
    var n = java.text.Normalizer.normalize(s.toLowerCase(), java.text.Normalizer.Form.NFD);
    return n.replaceAll("\\p{M}", "");
  }
}
