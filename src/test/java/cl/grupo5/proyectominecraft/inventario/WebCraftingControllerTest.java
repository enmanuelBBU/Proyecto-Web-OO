package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.items.ItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = WebCraftingController.class)
class WebCraftingControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private CraftingCalculatorService calculator;

  @MockitoBean
  private ItemService itemService;

  private MockHttpSession authenticated() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");
    return session;
  }

  @Test
  void viewWithoutSessionRedirectsToLogin() throws Exception {
    mvc.perform(get("/calculadora"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void viewWithoutTargetRendersTemplateWithoutPlan() throws Exception {
    when(itemService.list(null, false)).thenReturn(List.of());

    mvc.perform(get("/calculadora").session(authenticated()))
        .andExpect(status().isOk())
        .andExpect(view().name("calculadora"))
        .andExpect(model().attributeDoesNotExist("plan"));
  }

  @Test
  void craftWithoutMaterialsRerendersWithError() throws Exception {
    when(itemService.list(null, false)).thenReturn(List.of());
    doThrow(new InventoryException("No tienes suficientes materiales."))
        .when(calculator).craft(eq("someuid"), eq("pala"), anyInt());

    mvc.perform(post("/calculadora/craftear").session(authenticated())
            .param("itemId", "pala").param("cantidad", "1"))
        .andExpect(status().isOk())
        .andExpect(view().name("calculadora"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "No tienes suficientes materiales."));
  }

  @Test
  void craftSuccessRedirectsWithSuccessToast() throws Exception {
    mvc.perform(post("/calculadora/craftear").session(authenticated())
            .param("itemId", "pala").param("cantidad", "1"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/calculadora?targetId=pala&cantidad=1"))
        .andExpect(flash().attribute("toastSuccess", "¡Crafteo exitoso!"));
  }
}
