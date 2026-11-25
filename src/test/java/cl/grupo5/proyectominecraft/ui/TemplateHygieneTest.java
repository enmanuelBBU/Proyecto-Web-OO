package cl.grupo5.proyectominecraft.ui;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Higiene de templates: sin style="" estáticos, sin emoji en h1 y con el fragmento head. */
class TemplateHygieneTest {

  private static final Path DIR = Path.of("src/main/resources/templates");
  private static final Pattern STYLE_ESTATICO = Pattern.compile("(?<!th:)style=\"");
  private static final Pattern H1 = Pattern.compile("<h1[^>]*>(.*?)</h1>", Pattern.DOTALL);
  private static final Pattern EMOJI = Pattern.compile("[\\x{1F300}-\\x{1FAFF}\\x{2600}-\\x{27BF}]");

  /** Páginas migradas al sistema nuevo. Cada task agrega las suyas. */
  static Stream<String> paginas() {
    return Stream.of(
        // Task 4
        "items", "item-edit", "materiales", "calculadora", "historial-crafteo", "modelo-3d",
        "inventario", "perfil", "admin-usuarios", "admin-usuario-edit", "admin-auditoria",
        "login", "register",
        // Task 5
        "proyectos", "proyecto-detalle", "proyecto-edit", "proyecto-estados", "reportes",
        "mundos", "mundo-detalle", "mundo-edit");
  }

  static Stream<String> fragmentos() throws IOException {
    try (var s = Files.list(DIR.resolve("fragments"))) {
      return s.map(p -> "fragments/" + p.getFileName().toString().replace(".html", "")).toList().stream();
    }
  }

  static Stream<String> paginasYFragmentos() throws IOException {
    return Stream.concat(paginas(), fragmentos());
  }

  private static final Pattern BADGE_ESTADO_CRUDO = Pattern.compile(
      "class=\"[^\"]*badge[^\"]*\"[^>]*th:text=\"\\$\\{[a-z]+\\.estado\\}\""
          + "|th:text=\"\\$\\{[a-z]+\\.estado\\}\"[^>]*class=\"[^\"]*badge[^\"]*\"");

  private static String leer(String nombre) throws IOException {
    return Files.readString(DIR.resolve(nombre + ".html"), StandardCharsets.UTF_8);
  }

  @ParameterizedTest
  @MethodSource("paginasYFragmentos")
  void noStaticInlineStyles(String nombre) throws IOException {
    assertFalse(STYLE_ESTATICO.matcher(leer(nombre)).find(), nombre + " tiene style=\"...\" estático");
  }

  @ParameterizedTest
  @MethodSource("paginas")
  void noEmojiInHeadings(String nombre) throws IOException {
    var m = H1.matcher(leer(nombre));
    while (m.find()) {
      var texto = m.group(1);
      assertFalse(EMOJI.matcher(texto).find(), nombre + " tiene emoji en <h1>: " + texto);
      assertTrue(texto.codePoints().noneMatch(c -> Character.getType(c) == Character.OTHER_SYMBOL || Character.isSupplementaryCodePoint(c)),
          nombre + " tiene símbolo en <h1>: " + texto);
    }
  }

  @ParameterizedTest
  @MethodSource("paginas")
  void everyPageUsesHeadFragment(String nombre) throws IOException {
    assertTrue(leer(nombre).contains("fragments/head :: head("), nombre + " no usa fragments/head");
  }

  @ParameterizedTest
  @MethodSource("paginasYFragmentos")
  void estadoBadgesUseFragment(String nombre) throws IOException {
    assertFalse(BADGE_ESTADO_CRUDO.matcher(leer(nombre)).find(),
        nombre + " muestra el estado crudo en un badge; usar fragments/badge :: estado");
  }
}
