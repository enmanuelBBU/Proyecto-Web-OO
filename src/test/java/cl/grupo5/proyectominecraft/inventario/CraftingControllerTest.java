package cl.grupo5.proyectominecraft.inventario;

import cl.grupo5.proyectominecraft.config.AdminAuthInterceptor;
import cl.grupo5.proyectominecraft.config.ApiAuthInterceptor;
import cl.grupo5.proyectominecraft.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = CraftingController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class CraftingControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private CraftingCalculatorService calculator;

  private MockHttpSession authenticated() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");
    return session;
  }

  private CraftingPlan craftablePlan() {
    var fila = new MaterialRequirement();
    fila.setItemId("tronco");
    fila.setNombre("Tronco");
    fila.setRequerido(8L);
    fila.setDisponible(8L);
    fila.setFaltante(0L);
    var plan = new CraftingPlan();
    plan.setTargetItemId("pala");
    plan.setCantidad(1);
    plan.setMateriales(List.of(fila));
    plan.setCraftable(true);
    return plan;
  }

  @Test
  void planWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(get("/api/calculadora/plan/pala"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void planReturnsCalculatorResult() throws Exception {
    when(calculator.plan("someuid", "pala", 1)).thenReturn(craftablePlan());

    mvc.perform(get("/api/calculadora/plan/pala").session(authenticated()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.craftable").value(true))
        .andExpect(jsonPath("$.materiales[0].faltante").value(0));
  }

  @Test
  void planForRawMaterialIsBadRequest() throws Exception {
    doThrow(new InventoryException("Las materias primas no se craftean."))
        .when(calculator).plan(eq("someuid"), eq("tronco"), anyInt());

    mvc.perform(get("/api/calculadora/plan/tronco").session(authenticated()))
        .andExpect(status().isBadRequest());
  }

  @Test
  void craftWhenNotCraftableIsBadRequest() throws Exception {
    doThrow(new InventoryException("No tienes suficientes materiales."))
        .when(calculator).craft(eq("someuid"), eq("pala"), anyInt());

    mvc.perform(post("/api/calculadora/craftear")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"itemId\":\"pala\",\"cantidad\":1}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void craftWithoutItemIdIsBadRequest() throws Exception {
    mvc.perform(post("/api/calculadora/craftear")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"cantidad\":1}"))
        .andExpect(status().isBadRequest());
  }
}
