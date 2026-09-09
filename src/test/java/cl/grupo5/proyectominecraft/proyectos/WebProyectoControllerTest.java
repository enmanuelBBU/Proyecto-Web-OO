package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.items.ItemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebProyectoController.class)
class WebProyectoControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private ProyectoService proyectoService;

  @MockitoBean
  private ItemService itemService;

  private MockHttpSession userSession(String uid) {
    var session = new MockHttpSession();
    session.setAttribute("uid", uid);
    session.setAttribute("nombre", "Steve");
    session.setAttribute("rol", "USER");
    return session;
  }

  @Test
  void proyectosWithoutSessionRedirectsToLogin() throws Exception {
    mvc.perform(get("/proyectos"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void listProyectosExposesModelAttributes() throws Exception {
    var session = userSession("user123");
    when(proyectoService.list(null, null, null)).thenReturn(List.of());
    when(itemService.list(null, null)).thenReturn(List.of());

    mvc.perform(get("/proyectos").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("proyectos"))
        .andExpect(model().attributeExists("proyectos"))
        .andExpect(model().attributeExists("catalogItems"))
        .andExpect(model().attribute("currentUid", "user123"));
  }

  @Test
  void listProyectosWithMisProyectosFilter() throws Exception {
    var session = userSession("user123");
    when(proyectoService.list(null, null, "user123")).thenReturn(List.of());
    when(itemService.list(null, null)).thenReturn(List.of());

    mvc.perform(get("/proyectos").session(session).param("misProyectos", "true"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("misProyectos", true));
  }

  @Test
  void createProyectoRedirectsOnSuccess() throws Exception {
    var session = userSession("user123");

    mvc.perform(post("/proyectos").session(session)
            .param("nombre", "Granja de Hierro")
            .param("descripcion", "Granja automatica")
            .param("estado", "PLANIFICACION"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos"))
        .andExpect(flash().attribute("toastSuccess", "Proyecto creado correctamente"));
  }

  @Test
  void createProyectoRerendersWithErrorWhenValidationFails() throws Exception {
    var session = userSession("user123");
    doThrow(new ProyectoValidationException("El nombre del proyecto es obligatorio."))
        .when(proyectoService).create(any(), any(), any());

    mvc.perform(post("/proyectos").session(session).param("nombre", ""))
        .andExpect(status().isOk())
        .andExpect(view().name("proyectos"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "El nombre del proyecto es obligatorio."));
  }

  @Test
  void detailWithMissingProyectoRedirectsToProyectos() throws Exception {
    var session = userSession("user123");
    when(proyectoService.get("missing-id")).thenReturn(null);

    mvc.perform(get("/proyectos/missing-id").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos"));
  }

  @Test
  void detailExposesProyectoAndCanEdit() throws Exception {
    var session = userSession("user123");
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid("user123");
    when(proyectoService.get("castillo")).thenReturn(p);
    when(itemService.list(null, null)).thenReturn(List.of());

    mvc.perform(get("/proyectos/castillo").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("proyecto-detalle"))
        .andExpect(model().attribute("canEdit", true));
  }

  @Test
  void editFormForbiddenForNonOwnerRedirectsToDetail() throws Exception {
    var session = userSession("otherUser");
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid("user123");
    when(proyectoService.get("castillo")).thenReturn(p);

    mvc.perform(get("/proyectos/castillo/edit").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos/castillo"));
  }

  @Test
  void updateForbiddenRerendersEditWithError() throws Exception {
    var session = userSession("user123");
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid("user123");
    when(proyectoService.get("castillo")).thenReturn(p);
    doThrow(new ProyectoValidationException("El estado 'INVALID' no es válido."))
        .when(proyectoService).update(eq("castillo"), any(), any(), any());

    mvc.perform(post("/proyectos/castillo/update").session(session)
            .param("nombre", "Castillo")
            .param("estado", "INVALID"))
        .andExpect(status().isOk())
        .andExpect(view().name("proyecto-edit"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "El estado 'INVALID' no es válido."));
  }

  @Test
  void updateRedirectsWithSuccessToast() throws Exception {
    var session = userSession("user123");
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    p.setCreadorUid("user123");
    when(proyectoService.get("castillo")).thenReturn(p);

    mvc.perform(post("/proyectos/castillo/update").session(session)
            .param("nombre", "Castillo Nuevo")
            .param("estado", "EN_CONSTRUCCION"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos/castillo"))
        .andExpect(flash().attribute("toastSuccess", "Proyecto actualizado correctamente"));
  }

  @Test
  void deleteRedirectsToProyectos() throws Exception {
    var session = userSession("user123");

    mvc.perform(post("/proyectos/castillo/delete").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos"))
        .andExpect(flash().attribute("toastSuccess", "Proyecto eliminado correctamente"));
  }

  @Test
  void estadosExposesModelAttributes() throws Exception {
    var session = userSession("user123");
    var p = new Proyecto();
    p.setId("castillo");
    p.setEstado("PLANIFICACION");
    p.setCreadorUid("user123");
    when(proyectoService.get("castillo")).thenReturn(p);

    mvc.perform(get("/proyectos/castillo/estados").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("proyecto-estados"))
        .andExpect(model().attributeExists("transicionesDisponibles"))
        .andExpect(model().attribute("canEdit", true));
  }

  @Test
  void transicionarRedirectsOnSuccess() throws Exception {
    var session = userSession("user123");

    mvc.perform(post("/proyectos/castillo/transicionar").session(session)
            .param("nuevoEstado", "EN_CONSTRUCCION")
            .param("motivo", "Start"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos/castillo/estados"))
        .andExpect(flash().attribute("toastSuccess", "Estado actualizado a EN_CONSTRUCCION"));
  }

  @Test
  void transicionarRerendersWithErrorOnFailure() throws Exception {
    var session = userSession("user123");
    var p = new Proyecto();
    p.setId("castillo");
    p.setEstado("PLANIFICACION");
    when(proyectoService.get("castillo")).thenReturn(p);

    doThrow(new ProyectoValidationException("Transición inválida"))
        .when(proyectoService).transicionarEstado(any(), any(), any(), any(), any(), any());

    mvc.perform(post("/proyectos/castillo/transicionar").session(session)
            .param("nuevoEstado", "COMPLETADO"))
        .andExpect(status().isOk())
        .andExpect(view().name("proyecto-estados"))
        .andExpect(model().attributeExists("error"))
        .andExpect(model().attribute("toastError", "Transición inválida"));
  }
}
