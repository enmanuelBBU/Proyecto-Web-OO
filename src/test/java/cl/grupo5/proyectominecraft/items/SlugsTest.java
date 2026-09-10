package cl.grupo5.proyectominecraft.items;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class SlugsTest {

  @Test
  void slugifyLowercasesAndReplacesSpacesWithUnderscore() {
    assertThat(Slugs.slugify("Tablones de Roble")).isEqualTo("tablones_de_roble");
  }

  @Test
  void slugifyStripsAccents() {
    assertThat(Slugs.slugify("Peña")).isEqualTo("pena");
  }

  @Test
  void slugifySingleWord() {
    assertThat(Slugs.slugify("Arcilla")).isEqualTo("arcilla");
  }

  @Test
  void slugifyCollapsesMultipleSpacesAndTrims() {
    assertThat(Slugs.slugify("  Lingote   de   Hierro  ")).isEqualTo("lingote_de_hierro");
  }

  @Test
  void slugifyStripsSlashes() {
    assertThat(Slugs.slugify("Bloque/Piedra")).matches("[a-z0-9_]+");
    assertThat(Slugs.slugify("Bloque/Piedra")).isEqualTo("bloquepiedra");
  }

  @Test
  void slugifyStripsHashAndOtherPunctuation() {
    assertThat(Slugs.slugify("Item #1 (raro)?")).matches("[a-z0-9_]+");
    assertThat(Slugs.slugify("Item #1 (raro)?")).isEqualTo("item_1_raro");
  }

  @Test
  void slugifyStripsDotsFromAllPunctuationName() {
    assertThat(Slugs.slugify("...")).isEmpty();
  }
}
