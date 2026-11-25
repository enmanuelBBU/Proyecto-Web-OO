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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
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
        .andExpect(flash().attribute("toastSuccess", "¡Crafteo exitoso! Quedó registrado en tu historial."));
  }

  @Test
  void calculadoraMuestraFundirParaRecetaDeHorno() throws Exception {
    var plan = new CraftingPlan();
    plan.setTargetItemId("lingote");
    plan.setCantidad(1);
    plan.setMateriales(List.of());
    plan.setCraftable(true);
    plan.setTipoReceta("HORNO");
    when(itemService.list(null, false)).thenReturn(List.of());
    when(calculator.plan("someuid", "lingote", 1)).thenReturn(plan);
    mvc.perform(get("/calculadora").param("targetId", "lingote").session(authenticated()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString(">Fundir<")));
  }

  private static NodoCrafteo nodo(String id, String nombre, String tipo, long req, long usado, long fabricar, long falta,
                                  NodoCrafteo... hijos) {
    var n = new NodoCrafteo();
    n.setItemId(id);
    n.setNombre(nombre);
    n.setTipo(tipo);
    n.setRequerido(req);
    n.setUsadoDeInventario(usado);
    n.setAFabricar(fabricar);
    n.setFaltante(falta);
    n.setHijos(new java.util.ArrayList<>(List.of(hijos)));
    return n;
  }

  @Test
  void calculadoraMuestraArbolConIntermediosYHorno() throws Exception {
    var crudo = nodo("hierro_crudo", "Hierro crudo", NodoCrafteo.MATERIA_PRIMA, 3, 1, 0, 2);
    var lingote = nodo("lingote", "Lingote de hierro", NodoCrafteo.HORNO, 3, 0, 3, 0, crudo);
    var tablones = nodo("tablones", "Tablones", NodoCrafteo.CRAFTEO, 8, 4, 4, 0);
    var raiz = nodo("cubeta", "Cubeta", NodoCrafteo.CRAFTEO, 1, 0, 1, 0, lingote, tablones);
    var plan = new CraftingPlan();
    plan.setTargetItemId("cubeta");
    plan.setCantidad(1);
    plan.setArbol(List.of(raiz));
    plan.setConsumidos(List.of(new MaterialCantidad("tablones", "Tablones", 4)));
    plan.setFaltantes(List.of(new MaterialCantidad("hierro_crudo", "Hierro crudo", 2)));
    plan.setUsaIntermedios(true);
    plan.setTipoReceta("CRAFTEO");
    when(itemService.list(null, false)).thenReturn(List.of());
    when(calculator.plan("someuid", "cubeta", 1)).thenReturn(plan);

    mvc.perform(get("/calculadora").param("targetId", "cubeta").session(authenticated()))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("usando 4 de tu inventario")))
        .andExpect(content().string(containsString("fabricar 4")))
        .andExpect(content().string(containsString("Fundir 3 Hierro crudo → 3 Lingote de hierro")))
        .andExpect(content().string(containsString("badge-horno")))
        .andExpect(content().string(containsString("incluidos ítems intermedios")))
        .andExpect(content().string(containsString("faltan 2")));
  }
}
