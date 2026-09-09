package cl.grupo5.proyectominecraft.proyectos;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EstadoProyectoTest {

  @Test
  void testTransicionesValidas() {
    assertThat(EstadoProyecto.PLANIFICACION.puedeTransicionarA("EN_CONSTRUCCION")).isTrue();
    assertThat(EstadoProyecto.PLANIFICACION.puedeTransicionarA("CANCELADO")).isTrue();
    assertThat(EstadoProyecto.PLANIFICACION.puedeTransicionarA("COMPLETADO")).isFalse();

    assertThat(EstadoProyecto.EN_CONSTRUCCION.puedeTransicionarA("COMPLETADO")).isTrue();
    assertThat(EstadoProyecto.EN_CONSTRUCCION.puedeTransicionarA("CANCELADO")).isTrue();
    assertThat(EstadoProyecto.EN_CONSTRUCCION.puedeTransicionarA("PLANIFICACION")).isFalse();

    assertThat(EstadoProyecto.COMPLETADO.getTransicionesValidas()).isEmpty();
    assertThat(EstadoProyecto.COMPLETADO.puedeTransicionarA("PLANIFICACION")).isFalse();

    assertThat(EstadoProyecto.CANCELADO.puedeTransicionarA("PLANIFICACION")).isTrue();
    assertThat(EstadoProyecto.CANCELADO.puedeTransicionarA("EN_CONSTRUCCION")).isFalse();
  }

  @Test
  void fromStringIsCaseInsensitive() {
    assertThat(EstadoProyecto.fromString("planificacion")).isEqualTo(EstadoProyecto.PLANIFICACION);
    assertThat(EstadoProyecto.fromString("En_Construccion")).isEqualTo(EstadoProyecto.EN_CONSTRUCCION);
  }

  @Test
  void fromStringThrowsOnInvalidState() {
    assertThatThrownBy(() -> EstadoProyecto.fromString("INVALIDO"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("El estado 'INVALIDO' no es válido.");
    
    assertThatThrownBy(() -> EstadoProyecto.fromString(null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("Estado no puede ser nulo");
  }
}
