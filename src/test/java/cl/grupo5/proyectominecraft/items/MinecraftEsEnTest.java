package cl.grupo5.proyectominecraft.items;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class MinecraftEsEnTest {

  private final MinecraftEsEn dictionary = new MinecraftEsEn();

  @Test
  void translatesMultiWordNameDroppingStopwords() {
    var result = dictionary.translate("Tablones de Roble");

    assertThat(result).contains("oak").contains("planks").doesNotContain("de");
  }

  @Test
  void translatesSingleKnownWord() {
    assertThat(dictionary.translate("Arcilla")).isEqualTo("clay");
  }

  @Test
  void passesThroughUnknownTokenUnchanged() {
    assertThat(dictionary.translate("TNT")).isEqualTo("tnt");
  }

  @Test
  void emptyOrNullNombreTranslatesToEmptyString() {
    assertThat(dictionary.translate("")).isEmpty();
    assertThat(dictionary.translate(null)).isEmpty();
  }
}
