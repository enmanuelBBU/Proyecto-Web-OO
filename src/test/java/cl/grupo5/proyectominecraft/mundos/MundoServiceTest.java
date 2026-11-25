package cl.grupo5.proyectominecraft.mundos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MundoServiceTest {

  private Mundo mundo(String nombre, String version, String modo) {
    var m = new Mundo();
    m.setNombre(nombre);
    m.setVersion(version);
    m.setModoJuego(modo);
    return m;
  }

  @Test
  void validarExigeNombre() {
    assertThatThrownBy(() -> MundoService.validar(mundo(" ", "1.21", null)))
        .isInstanceOf(MundoValidationException.class)
        .hasMessage("El nombre del mundo es obligatorio.");
  }

  @Test
  void validarExigeVersion() {
    assertThatThrownBy(() -> MundoService.validar(mundo("Survival", "", null)))
        .isInstanceOf(MundoValidationException.class)
        .hasMessage("La versión es obligatoria.");
  }

  @Test
  void validarRechazaModoInvalido() {
    assertThatThrownBy(() -> MundoService.validar(mundo("S", "1.21", "PACIFICO")))
        .isInstanceOf(MundoValidationException.class)
        .hasMessage("Modo de juego inválido.");
  }

  @Test
  void validarNormalizaModoYSemilla() {
    var m = mundo("  Survival  ", " 1.21 ", "creativo");
    m.setSemilla("  ");
    MundoService.validar(m);
    assertThat(m.getNombre()).isEqualTo("Survival");
    assertThat(m.getVersion()).isEqualTo("1.21");
    assertThat(m.getModoJuego()).isEqualTo("CREATIVO");
    assertThat(m.getSemilla()).isNull();

    var sinModo = mundo("S", "1.21", null);
    MundoService.validar(sinModo);
    assertThat(sinModo.getModoJuego()).isEqualTo("SUPERVIVENCIA");
  }

  @Test
  void validarConservaSemillaTextual() {
    var m = mundo("S", "1.21", "HARDCORE");
    m.setSemilla(" -4172144997902289642 ");
    MundoService.validar(m);
    assertThat(m.getSemilla()).isEqualTo("-4172144997902289642");
  }

  @Test
  void soloDuenoOAdminGestiona() {
    var m = mundo("S", "1.21", null);
    m.setUid("u1");
    assertThat(MundoService.puedeGestionar(m, "u1", "USUARIO")).isTrue();
    assertThat(MundoService.puedeGestionar(m, "u2", "ADMIN")).isTrue();
    assertThat(MundoService.puedeGestionar(m, "u2", "USUARIO")).isFalse();
    assertThat(MundoService.puedeGestionar(null, "u1", "ADMIN")).isFalse();
  }

  @Test
  void delUsuarioFiltraYOrdena() {
    var a = mundo("zeta", "1", null);
    a.setUid("u1");
    var b = mundo("Alfa", "1", null);
    b.setUid("u1");
    var c = mundo("otro", "1", null);
    c.setUid("u2");
    assertThat(MundoService.delUsuario(List.of(a, b, c), "u1"))
        .extracting(Mundo::getNombre).containsExactly("Alfa", "zeta");
  }

  @Test
  void actualizarPreservaPortales() {
    var actual = new Mundo();
    actual.setUid("u1");
    actual.setFechaCreacion("2026-11-01T00:00:00Z");
    var portal = new Portal();
    portal.setId("p1");
    actual.setPortales(new java.util.ArrayList<>(java.util.List.of(portal)));
    var cambios = new Mundo();
    cambios.setNombre("Nuevo");
    cambios.setPortales(null);

    var r = MundoService.prepararActualizacion(actual, cambios, "m1");

    assertThat(r.getId()).isEqualTo("m1");
    assertThat(r.getUid()).isEqualTo("u1");
    assertThat(r.getFechaCreacion()).isEqualTo("2026-11-01T00:00:00Z");
    assertThat(r.getPortales()).containsExactly(portal);
  }
}
