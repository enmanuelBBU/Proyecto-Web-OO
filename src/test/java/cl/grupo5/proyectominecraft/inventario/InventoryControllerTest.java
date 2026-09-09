package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.config.AdminAuthInterceptor;
import cl.grupo5.proyectominecraft.config.ApiAuthInterceptor;
import cl.grupo5.proyectominecraft.config.CorsConfig;
import cl.grupo5.proyectominecraft.items.Item;
import cl.grupo5.proyectominecraft.items.ItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = InventoryController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class InventoryControllerTest {

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
  void getWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(get("/api/inventario"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void getReturnsEntriesWithResolvedNames() throws Exception {
    when(inventoryService.get("someuid")).thenReturn(Map.of("tronco", 5L));
    var item = new Item();
    item.setNombre("Tronco");
    when(itemService.get("tronco")).thenReturn(item);

    mvc.perform(get("/api/inventario").session(authenticated()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].itemId").value("tronco"))
        .andExpect(jsonPath("$[0].nombre").value("Tronco"))
        .andExpect(jsonPath("$[0].cantidad").value(5));
  }

  @Test
  void setUnknownItemIsBadRequest() throws Exception {
    doThrow(new InventoryException("El ítem no existe en el catálogo."))
        .when(inventoryService).set(eq("someuid"), eq("diamante"), anyLong());

    mvc.perform(put("/api/inventario/items/diamante")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"cantidad\":3}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void setNonNumericCantidadIsBadRequest() throws Exception {
    mvc.perform(put("/api/inventario/items/tronco")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"cantidad\":\"mucha\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void removeEntryReturnsUpdatedInventory() throws Exception {
    when(inventoryService.set("someuid", "tronco", 0)).thenReturn(Map.of());

    mvc.perform(delete("/api/inventario/items/tronco").session(authenticated()))
        .andExpect(status().isOk());
  }
}
