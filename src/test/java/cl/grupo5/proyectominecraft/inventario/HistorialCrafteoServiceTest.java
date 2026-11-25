package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.Item;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class HistorialCrafteoServiceTest {

  @Test
  void crearRegistroCopiaConsumidosDelPlanIncluidosIntermedios() {
    var target = new Item();
    target.setId("antorcha");
    target.setNombre("Antorcha");
    var plan = new CraftingPlan();
    plan.setTargetItemId("antorcha");
    plan.setCantidad(4);
    plan.setTipoReceta("CRAFTEO");
    plan.setConsumidos(List.of(new MaterialCantidad("carbon", "Carbón", 4), new MaterialCantidad("palos", "Palos", 2)));

    var r = HistorialCrafteoService.crearRegistro("u1", target, plan, "2026-11-23T10:00:00Z");

    assertThat(r.getUid()).isEqualTo("u1");
    assertThat(r.getItemId()).isEqualTo("antorcha");
    assertThat(r.getItemNombre()).isEqualTo("Antorcha");
    assertThat(r.getCantidad()).isEqualTo(4);
    assertThat(r.getTipoReceta()).isEqualTo("CRAFTEO");
    assertThat(r.getFecha()).isEqualTo("2026-11-23T10:00:00Z");
    assertThat(r.getMateriales())
        .extracting(MaterialConsumido::getItemId, MaterialConsumido::getNombre, MaterialConsumido::getCantidad)
        .containsExactly(tuple("carbon", "Carbón", 4L), tuple("palos", "Palos", 2L));
  }

  @Test
  void crearRegistroUsaIdSiElItemNoTieneNombre() {
    var target = new Item();
    target.setId("lingote");
    var plan = new CraftingPlan();
    plan.setTargetItemId("lingote");
    plan.setCantidad(1);
    plan.setTipoReceta("HORNO");
    plan.setMateriales(List.of());
    assertThat(HistorialCrafteoService.crearRegistro("u1", target, plan, "t").getItemNombre()).isEqualTo("lingote");
  }

  @Test
  void ordenarRecientesPrimero() {
    var viejo = new RegistroCrafteo();
    viejo.setFecha("2026-11-01T10:00:00Z");
    var nuevo = new RegistroCrafteo();
    nuevo.setFecha("2026-11-23T10:00:00Z");
    var sinFecha = new RegistroCrafteo();
    assertThat(HistorialCrafteoService.ordenarRecientes(List.of(viejo, sinFecha, nuevo)))
        .containsExactly(nuevo, viejo, sinFecha);
  }
}
