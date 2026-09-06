package cl.grupo5.proyectominecraft.items;

import java.util.regex.Pattern;

public final class Slugs {
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private Slugs() {}

  public static String slugify(String nombre) {
    String lower = Accents.strip(nombre).toLowerCase().trim();
    return WHITESPACE.matcher(lower).replaceAll("_");
  }
}
