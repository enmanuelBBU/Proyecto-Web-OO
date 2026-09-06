package cl.grupo5.proyectominecraft.items;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = WebItemController.class)
class WebItemControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private ItemService itemService;

  @Test
  void createWithBlankNombreRerendersItemsWithError() throws Exception {
    when(itemService.list(null, null)).thenReturn(List.of());
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");

    mvc.perform(post("/items").session(session).param("nombre", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("items"))
        .andExpect(model().attributeExists("error"));
  }

  @Test
  void updateWithBlankNombreRerendersItemEditWithError() throws Exception {
    var existing = new Item();
    existing.setNombre("Piedra");
    when(itemService.get("item-1")).thenReturn(existing);
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");

    mvc.perform(post("/items/item-1/update").session(session).param("nombre", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("item-edit"))
        .andExpect(model().attributeExists("error"));
  }

  @Test
  void updateWithMissingItemRedirectsToItems() throws Exception {
    when(itemService.get("missing-1")).thenReturn(null);
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");

    mvc.perform(post("/items/missing-1/update").session(session).param("nombre", "New Name"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));
  }

  @Test
  void editWithMissingItemRedirectsToItems() throws Exception {
    when(itemService.get("missing-1")).thenReturn(null);
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");

    mvc.perform(get("/items/missing-1/edit").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));
  }
}
