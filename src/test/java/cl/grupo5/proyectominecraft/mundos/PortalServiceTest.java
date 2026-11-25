package cl.grupo5.proyectominecraft.mundos;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PortalServiceTest {

  private static Portal portal(String id, String nombre, String dim, int x, int y, int z) {
    var p = new Portal();
    p.setId(id);
    p.setNombre(nombre);
    p.setDimension(dim);
    p.setX(x);
    p.setY(y);
    p.setZ(z);
    return p;
  }

  private static Mundo mundo(Portal... portales) {
    var m = new Mundo();
    m.setId("m1");
    m.setPortales(new ArrayList<>(List.of(portales)));
    return m;
  }

  @Test
  void normalizarValidaNombreDimensionYRango() {
    assertThatThrownBy(() -> PortalService.normalizar(portal(null, "  ", "OVERWORLD", 0, 64, 0)))
        .isInstanceOf(MundoValidationException.class).hasMessage("El nombre del portal es obligatorio.");
    assertThatThrownBy(() -> PortalService.normalizar(portal(null, "a".repeat(61), "OVERWORLD", 0, 64, 0)))
        .isInstanceOf(MundoValidationException.class);
    assertThatThrownBy(() -> PortalService.normalizar(portal(null, "P", "END", 0, 64, 0)))
        .isInstanceOf(MundoValidationException.class).hasMessage("Dimensión inválida.");
    assertThatThrownBy(() -> PortalService.normalizar(portal(null, "P", "NETHER", 0, 300, 0)))
        .isInstanceOf(MundoValidationException.class);
    var ok = PortalService.normalizar(portal(null, " Base ", "nether", 1, 2, 3));
    assertThat(ok.getNombre()).isEqualTo("Base");
    assertThat(ok.getDimension()).isEqualTo("NETHER");
  }

  @Test
  void agregarAsignaUuidYToleraListaNula() {
    var m = new Mundo();
    m.setPortales(null);
    var p = PortalService.agregar(m, portal("ignorado", "Base", "OVERWORLD", 0, 64, 0));
    assertThat(p.getId()).isNotBlank().isNotEqualTo("ignorado");
    assertThat(p.getEnlazadoId()).isNull();
    assertThat(m.getPortales()).containsExactly(p);
  }

  @Test
  void listarToleraNull() {
    var m = new Mundo();
    m.setPortales(null);
    assertThat(PortalService.portales(m)).isEmpty();
    assertThat(PortalService.portales(null)).isEmpty();
  }

  @Test
  void enlazarMismaDimensionFalla() {
    var m = mundo(portal("a", "A", "OVERWORLD", 0, 64, 0), portal("b", "B", "OVERWORLD", 8, 64, 0));
    assertThatThrownBy(() -> PortalService.enlazar(m, "a", "b"))
        .isInstanceOf(MundoValidationException.class)
        .hasMessage("Solo se pueden enlazar portales de dimensiones distintas.");
  }

  @Test
  void enlazarPortalInexistenteOConsigoMismoFalla() {
    var m = mundo(portal("a", "A", "OVERWORLD", 0, 64, 0));
    assertThatThrownBy(() -> PortalService.enlazar(m, "a", "zz")).isInstanceOf(MundoValidationException.class);
    assertThatThrownBy(() -> PortalService.enlazar(m, "a", "a")).isInstanceOf(MundoValidationException.class);
  }

  @Test
  void enlazarEsBidireccional() {
    var a = portal("a", "A", "OVERWORLD", 0, 64, 0);
    var b = portal("b", "B", "NETHER", 0, 64, 0);
    PortalService.enlazar(mundo(a, b), "a", "b");
    assertThat(a.getEnlazadoId()).isEqualTo("b");
    assertThat(b.getEnlazadoId()).isEqualTo("a");
  }

  @Test
  void enlazarSueltaParesAnteriores() {
    var a = portal("a", "A", "OVERWORLD", 0, 64, 0);
    var b = portal("b", "B", "NETHER", 0, 64, 0);
    var c = portal("c", "C", "NETHER", 5, 64, 5);
    var d = portal("d", "D", "OVERWORLD", 40, 64, 40);
    var m = mundo(a, b, c, d);
    PortalService.enlazar(m, "a", "b");
    PortalService.enlazar(m, "d", "c");
    PortalService.enlazar(m, "a", "c");
    assertThat(a.getEnlazadoId()).isEqualTo("c");
    assertThat(c.getEnlazadoId()).isEqualTo("a");
    assertThat(b.getEnlazadoId()).isNull();
    assertThat(d.getEnlazadoId()).isNull();
  }

  @Test
  void eliminarLimpiaEnlaceDelOtro() {
    var a = portal("a", "A", "OVERWORLD", 0, 64, 0);
    var b = portal("b", "B", "NETHER", 0, 64, 0);
    var m = mundo(a, b);
    PortalService.enlazar(m, "a", "b");
    PortalService.eliminar(m, "a");
    assertThat(m.getPortales()).containsExactly(b);
    assertThat(b.getEnlazadoId()).isNull();
  }

  @Test
  void eliminarInexistenteFalla() {
    assertThatThrownBy(() -> PortalService.eliminar(mundo(), "zz"))
        .isInstanceOf(MundoValidationException.class).hasMessage(PortalService.NO_ENCONTRADO);
  }

  @Test
  void crearPareadoConvierteYEnlaza() {
    var a = portal("a", "Base", "OVERWORLD", 100, 70, -9);
    var m = mundo(a);
    var par = PortalService.crearPareado(m, "a");
    assertThat(par.getDimension()).isEqualTo("NETHER");
    assertThat(par.getX()).isEqualTo(12);
    assertThat(par.getY()).isEqualTo(70);
    assertThat(par.getZ()).isEqualTo(-2);
    assertThat(par.getNombre()).isEqualTo("Base (par)");
    assertThat(par.getEnlazadoId()).isEqualTo("a");
    assertThat(a.getEnlazadoId()).isEqualTo(par.getId());
    assertThat(m.getPortales()).hasSize(2);
  }

  @Test
  void pareadoAcotaY() {
    var alto = portal("a", "Torre", "OVERWORLD", 0, 300, 0);
    assertThat(PortalService.crearPareado(mundo(alto), "a").getY()).isEqualTo(255);
    var bajo = portal("b", "Mina", "OVERWORLD", 0, -60, 0);
    assertThat(PortalService.crearPareado(mundo(bajo), "b").getY()).isZero();
  }

  @Test
  void actualizarCambiaDatosYEnlaceVacioDesenlaza() {
    var a = portal("a", "A", "OVERWORLD", 0, 64, 0);
    var b = portal("b", "B", "NETHER", 0, 64, 0);
    var m = mundo(a, b);
    PortalService.enlazar(m, "a", "b");
    var cambios = portal(null, "A2", "OVERWORLD", 16, 70, 16);
    cambios.setEnlazadoId("");
    PortalService.actualizar(m, "a", cambios);
    assertThat(a.getNombre()).isEqualTo("A2");
    assertThat(a.getX()).isEqualTo(16);
    assertThat(a.getEnlazadoId()).isNull();
    assertThat(b.getEnlazadoId()).isNull();
  }

  @Test
  void actualizarConCambioDeDimensionSueltaEnlaceInvalido() {
    var a = portal("a", "A", "OVERWORLD", 0, 64, 0);
    var b = portal("b", "B", "NETHER", 0, 64, 0);
    var m = mundo(a, b);
    PortalService.enlazar(m, "a", "b");
    var cambios = portal(null, "A", "NETHER", 0, 64, 0);
    cambios.setEnlazadoId("b");
    assertThatThrownBy(() -> PortalService.actualizar(m, "a", cambios)).isInstanceOf(MundoValidationException.class);
  }

  @Test
  void actualizarEnlazaConOtro() {
    var a = portal("a", "A", "OVERWORLD", 0, 64, 0);
    var b = portal("b", "B", "NETHER", 0, 64, 0);
    var m = mundo(a, b);
    var cambios = portal(null, "A", "OVERWORLD", 0, 64, 0);
    cambios.setEnlazadoId("b");
    PortalService.actualizar(m, "a", cambios);
    assertThat(a.getEnlazadoId()).isEqualTo("b");
    assertThat(b.getEnlazadoId()).isEqualTo("a");
  }

  @Test
  void masCercanoSoloOverworld() {
    var lejos = portal("a", "Lejos", "OVERWORLD", 300, 64, 400);
    var cerca = portal("b", "Cerca", "OVERWORLD", 30, 64, 40);
    var nether = portal("c", "Nether", "NETHER", 0, 64, 0);
    var r = PortalService.masCercano(List.of(lejos, nether, cerca), 0, 0);
    assertThat(r).isPresent();
    assertThat(r.get().portal()).isSameAs(cerca);
    assertThat(r.get().distancia()).isEqualTo(50);
    assertThat(PortalService.masCercano(List.of(nether), 0, 0)).isEmpty();
    assertThat(PortalService.masCercano(null, 0, 0)).isEmpty();
  }
}
