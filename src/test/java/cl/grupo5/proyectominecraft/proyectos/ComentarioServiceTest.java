package cl.grupo5.proyectominecraft.proyectos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ComentarioServiceTest {

  private static Comentario comentario(String autor, String fecha) {
    var c = new Comentario();
    c.setAutorUid(autor);
    c.setFechaCreacion(fecha);
    return c;
  }

  private static Proyecto proyecto() {
    var p = new Proyecto();
    p.setCreadorUid("creador");
    p.setMiembros(List.of(new Miembro("editor", "Ed", "e@x", ProyectoService.EDICION, "t"),
        new Miembro("lector", "Le", "l@x", ProyectoService.LECTURA, "t")));
    return p;
  }

  @Test
  void normalizarTextoHaceTrim() {
    assertThat(ComentarioService.normalizarTexto("  hola  ")).isEqualTo("hola");
  }

  @Test
  void normalizarTextoRechazaVacioYLargo() {
    assertThatThrownBy(() -> ComentarioService.normalizarTexto(null)).isInstanceOf(ComentarioValidationException.class);
    assertThatThrownBy(() -> ComentarioService.normalizarTexto("   ")).isInstanceOf(ComentarioValidationException.class)
        .hasMessage("El comentario no puede estar vacío.");
    assertThatThrownBy(() -> ComentarioService.normalizarTexto("a".repeat(1001)))
        .isInstanceOf(ComentarioValidationException.class)
        .hasMessage("El comentario no puede superar los 1000 caracteres.");
    assertThat(ComentarioService.normalizarTexto(" " + "a".repeat(1000) + " ")).hasSize(1000);
  }

  @Test
  void soloAutorEdita() {
    var c = comentario("lector", "t");
    assertThat(ComentarioService.puedeEditar(c, "lector")).isTrue();
    assertThat(ComentarioService.puedeEditar(c, "creador")).isFalse();
    assertThat(ComentarioService.puedeEditar(c, null)).isFalse();
    assertThat(ComentarioService.puedeEditar(null, "lector")).isFalse();
  }

  @Test
  void borranAutorCreadorYAdmin() {
    var c = comentario("lector", "t");
    var p = proyecto();
    assertThat(ComentarioService.puedeBorrar(c, p, "lector", "USER")).isTrue();
    assertThat(ComentarioService.puedeBorrar(c, p, "creador", "USER")).isTrue();
    assertThat(ComentarioService.puedeBorrar(c, p, "otro-admin", "ADMIN")).isTrue();
    assertThat(ComentarioService.puedeBorrar(c, p, "editor", "USER")).isFalse();
    assertThat(ComentarioService.puedeBorrar(c, p, "extraño", "USER")).isFalse();
  }

  @Test
  void puedeComentarQuienVe() {
    var p = proyecto();
    assertThat(ComentarioService.puedeComentar(p, "lector", "USER")).isTrue();
    assertThat(ComentarioService.puedeComentar(p, "extraño", "USER")).isFalse();
  }

  @Test
  void esModeracionCuandoNoEsElAutor() {
    var c = comentario("lector", "t");
    assertThat(ComentarioService.esModeracion(c, "creador")).isTrue();
    assertThat(ComentarioService.esModeracion(c, "lector")).isFalse();
  }

  @Test
  void ordenAscendenteNullsAlFinal() {
    var b = comentario("a", "2026-11-25T10:00:00Z");
    var a = comentario("a", "2026-11-24T10:00:00Z");
    var sin = comentario("a", null);
    assertThat(ComentarioService.ordenar(List.of(b, sin, a))).containsExactly(a, b, sin);
  }

  @Test
  void nuevoComentarioSinFechaEdicion() {
    var c = ComentarioService.nuevo("p1", "u1", "Steve", "  hola ", "2026-11-25T10:00:00Z");
    assertThat(c.getProyectoId()).isEqualTo("p1");
    assertThat(c.getAutorUid()).isEqualTo("u1");
    assertThat(c.getAutorNombre()).isEqualTo("Steve");
    assertThat(c.getTexto()).isEqualTo("hola");
    assertThat(c.getFechaCreacion()).isEqualTo("2026-11-25T10:00:00Z");
    assertThat(c.getFechaEdicion()).isNull();
  }

  @Test
  void nuevoComentarioSinNombreUsaAnonimo() {
    assertThat(ComentarioService.nuevo("p1", "u1", null, "hola", "t").getAutorNombre()).isEqualTo("Anónimo");
  }
}
