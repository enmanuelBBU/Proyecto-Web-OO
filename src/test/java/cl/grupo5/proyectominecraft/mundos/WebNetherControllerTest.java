package cl.grupo5.proyectominecraft.mundos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebNetherController.class)
class WebNetherControllerTest {

  @Autowired
  private MockMvc mvc;

  private static MockHttpSession sesion() {
    var s = new MockHttpSession();
    s.setAttribute("uid", "u1");
    s.setAttribute("rol", "USER");
    return s;
  }

  @Test
  void sinSesionRedirigeALogin() throws Exception {
    mvc.perform(get("/calculadora-nether")).andExpect(redirectedUrl("/login"));
  }

  @Test
  void sinParametrosMuestraFormulario() throws Exception {
    mvc.perform(get("/calculadora-nether").session(sesion()))
        .andExpect(status().isOk())
        .andExpect(view().name("calculadora-nether"))
        .andExpect(model().attributeDoesNotExist("conversion"))
        .andExpect(content().string(containsString("Calculadora Nether")));
  }

  @Test
  void convierteYMuestraResultado() throws Exception {
    mvc.perform(get("/calculadora-nether").session(sesion())
            .param("dimension", "OVERWORLD").param("x", "-9").param("y", "70").param("z", "100"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("conversion", new Coordenadas("NETHER", -2, 70, 12)))
        .andExpect(content().string(containsString("X -2 / Y 70 / Z 12")));
  }

  @Test
  void entradaInvalidaMuestraErrorSinRomper() throws Exception {
    mvc.perform(get("/calculadora-nether").session(sesion())
            .param("dimension", "NETHER").param("x", "0").param("y", "300").param("z", "0"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("netherError", "La coordenada Y debe estar entre 0 y 255."));
  }

  @Test
  void navMarcaCalculadoraNether() throws Exception {
    mvc.perform(get("/calculadora-nether").session(sesion()))
        .andExpect(content().string(containsString("href=\"/calculadora-nether\" aria-current=\"page\"")));
  }
}
