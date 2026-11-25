package cl.grupo5.proyectominecraft.estadisticas;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebEstadisticasController.class)
class WebEstadisticasControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private EstadisticasService service;

  private static MockHttpSession sesion() {
    var s = new MockHttpSession();
    s.setAttribute("uid", "u1");
    s.setAttribute("rol", "USER");
    return s;
  }

  private static Map<String, Integer> estados(int plan) {
    var m = new LinkedHashMap<String, Integer>();
    m.put("PLANIFICACION", plan);
    m.put("EN_CONSTRUCCION", 0);
    m.put("COMPLETADO", 0);
    m.put("CANCELADO", 0);
    return m;
  }

  @Test
  void sinSesionRedirigeALogin() throws Exception {
    mvc.perform(get("/estadisticas")).andExpect(redirectedUrl("/login"));
  }

  @Test
  void muestraGraficosYTablas() throws Exception {
    when(service.resumen("u1", "USER")).thenReturn(new Estadisticas(estados(2),
        List.of(new EntradaRanking("palo", "Palo", 3, 4)),
        List.of(new EntradaRanking("hierro", "Hierro crudo", 5, 2))));

    mvc.perform(get("/estadisticas").session(sesion()))
        .andExpect(status().isOk())
        .andExpect(view().name("estadisticas"))
        .andExpect(model().attributeExists("datosGraficos"))
        .andExpect(content().string(containsString("https://cdnjs.cloudflare.com/ajax/libs/Chart.js/4.4.1/chart.umd.min.js")))
        .andExpect(content().string(containsString("/estadisticas.js")))
        .andExpect(content().string(containsString("Hierro crudo")))
        .andExpect(content().string(containsString("\"labels\":[\"Hierro crudo\"]")))
        .andExpect(content().string(containsString("\"escasos\":{")))
        .andExpect(content().string(containsString("Ítems más usados en recetas")))
        .andExpect(content().string(containsString("Materiales más escasos")))
        .andExpect(content().string(containsString("href=\"/estadisticas\" aria-current=\"page\"")));
  }

  @Test
  void fuenteCaidaMuestraGuionSinRomper() throws Exception {
    when(service.resumen("u1", "USER")).thenReturn(new Estadisticas(estados(1), List.of(), null));

    mvc.perform(get("/estadisticas").session(sesion()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("—")))
        .andExpect(content().string(containsString("No se pudo cargar")));
  }

  @Test
  void sinDatosMuestraEstadosVacios() throws Exception {
    when(service.resumen("u1", "USER")).thenReturn(new Estadisticas(estados(0), List.of(), List.of()));

    mvc.perform(get("/estadisticas").session(sesion()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Aún no tienes proyectos")))
        .andExpect(content().string(containsString("Ningún ítem del catálogo tiene receta")))
        .andExpect(content().string(containsString("No te falta nada")));
  }
}
