package cl.grupo5.proyectominecraft.items;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ItemController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class ItemControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private ItemService itemService;

  private MockHttpSession authenticated() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");
    return session;
  }

  @Test
  void createWithBlankNombreIsBadRequest() throws Exception {
    mvc.perform(post("/api/items")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void updateUnknownIdIsNotFound() throws Exception {
    when(itemService.update(eq("missing-1"), any())).thenReturn(null);

    mvc.perform(put("/api/items/missing-1")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Piedra\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void deleteUnknownIdIsNotFound() throws Exception {
    when(itemService.delete("missing-1")).thenReturn(false);

    mvc.perform(delete("/api/items/missing-1").session(authenticated()))
        .andExpect(status().isNotFound());
  }

  @Test
  void createDuplicateSlugIsConflict() throws Exception {
    doThrow(new ItemAlreadyExistsException("arcilla")).when(itemService).create(any());

    mvc.perform(post("/api/items")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Arcilla\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void createInvalidRecipeIsBadRequest() throws Exception {
    doThrow(new RecipeValidationException("La receta debe tener exactamente 9 casillas."))
        .when(itemService).create(any());

    mvc.perform(post("/api/items")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Cama\",\"recetaMatriz\":[\"lana\"]}"))
        .andExpect(status().isBadRequest());
  }
}
