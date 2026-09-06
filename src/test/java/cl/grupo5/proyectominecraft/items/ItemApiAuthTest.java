package cl.grupo5.proyectominecraft.items;

import cl.grupo5.proyectominecraft.config.AdminAuthInterceptor;
import cl.grupo5.proyectominecraft.config.ApiAuthInterceptor;
import cl.grupo5.proyectominecraft.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.util.List;

import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ItemController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class ItemApiAuthTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private ItemService itemService;

  @Test
  void listWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.get("/api/items"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listWithSessionIsOk() throws Exception {
    org.mockito.Mockito.when(itemService.list(null, null)).thenReturn(List.of());
    var session = new org.springframework.mock.web.MockHttpSession();
    session.setAttribute("uid", "someuid");
    mvc.perform(MockMvcRequestBuilders.get("/api/items").session(session))
        .andExpect(status().isOk());
  }

  @Test
  void createWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.post("/api/items")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Piedra\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void updateWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.put("/api/items/abc")
            .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Piedra\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(MockMvcRequestBuilders.delete("/api/items/abc"))
        .andExpect(status().isUnauthorized());
  }
}
