package cl.grupo5.proyectominecraft.items;

import java.util.regex.Pattern;

public final class Slugs {
  private static final Pattern WHITESPACE = Pattern.compile("\\s+");

  private Slugs() {}

  public static String slugify(String nombre) {
    String lower = Accents.strip(nombre).toLowerCase().trim();
    String withUnderscores = WHITESPACE.matcher(lower).replaceAll("_");
    String cleaned = withUnderscores.replaceAll("[^a-z0-9_]", "");
    // Note: a nombre made up entirely of stripped punctuation (e.g. "...") would
    // slugify to an empty string; that extreme edge case is not handled here.
    return cleaned.replaceAll("_+", "_");
  }
}
