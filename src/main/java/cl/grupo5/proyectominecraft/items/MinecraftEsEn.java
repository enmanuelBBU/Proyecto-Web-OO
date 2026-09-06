package cl.grupo5.proyectominecraft.items;

import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Map;
import java.util.Set;

@Component
public class MinecraftEsEn {
  private static final Set<String> STOPWORDS = Set.of("de", "la", "el", "los", "las");

  private static final Map<String, String> DICTIONARY = Map.ofEntries(
      Map.entry("roble", "oak"),
      Map.entry("madera", "wood"),
      Map.entry("palo", "stick"),
      Map.entry("piedra", "stone"),
      Map.entry("roca", "cobblestone"),
      Map.entry("carbon", "coal"),
      Map.entry("lingote", "ingot"),
      Map.entry("hierro", "iron"),
      Map.entry("oro", "gold"),
      Map.entry("diamante", "diamond"),
      Map.entry("esmeralda", "emerald"),
      Map.entry("lapislazuli", "lapis_lazuli"),
      Map.entry("cobre", "copper"),
      Map.entry("cuarzo", "quartz"),
      Map.entry("lana", "wool"),
      Map.entry("cuero", "leather"),
      Map.entry("arena", "sand"),
      Map.entry("grava", "gravel"),
      Map.entry("arcilla", "clay"),
      Map.entry("vidrio", "glass"),
      Map.entry("obsidiana", "obsidian"),
      Map.entry("trigo", "wheat"),
      Map.entry("semillas", "seeds"),
      Map.entry("semilla", "seeds"),
      Map.entry("ladrillo", "brick"),
      Map.entry("hueso", "bone"),
      Map.entry("cuerda", "string"),
      Map.entry("pluma", "feather"),
      Map.entry("polvora", "gunpowder"),
      Map.entry("perla", "pearl"),
      Map.entry("papel", "paper"),
      Map.entry("tablones", "planks"),
      Map.entry("tablon", "plank"),
      Map.entry("mesa", "table"),
      Map.entry("crafteo", "crafting"),
      Map.entry("horno", "furnace"),
      Map.entry("cofre", "chest"),
      Map.entry("antorcha", "torch"),
      Map.entry("puerta", "door"),
      Map.entry("escalera", "stairs"),
      Map.entry("cama", "bed"),
      Map.entry("yunque", "anvil"),
      Map.entry("libreria", "bookshelf"),
      Map.entry("pico", "pickaxe"),
      Map.entry("hacha", "axe"),
      Map.entry("pala", "shovel"),
      Map.entry("espada", "sword"),
      Map.entry("azada", "hoe")
  );

  public String translate(String nombre) {
    if (nombre == null || nombre.isBlank()) return "";
    var normalized = Accents.strip(nombre).toLowerCase();
    var tokens = normalized.split("[^a-z0-9]+");
    var out = new ArrayList<String>();
    for (var token : tokens) {
      if (token.isBlank() || STOPWORDS.contains(token)) continue;
      out.add(DICTIONARY.getOrDefault(token, token));
    }
    return String.join(" ", out);
  }
}
