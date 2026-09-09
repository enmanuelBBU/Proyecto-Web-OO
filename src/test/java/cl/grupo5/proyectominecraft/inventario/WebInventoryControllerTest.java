package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.ItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = WebInventoryController.class)
class WebInventoryControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private InventoryService inventoryService;

  @MockitoBean
  private ItemService itemService;

  private MockHttpSession authenticated() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");
    return session;
  }

  @Test
  void viewWithoutSessionRedirectsToLogin() throws Exception {
    mvc.perform(get("/inventario"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void viewRendersInventoryTemplate() throws Exception {
    when(inventoryService.get("someuid")).thenReturn(Map.of());
    when(itemService.list(null, null)).thenReturn(List.of());

    mvc.perform(get("/inventario").session(authenticated()))
        .andExpect(status().isOk())
        .andExpect(view().name("inventario"));
  }

  @Test
  void addUnknownItemRerendersWithError() throws Exception {
    when(inventoryService.get("someuid")).thenReturn(Map.of());
    when(itemService.list(null, null)).thenReturn(List.of());
    doThrow(new InventoryException("El ítem no existe en el catálogo."))
        .when(inventoryService).add(eq("someuid"), eq("diamante"), anyLong());

    mvc.perform(post("/inventario/agregar").session(authenticated())
            .param("itemId", "diamante").param("cantidad", "3"))
        .andExpect(status().isOk())
        .andExpect(view().name("inventario"))
        .andExpect(model().attributeExists("error"));
  }
}
