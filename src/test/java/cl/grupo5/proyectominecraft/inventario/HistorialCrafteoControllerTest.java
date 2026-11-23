package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.config.AdminAuthInterceptor;
import cl.grupo5.proyectominecraft.config.ApiAuthInterceptor;
import cl.grupo5.proyectominecraft.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = HistorialCrafteoController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class HistorialCrafteoControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private HistorialCrafteoService historial;

  @Test
  void apiCrafteosSinSesionDa401() throws Exception {
    mvc.perform(get("/api/crafteos")).andExpect(status().isUnauthorized());
  }

  @Test
  void apiCrafteosDevuelveListaDelUsuario() throws Exception {
    var r = new RegistroCrafteo();
    r.setItemId("antorcha");
    when(historial.listar("u1")).thenReturn(List.of(r));
    var s = new MockHttpSession();
    s.setAttribute("uid", "u1");
    mvc.perform(get("/api/crafteos").session(s))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].itemId").value("antorcha"));
  }
}
