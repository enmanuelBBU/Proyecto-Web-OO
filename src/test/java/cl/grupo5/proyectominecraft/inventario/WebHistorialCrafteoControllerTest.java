package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.ItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebHistorialCrafteoController.class)
class WebHistorialCrafteoControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private HistorialCrafteoService historial;

  @MockitoBean
  private ItemService itemService;

  @Test
  void historialSinSesionRedirige() throws Exception {
    mvc.perform(get("/historial-crafteo")).andExpect(redirectedUrl("/login"));
  }

  @Test
  void historialExponeRegistrosDelUsuario() throws Exception {
    var r = new RegistroCrafteo();
    r.setItemId("antorcha");
    r.setItemNombre("Antorcha");
    r.setCantidad(4);
    r.setTipoReceta("CRAFTEO");
    r.setFecha("2026-11-23T10:00:00Z");
    var mc = new MaterialConsumido();
    mc.setItemId("carbon");
    mc.setNombre("Carbón");
    mc.setCantidad(4);
    r.setMateriales(List.of(mc));
    when(historial.listar("u1")).thenReturn(List.of(r));
    when(itemService.list(null, null)).thenReturn(List.of());
    var s = new MockHttpSession();
    s.setAttribute("uid", "u1");
    mvc.perform(get("/historial-crafteo").session(s))
        .andExpect(status().isOk())
        .andExpect(view().name("historial-crafteo"))
        .andExpect(model().attribute("registros", List.of(r)));
  }
}
