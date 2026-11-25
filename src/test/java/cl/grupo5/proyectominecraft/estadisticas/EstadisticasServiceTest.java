package cl.grupo5.proyectominecraft.estadisticas;

import cl.grupo5.proyectominecraft.inventario.InventoryService;
import cl.grupo5.proyectominecraft.items.Ingrediente;
import cl.grupo5.proyectominecraft.items.Item;
import cl.grupo5.proyectominecraft.items.ItemService;
import cl.grupo5.proyectominecraft.proyectos.ItemRequerido;
import cl.grupo5.proyectominecraft.proyectos.Proyecto;
import cl.grupo5.proyectominecraft.proyectos.ProyectoService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class EstadisticasServiceTest {

  private static Item raw(String id, String nombre) {
    var i = new Item();
    i.setId(id);
    i.setNombre(nombre);
    i.setEsMateriaPrima(true);
    return i;
  }

  private static Item receta(String id, String nombre, Object... ings) {
    var i = new Item();
    i.setId(id);
    i.setNombre(nombre);
    var lista = new ArrayList<Ingrediente>();
    for (int k = 0; k < ings.length; k += 2) {
      var ing = new Ingrediente();
      ing.setItemId((String) ings[k]);
      ing.setCantidad((Integer) ings[k + 1]);
      lista.add(ing);
    }
    i.setIngredientesParaCalculo(lista);
    return i;
  }

  private static Proyecto proyecto(String estado, Object... items) {
    var p = new Proyecto();
    p.setEstado(estado);
    var lista = new ArrayList<ItemRequerido>();
    for (int k = 0; k < items.length; k += 2) lista.add(new ItemRequerido((String) items[k], (Integer) items[k + 1]));
    p.setItemsRequeridos(lista);
    return p;
  }

  private static Map<String, Item> mapa(Item... items) {
    var m = new HashMap<String, Item>();
    for (var i : items) m.put(i.getId(), i);
    return m;
  }

  @Test
  void porEstadoNullCuentaPlanificacionYSiempreLosCuatro() {
    var r = EstadisticasService.porEstado(List.of(proyecto(null), proyecto("PLANIFICACION"), proyecto("COMPLETADO")));
    assertThat(r).containsExactly(
        Map.entry("PLANIFICACION", 2), Map.entry("EN_CONSTRUCCION", 0),
        Map.entry("COMPLETADO", 1), Map.entry("CANCELADO", 0));
  }

  @Test
  void masUsadosCuentaRecetasDistintasYDesempata() {
    var catalogo = List.of(
        raw("tronco", "Tronco"), raw("hierro", "Hierro"), raw("palo", "Palo"),
        receta("tablones", "Tablones", "tronco", 1),
        receta("pico", "Pico", "hierro", 3, "palo", 2),
        receta("espada", "Espada", "hierro", 2, "palo", 1),
        receta("antorcha", "Antorcha", "palo", 1),
        receta("hacha", "Hacha", "tronco", 5));

    var r = EstadisticasService.itemsMasUsados(catalogo, 10);

    assertThat(r).extracting(EntradaRanking::getItemId, EntradaRanking::getValor, EntradaRanking::getSecundario)
        .containsExactly(tuple("palo", 3L, 4L), tuple("tronco", 2L, 6L), tuple("hierro", 2L, 5L));
    assertThat(r.get(0).getNombre()).isEqualTo("Palo");
  }

  @Test
  void masUsadosTop10() {
    var catalogo = new ArrayList<Item>();
    for (int k = 0; k < 15; k++) {
      catalogo.add(raw("m" + k, "M" + k));
      catalogo.add(receta("r" + k, "R" + k, "m" + k, 1));
    }
    assertThat(EstadisticasService.itemsMasUsados(catalogo, 10)).hasSize(10);
    assertThat(EstadisticasService.itemsMasUsados(List.of(), 10)).isEmpty();
  }

  @Test
  void escasosUsaStockCompartidoYSoloActivos() {
    var catalogo = mapa(raw("tronco", "Tronco"), raw("hierro", "Hierro"), receta("tablones", "Tablones", "tronco", 1));
    var stock = new LinkedHashMap<String, Long>(Map.of("tronco", 3L));
    var proyectos = List.of(
        proyecto("PLANIFICACION", "tablones", 2),
        proyecto(null, "tablones", 2, "hierro", 1),
        proyecto("EN_CONSTRUCCION", "hierro", 4),
        proyecto("COMPLETADO", "hierro", 100),
        proyecto("CANCELADO", "tronco", 100));

    var r = EstadisticasService.materialesEscasos(proyectos, catalogo, stock, 10);

    assertThat(r).extracting(EntradaRanking::getItemId, EntradaRanking::getValor, EntradaRanking::getSecundario)
        .containsExactly(tuple("hierro", 5L, 2L), tuple("tronco", 1L, 1L));
    assertThat(stock).containsEntry("tronco", 3L);
  }

  @Test
  void escasosOmiteProyectoQueFallaSinConsumirStock() {
    var catalogo = mapa(raw("tronco", "Tronco"), receta("tablones", "Tablones", "tronco", 1));
    var proyectos = List.of(
        proyecto("PLANIFICACION", "tronco", 2, "borrado", 1),
        proyecto("PLANIFICACION", "tronco", 3));

    var r = EstadisticasService.materialesEscasos(proyectos, catalogo, Map.of("tronco", 2L), 10);

    assertThat(r).extracting(EntradaRanking::getItemId, EntradaRanking::getValor).containsExactly(tuple("tronco", 1L));
  }

  @Test
  void resumenMarcaNullSoloLaFuenteQueFalla() throws Exception {
    var proyectos = mock(ProyectoService.class);
    var items = mock(ItemService.class);
    var inventario = mock(InventoryService.class);
    when(proyectos.list(any(), any(), any(), any(), any())).thenReturn(List.of(proyecto("PLANIFICACION")));
    when(items.list(null, null)).thenReturn(List.of(raw("tronco", "Tronco")));
    when(inventario.get("u1")).thenThrow(new RuntimeException("Firestore caído"));

    var r = new EstadisticasService(proyectos, items, inventario).resumen("u1", "USER");

    assertThat(r.porEstado()).containsEntry("PLANIFICACION", 1);
    assertThat(r.masUsados()).isEmpty();
    assertThat(r.escasos()).isNull();
  }

  @Test
  void datosGraficosUsaEtiquetasLegiblesYNullSiFalla() {
    var e = new Estadisticas(
        EstadisticasService.porEstado(List.of(proyecto("EN_CONSTRUCCION"))),
        List.of(new EntradaRanking("palo", "Palo", 3, 4)),
        null);

    var d = EstadisticasService.datosGraficos(e);

    @SuppressWarnings("unchecked")
    var estado = (Map<String, Object>) d.get("porEstado");
    assertThat(estado.get("labels")).isEqualTo(List.of("Planificación", "En construcción", "Completado", "Cancelado"));
    assertThat(estado.get("valores")).isEqualTo(List.of(0, 1, 0, 0));
    @SuppressWarnings("unchecked")
    var usados = (Map<String, Object>) d.get("masUsados");
    assertThat(usados.get("labels")).isEqualTo(List.of("Palo"));
    assertThat(usados.get("valores")).isEqualTo(List.of(3L));
    assertThat(d.get("escasos")).isNull();
    assertThat(d).containsKey("escasos");
  }
}
