package cl.grupo5.proyectominecraft.home;

import cl.grupo5.proyectominecraft.inventario.HistorialCrafteoService;
import cl.grupo5.proyectominecraft.inventario.InventoryService;
import cl.grupo5.proyectominecraft.inventario.RegistroCrafteo;
import cl.grupo5.proyectominecraft.mundos.Mundo;
import cl.grupo5.proyectominecraft.mundos.MundoService;
import cl.grupo5.proyectominecraft.proyectos.Proyecto;
import cl.grupo5.proyectominecraft.proyectos.ProyectoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DashboardServiceTest {

  private ProyectoService proyectos;
  private InventoryService inventory;
  private HistorialCrafteoService historial;
  private MundoService mundos;
  private DashboardService service;

  @BeforeEach
  void setUp() {
    proyectos = mock(ProyectoService.class);
    inventory = mock(InventoryService.class);
    historial = mock(HistorialCrafteoService.class);
    mundos = mock(MundoService.class);
    service = new DashboardService(proyectos, inventory, historial, mundos);
  }

  private static Proyecto proyecto(String estado, String fecha) {
    var p = new Proyecto();
    p.setEstado(estado);
    p.setFechaCreacion(fecha);
    return p;
  }

  private void stubTodoVacio() throws Exception {
    when(proyectos.list(null, null, null, "u1", "USUARIO")).thenReturn(List.of());
    when(inventory.get("u1")).thenReturn(Map.of());
    when(historial.listar("u1")).thenReturn(List.of());
    when(mundos.listar("u1")).thenReturn(List.of());
  }

  @Test
  void contarActivosIncluyePlanificacionEnConstruccionYNull() {
    var lista = Arrays.asList(
        proyecto("PLANIFICACION", null),
        proyecto("EN_CONSTRUCCION", null),
        proyecto(null, null),
        proyecto("COMPLETADO", null),
        proyecto("CANCELADO", null));
    assertEquals(3, DashboardService.contarActivos(lista));
  }

  @Test
  void recientesOrdenaDescYLimita() {
    var lista = new ArrayList<Proyecto>();
    lista.add(proyecto("PLANIFICACION", null));
    for (int i = 1; i <= 7; i++) lista.add(proyecto("PLANIFICACION", "2026-0" + i + "-01T10:00:00"));

    var r = DashboardService.recientes(lista, 5);

    assertEquals(5, r.size());
    assertEquals("2026-07-01T10:00:00", r.get(0).getFechaCreacion());
    assertTrue(r.stream().noneMatch(p -> p.getFechaCreacion() == null));
  }

  @Test
  void recientesConNullAlFinal() {
    var r = DashboardService.recientes(List.of(proyecto("PLANIFICACION", null), proyecto("PLANIFICACION", "2026-01-01")), 5);
    assertEquals(Arrays.asList("2026-01-01", null), r.stream().map(Proyecto::getFechaCreacion).toList());
  }

  @Test
  void unidadesYTiposIgnoranCeroYNegativos() {
    var inv = new LinkedHashMap<String, Long>();
    inv.put("a", 3L);
    inv.put("b", 0L);
    inv.put("c", 5L);
    inv.put("d", -1L);
    assertEquals(8L, DashboardService.unidades(inv));
    assertEquals(2, DashboardService.tipos(inv));
  }

  @Test
  void resumenAgregaTodasLasFuentes() throws Exception {
    var lista = List.of(proyecto("PLANIFICACION", "2026-01-01"), proyecto("EN_CONSTRUCCION", "2026-02-01"), proyecto("COMPLETADO", "2026-03-01"));
    when(proyectos.list(null, null, null, "u1", "USUARIO")).thenReturn(lista);
    when(inventory.get("u1")).thenReturn(Map.of("a", 4L));
    when(historial.listar("u1")).thenReturn(List.of(new RegistroCrafteo(), new RegistroCrafteo()));
    when(mundos.listar("u1")).thenReturn(List.of(new Mundo()));

    var r = service.resumen("u1", "USUARIO");

    assertEquals(2, r.proyectosActivos());
    assertEquals(3, r.proyectosTotal());
    assertEquals(4L, r.unidadesInventario());
    assertEquals(1, r.tiposInventario());
    assertEquals(2, r.crafteos());
    assertEquals(1, r.mundos());
    assertEquals(3, r.recientes().size());
    assertEquals("2026-03-01", r.recientes().get(0).getFechaCreacion());
  }

  @Test
  void resumenToleraFallaDeUnaFuente() throws Exception {
    stubTodoVacio();
    when(inventory.get("u1")).thenThrow(new RuntimeException("Firestore caído"));

    var r = service.resumen("u1", "USUARIO");

    assertNull(r.unidadesInventario());
    assertNull(r.tiposInventario());
    assertEquals(0, r.proyectosActivos());
    assertEquals(0, r.proyectosTotal());
    assertEquals(0, r.crafteos());
    assertEquals(0, r.mundos());
  }

  @Test
  void resumenToleraFallaDeProyectos() throws Exception {
    stubTodoVacio();
    when(proyectos.list(any(), any(), any(), any(), any())).thenThrow(new RuntimeException("caído"));

    var r = service.resumen("u1", "USUARIO");

    assertNull(r.proyectosActivos());
    assertNull(r.proyectosTotal());
    assertTrue(r.recientes().isEmpty());
    assertEquals(0L, r.unidadesInventario());
  }
}
