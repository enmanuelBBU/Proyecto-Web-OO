package cl.grupo5.proyectominecraft.mundos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NetherCalculatorTest {

  @Test
  void overworldANetherUsaFloorDivConNegativos() {
    assertThat(NetherCalculator.convertir(Dimension.OVERWORLD, -1, 64, -1)).isEqualTo(new Coordenadas("NETHER", -1, 64, -1));
    assertThat(NetherCalculator.convertir(Dimension.OVERWORLD, -9, 64, -16)).isEqualTo(new Coordenadas("NETHER", -2, 64, -2));
    assertThat(NetherCalculator.convertir(Dimension.OVERWORLD, 8, 70, 15)).isEqualTo(new Coordenadas("NETHER", 1, 70, 1));
    assertThat(NetherCalculator.convertir(Dimension.OVERWORLD, 0, 0, 7)).isEqualTo(new Coordenadas("NETHER", 0, 0, 0));
  }

  @Test
  void netherAOverworldMultiplicaPorOcho() {
    assertThat(NetherCalculator.convertir(Dimension.NETHER, -2, 40, 3)).isEqualTo(new Coordenadas("OVERWORLD", -16, 40, 24));
  }

  @Test
  void yOverworldFueraDeRango() {
    assertThatThrownBy(() -> NetherCalculator.convertir(Dimension.OVERWORLD, 0, -65, 0))
        .isInstanceOf(MundoValidationException.class).hasMessageContaining("-64");
    assertThatThrownBy(() -> NetherCalculator.convertir(Dimension.OVERWORLD, 0, 321, 0))
        .isInstanceOf(MundoValidationException.class);
    NetherCalculator.validar(Dimension.OVERWORLD, 0, -64, 0);
    NetherCalculator.validar(Dimension.OVERWORLD, 0, 320, 0);
  }

  @Test
  void yNetherFueraDeRango() {
    assertThatThrownBy(() -> NetherCalculator.convertir(Dimension.NETHER, 0, -1, 0))
        .isInstanceOf(MundoValidationException.class).hasMessageContaining("255");
    assertThatThrownBy(() -> NetherCalculator.convertir(Dimension.NETHER, 0, 256, 0))
        .isInstanceOf(MundoValidationException.class);
  }

  @Test
  void xzFueraDeRango() {
    assertThatThrownBy(() -> NetherCalculator.convertir(Dimension.OVERWORLD, 30_000_001, 64, 0))
        .isInstanceOf(MundoValidationException.class);
    assertThatThrownBy(() -> NetherCalculator.convertir(Dimension.OVERWORLD, 0, 64, -30_000_001))
        .isInstanceOf(MundoValidationException.class);
    assertThatThrownBy(() -> NetherCalculator.convertir(Dimension.NETHER, 3_750_001, 64, 0))
        .isInstanceOf(MundoValidationException.class);
    assertThat(NetherCalculator.convertir(Dimension.NETHER, 3_750_000, 64, -3_750_000))
        .isEqualTo(new Coordenadas("OVERWORLD", 30_000_000, 64, -30_000_000));
  }

  @Test
  void acotarYAlRangoDeLaDimension() {
    assertThat(NetherCalculator.acotarY(Dimension.NETHER, 300)).isEqualTo(255);
    assertThat(NetherCalculator.acotarY(Dimension.NETHER, -60)).isZero();
    assertThat(NetherCalculator.acotarY(Dimension.OVERWORLD, 100)).isEqualTo(100);
  }

  @Test
  void dimensionFromStringSinMayusculas() {
    assertThat(Dimension.fromString(" nether ")).isEqualTo(Dimension.NETHER);
    assertThat(Dimension.OVERWORLD.opuesta()).isEqualTo(Dimension.NETHER);
    assertThatThrownBy(() -> Dimension.fromString("END")).isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Dimensión inválida.");
    assertThatThrownBy(() -> Dimension.fromString(null)).isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void dimensionParseoValidaComoMundoValidation() {
    assertThatThrownBy(() -> NetherCalculator.dimension("END")).isInstanceOf(MundoValidationException.class)
        .hasMessage("Dimensión inválida.");
  }
}
