package cl.grupo5.proyectominecraft.items;

import java.text.Normalizer;
import java.util.regex.Pattern;

final class Accents {
  private static final Pattern MARKS = Pattern.compile("\\p{M}");

  private Accents() {}

  static String strip(String s) {
    return MARKS.matcher(Normalizer.normalize(s, Normalizer.Form.NFD)).replaceAll("");
  }
}
