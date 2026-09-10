package cl.grupo5.proyectominecraft.model3d;

import cl.grupo5.proyectominecraft.items.Item;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = WebModel3DController.class)
class WebModel3DControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private ItemService itemService;

  private MockHttpSession session(String uid, String rol) {
    var session = new MockHttpSession();
    session.setAttribute("uid", uid);
    session.setAttribute("rol", rol);
    return session;
  }

  @Test
  void withoutSessionRedirectsToLogin() throws Exception {
    mvc.perform(get("/modelo-3d"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void withSessionRendersModeloWithCatalogItemsAndIsAdminFalse() throws Exception {
    var diamante = new Item();
    diamante.setId("diamante");
    diamante.setNombre("Diamante");
    when(itemService.list(null, null)).thenReturn(List.of(diamante));

    mvc.perform(get("/modelo-3d").session(session("user-1", "USUARIO")))
        .andExpect(status().isOk())
        .andExpect(view().name("modelo-3d"))
        .andExpect(model().attribute("items", List.of(diamante)))
        .andExpect(model().attribute("isAdmin", false));
  }

  @Test
  void withAdminSessionRendersModeloWithIsAdminTrue() throws Exception {
    when(itemService.list(null, null)).thenReturn(List.of());

    mvc.perform(get("/modelo-3d").session(session("admin-1", "ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("modelo-3d"))
        .andExpect(model().attribute("isAdmin", true));
  }
}