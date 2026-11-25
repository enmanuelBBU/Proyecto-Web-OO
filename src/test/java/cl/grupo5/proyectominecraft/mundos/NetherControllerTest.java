package cl.grupo5.proyectominecraft.mundos;

import cl.grupo5.proyectominecraft.config.AdminAuthInterceptor;
import cl.grupo5.proyectominecraft.config.ApiAuthInterceptor;
import cl.grupo5.proyectominecraft.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = NetherController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class NetherControllerTest {

  @Autowired
  private MockMvc mvc;

  private static MockHttpSession sesion() {
    var s = new MockHttpSession();
    s.setAttribute("uid", "u1");
    return s;
  }

  @Test
  void sinSesionEs401() throws Exception {
    mvc.perform(get("/api/nether/convertir").param("dimension", "OVERWORLD").param("x", "0").param("y", "64").param("z", "0"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void conviertePorParametros() throws Exception {
    mvc.perform(get("/api/nether/convertir").session(sesion())
            .param("dimension", "overworld").param("x", "-9").param("y", "64").param("z", "16"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.dimension").value("NETHER"))
        .andExpect(jsonPath("$.x").value(-2))
        .andExpect(jsonPath("$.y").value(64))
        .andExpect(jsonPath("$.z").value(2));
  }

  @Test
  void fueraDeRangoEs400() throws Exception {
    mvc.perform(get("/api/nether/convertir").session(sesion())
            .param("dimension", "NETHER").param("x", "0").param("y", "300").param("z", "0"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void dimensionInvalidaEs400() throws Exception {
    mvc.perform(get("/api/nether/convertir").session(sesion())
            .param("dimension", "END").param("x", "0").param("y", "64").param("z", "0"))
        .andExpect(status().isBadRequest());
  }
}
