package cl.grupo5.proyectominecraft.proyectos;

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

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ProyectoController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class ProyectoControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private ProyectoService proyectoService;

  private MockHttpSession authenticated() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "user123");
    session.setAttribute("nombre", "Steve");
    session.setAttribute("rol", "USER");
    return session;
  }

  @Test
  void listWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(get("/api/proyectos"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void listWithSessionReturnsOk() throws Exception {
    var p = new Proyecto();
    p.setId("castillo");
    p.setNombre("Castillo");
    when(proyectoService.list(null, null, null)).thenReturn(List.of(p));

    mvc.perform(get("/api/proyectos").session(authenticated()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("castillo"));
  }

  @Test
  void createWithBlankNombreIsBadRequest() throws Exception {
    mvc.perform(post("/api/proyectos")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void createDuplicateIsConflict() throws Exception {
    doThrow(new ProyectoAlreadyExistsException("castillo"))
        .when(proyectoService).create(any(), any(), any());

    mvc.perform(post("/api/proyectos")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Castillo\"}"))
        .andExpect(status().isConflict());
  }

  @Test
  void updateForbiddenIsForbidden() throws Exception {
    doThrow(new ProyectoValidationException("No tienes permisos para modificar este proyecto."))
        .when(proyectoService).update(eq("p1"), any(), any(), any());

    mvc.perform(put("/api/proyectos/p1")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Castillo Modificado\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void deleteUnknownIdIsNotFound() throws Exception {
    when(proyectoService.delete(eq("missing-p"), any(), any())).thenReturn(false);

    mvc.perform(delete("/api/proyectos/missing-p").session(authenticated()))
        .andExpect(status().isNotFound());
  }

  @Test
  void getEstadosReturnsOk() throws Exception {
    var p = new Proyecto();
    p.setId("castillo");
    p.setEstado("PLANIFICACION");
    when(proyectoService.get("castillo")).thenReturn(p);

    mvc.perform(get("/api/proyectos/castillo/estados").session(authenticated()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estadoActual").value("PLANIFICACION"))
        .andExpect(jsonPath("$.transiciones").isArray());
  }

  @Test
  void updateStateSuccess() throws Exception {
    var p = new Proyecto();
    p.setId("castillo");
    p.setEstado("EN_CONSTRUCCION");

    when(proyectoService.transicionarEstado(eq("castillo"), eq("EN_CONSTRUCCION"), any(), any(), any(), any()))
        .thenReturn(p);

    mvc.perform(post("/api/proyectos/castillo/estado")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"estado\":\"EN_CONSTRUCCION\",\"motivo\":\"Inicio\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.estado").value("EN_CONSTRUCCION"));
  }

  @Test
  void updateStateForbidden() throws Exception {
    doThrow(new ProyectoValidationException("No tienes permisos para cambiar el estado de este proyecto."))
        .when(proyectoService).transicionarEstado(any(), any(), any(), any(), any(), any());

    mvc.perform(post("/api/proyectos/castillo/estado")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"estado\":\"EN_CONSTRUCCION\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void updateStateInvalid() throws Exception {
    doThrow(new ProyectoValidationException("Transición inválida"))
        .when(proyectoService).transicionarEstado(any(), any(), any(), any(), any(), any());

    mvc.perform(post("/api/proyectos/castillo/estado")
            .session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"estado\":\"COMPLETADO\"}"))
        .andExpect(status().isBadRequest());
  }
}
