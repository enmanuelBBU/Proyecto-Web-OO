package cl.grupo5.proyectominecraft.proyectos;

import org.junit.jupiter.api.Test;

import java.util.List;

import static cl.grupo5.proyectominecraft.proyectos.ProyectoPdfService.FilaItem;
import static cl.grupo5.proyectominecraft.proyectos.ProyectoPdfService.ReporteProyecto;
import static org.assertj.core.api.Assertions.assertThat;

class ProyectoPdfServiceTest {

  private final ProyectoPdfService service = new ProyectoPdfService();

  @Test
  void generarProduceBytesConCabeceraPdf() throws Exception {
    var reporte = new ReporteProyecto(
        "Reporte de Proyecto · Minecraft Manager",
        "Subtítulo",
        "01/01/2026",
        "autor-1",
        "En planificación",
        "Casa de Roble",
        "Descripción",
        "Objetivo",
        List.of(new FilaItem("Tablones de Roble", 64, "Bloques de Construcción"))
    );

    byte[] bytes = service.generar(reporte);

    assertThat(bytes).isNotEmpty();
    assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
  }

  @Test
  void generarConListaVaciaDeItemsSigueProduciendoPdf() throws Exception {
    var reporte = new ReporteProyecto(
        "Reporte de Proyecto · Minecraft Manager",
        "Subtítulo",
        "01/01/2026",
        "autor-1",
        "En planificación",
        "Casa de Roble",
        "Descripción",
        "Objetivo",
        List.of()
    );

    byte[] bytes = service.generar(reporte);

    assertThat(bytes).isNotEmpty();
    assertThat(new String(bytes, 0, 5, java.nio.charset.StandardCharsets.ISO_8859_1)).isEqualTo("%PDF-");
  }
}