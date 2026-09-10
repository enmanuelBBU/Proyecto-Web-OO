package cl.grupo5.proyectominecraft.items;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
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

  private MockHttpSession adminSession() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "admin-1");
    session.setAttribute("rol", "ADMIN");
    return session;
  }

  @Test
  void createWithBlankNombreRerendersItemsWithError() throws Exception {
    when(itemService.list(null, null)).thenReturn(List.of());
    var session = adminSession();

    mvc.perform(post("/items").session(session).param("nombre", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("items"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "El nombre del ítem es obligatorio."));
  }

  @Test
  void createRedirectsToItemsForNonAdminSessionWithoutCallingService() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");
    session.setAttribute("rol", "USUARIO");

    mvc.perform(post("/items").session(session).param("nombre", "Arcilla"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));

    org.mockito.Mockito.verify(itemService, org.mockito.Mockito.never()).create(any());
  }

  @Test
  void createWithBlankNombreForAdminSessionKeepsIsAdminTrue() throws Exception {
    when(itemService.list(null, null)).thenReturn(List.of());
    var session = new MockHttpSession();
    session.setAttribute("uid", "admin-1");
    session.setAttribute("rol", "ADMIN");

    mvc.perform(post("/items").session(session).param("nombre", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("items"))
        .andExpect(model().attribute("isAdmin", true));
  }

  @Test
  void updateWithBlankNombreRerendersItemEditWithError() throws Exception {
    var existing = new Item();
    existing.setNombre("Piedra");
    when(itemService.get("item-1")).thenReturn(existing);
    var session = adminSession();

    mvc.perform(post("/items/item-1/update").session(session).param("nombre", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("item-edit"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "El nombre del ítem es obligatorio."));
  }

  @Test
  void updateWithMissingItemRedirectsToItems() throws Exception {
    when(itemService.get("missing-1")).thenReturn(null);
    var session = adminSession();

    mvc.perform(post("/items/missing-1/update").session(session).param("nombre", "New Name"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));
  }

  @Test
  void updateExistingItemRedirectsWithSuccessToast() throws Exception {
    var existing = new Item();
    existing.setNombre("Piedra");
    when(itemService.get("piedra")).thenReturn(existing);
    var session = adminSession();

    mvc.perform(post("/items/piedra/update").session(session).param("nombre", "Piedra Lisa"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"))
        .andExpect(flash().attribute("toastSuccess", "Ítem actualizado correctamente"));
  }

  @Test
  void updateRedirectsToItemsForNonAdminSessionWithoutCallingService() throws Exception {
    var existing = new Item();
    existing.setNombre("Piedra");
    when(itemService.get("piedra")).thenReturn(existing);
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");
    session.setAttribute("rol", "USUARIO");

    mvc.perform(post("/items/piedra/update").session(session).param("nombre", "Piedra Lisa"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));

    org.mockito.Mockito.verify(itemService, org.mockito.Mockito.never()).update(any(), any());
  }

  @Test
  void deleteRedirectsWithSuccessToast() throws Exception {
    var session = adminSession();

    mvc.perform(post("/items/piedra/delete").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"))
        .andExpect(flash().attribute("toastSuccess", "Ítem eliminado correctamente"));

    org.mockito.Mockito.verify(itemService).delete("piedra");
  }

  @Test
  void deleteRedirectsToItemsForNonAdminSessionWithoutCallingService() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");
    session.setAttribute("rol", "USUARIO");

    mvc.perform(post("/items/piedra/delete").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));

    org.mockito.Mockito.verify(itemService, org.mockito.Mockito.never()).delete(any());
  }

  @Test
  void editWithMissingItemRedirectsToItems() throws Exception {
    when(itemService.get("missing-1")).thenReturn(null);
    var session = adminSession();

    mvc.perform(get("/items/missing-1/edit").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));
  }

  @Test
  void editRedirectsToItemsForNonAdminSessionWithoutCallingService() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");
    session.setAttribute("rol", "USUARIO");

    mvc.perform(get("/items/piedra/edit").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));

    org.mockito.Mockito.verify(itemService, org.mockito.Mockito.never()).get(any());
  }

  @Test
  void editRedirectsToLoginWhenNotAuthenticated() throws Exception {
    mvc.perform(get("/items/piedra/edit"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void listExposesIsAdminTrueForAdminSession() throws Exception {
    when(itemService.list(null, null)).thenReturn(List.of());
    var session = new MockHttpSession();
    session.setAttribute("uid", "admin-1");
    session.setAttribute("rol", "ADMIN");

    mvc.perform(get("/items").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("isAdmin", true));
  }

  @Test
  void listExposesIsAdminFalseForRegularSession() throws Exception {
    when(itemService.list(null, null)).thenReturn(List.of());
    var session = new MockHttpSession();
    session.setAttribute("uid", "user-1");
    session.setAttribute("rol", "USUARIO");

    mvc.perform(get("/items").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("isAdmin", false));
  }

  @Test
  void createDuplicateSlugRerendersItemsWithError() throws Exception {
    when(itemService.list(null, null)).thenReturn(List.of());
    doThrow(new ItemAlreadyExistsException("arcilla")).when(itemService).create(any());
    var session = adminSession();

    mvc.perform(post("/items").session(session).param("nombre", "Arcilla"))
        .andExpect(status().isOk())
        .andExpect(view().name("items"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "Ya existe un item con ese nombre."));
  }

  @Test
  void createInvalidRecipeRerendersItemsWithError() throws Exception {
    when(itemService.list(null, null)).thenReturn(List.of());
    doThrow(new RecipeValidationException("La receta debe tener exactamente 9 casillas."))
        .when(itemService).create(any());
    var session = adminSession();

    mvc.perform(post("/items").session(session).param("nombre", "Cama").param("slot", "lana"))
        .andExpect(status().isOk())
        .andExpect(view().name("items"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "La receta debe tener exactamente 9 casillas."));
  }

  @Test
  void createWithFixedCategoriaSeleccionUsesThatValue() throws Exception {
    var session = adminSession();

    mvc.perform(post("/items").session(session)
            .param("nombre", "Yunque")
            .param("categoriaSeleccion", "Utilidad"))
        .andExpect(status().is3xxRedirection())
        .andExpect(flash().attribute("toastSuccess", "Ítem creado correctamente"));

    var captor = org.mockito.ArgumentCaptor.forClass(Item.class);
    org.mockito.Mockito.verify(itemService).create(captor.capture());
    assertThat(captor.getValue().getCategoria()).isEqualTo("Utilidad");
  }

  @Test
  void createWithCategoriaOtraOverridesSeleccion() throws Exception {
    var session = adminSession();

    mvc.perform(post("/items").session(session)
            .param("nombre", "Estatua")
            .param("categoriaSeleccion", "otra")
            .param("categoriaOtra", "Decoración"))
        .andExpect(status().is3xxRedirection());

    var captor = org.mockito.ArgumentCaptor.forClass(Item.class);
    org.mockito.Mockito.verify(itemService).create(captor.capture());
    assertThat(captor.getValue().getCategoria()).isEqualTo("Decoración");
  }

  @Test
  void editExposesCategoriaSeleccionOtraForCustomCategoria() throws Exception {
    var existing = new Item();
    existing.setNombre("Estatua");
    existing.setCategoria("Decoración");
    when(itemService.get("estatua")).thenReturn(existing);
    var session = adminSession();

    mvc.perform(get("/items/estatua/edit").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("categoriaSeleccion", "otra"))
        .andExpect(model().attribute("categoriaOtra", "Decoración"));
  }

  @Test
  void editExposesCategoriaSeleccionForFixedCategoria() throws Exception {
    var existing = new Item();
    existing.setNombre("Yunque");
    existing.setCategoria("Utilidad");
    when(itemService.get("yunque")).thenReturn(existing);
    var session = adminSession();

    mvc.perform(get("/items/yunque/edit").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("categoriaSeleccion", "Utilidad"))
        .andExpect(model().attribute("categoriaOtra", ""));
  }

  @Test
  void materialesWithoutSessionRedirectsToLogin() throws Exception {
    mvc.perform(get("/materiales"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void materialesListsOnlyMateriaPrimaItems() throws Exception {
    var arcilla = new Item();
    arcilla.setNombre("Arcilla");
    when(itemService.list(null, true)).thenReturn(List.of(arcilla));
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");

    mvc.perform(get("/materiales").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("materiales"))
        .andExpect(model().attribute("materiales", List.of(arcilla)));
  }

  @Test
  void materialesPassesSearchQueryToService() throws Exception {
    when(itemService.list("lana", true)).thenReturn(List.of());
    var session = new MockHttpSession();
    session.setAttribute("uid", "someuid");

    mvc.perform(get("/materiales").session(session).param("q", "lana"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("q", "lana"));
  }

  @Test
  void editExposesNineSlotRecetaMatrizPaddedWithNulls() throws Exception {
    var existing = new Item();
    existing.setNombre("Cama");
    existing.setRecetaMatriz(List.of("lana", "lana"));
    when(itemService.get("cama")).thenReturn(existing);
    var session = adminSession();

    mvc.perform(get("/items/cama/edit").session(session))
        .andExpect(status().isOk())
        .andExpect(model().attribute("recetaMatriz", java.util.Arrays.asList(
            "lana", "lana", null, null, null, null, null, null, null)));
  }
}
