package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.auditoria.AuditService;
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
  private AuditService audit;

  @MockitoBean
  private ProyectoService proyectoService;

  @MockitoBean
  private cl.grupo5.proyectominecraft.inventario.CraftingCalculatorService calculator;

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
    when(proyectoService.list(eq(null), eq(null), eq(null), any(), any())).thenReturn(List.of(p));

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
    when(proyectoService.getVisible(any(), any(), any())).thenReturn(new Proyecto());
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
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);

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
    when(proyectoService.getVisible(any(), any(), any())).thenReturn(new Proyecto());
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

  @Test
  void apiGetProyectoPrivadoAjenoDa404() throws Exception {
    when(proyectoService.getVisible("p1", "user123", "USER")).thenReturn(null);
    mvc.perform(get("/api/proyectos/p1").session(authenticated())).andExpect(status().isNotFound());
  }

  @Test
  void apiInvitarSinPermisoDa403() throws Exception {
    when(proyectoService.getVisible(eq("p1"), any(), any())).thenReturn(new Proyecto());
    when(proyectoService.invitar(eq("p1"), eq("a@x.cl"), eq("LECTURA"), any(), any()))
        .thenThrow(new ProyectoValidationException("No tienes permisos para gestionar los miembros de este proyecto."));
    mvc.perform(post("/api/proyectos/p1/miembros").session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"a@x.cl\",\"permiso\":\"LECTURA\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void apiInvitarEmailDesconocidoDa400() throws Exception {
    when(proyectoService.invitar(eq("p1"), eq("nadie@x.cl"), eq("LECTURA"), any(), any()))
        .thenThrow(new ProyectoValidationException("No existe un usuario registrado con ese email."));
    mvc.perform(post("/api/proyectos/p1/miembros").session(authenticated())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nadie@x.cl\",\"permiso\":\"LECTURA\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void apiListarMiembrosDeProyectoVisible() throws Exception {
    var p = new Proyecto();
    p.setId("p1");
    p.getMiembros().add(new Miembro("u2", "Alex", "a@x.cl", "LECTURA", "t"));
    when(proyectoService.getVisible("p1", "user123", "USER")).thenReturn(p);
    mvc.perform(get("/api/proyectos/p1/miembros").session(authenticated()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].uid").value("u2"));
  }

  @Test
  void apiQuitarMiembroInexistenteProyectoDa404() throws Exception {
    when(proyectoService.quitarMiembro(eq("p9"), eq("u2"), any(), any())).thenReturn(null);
    mvc.perform(delete("/api/proyectos/p9/miembros/u2").session(authenticated()))
        .andExpect(status().isNotFound());
  }

  // ---- Revisión final: escrituras sobre proyectos no visibles dan 404 ----

  private static final ProyectoValidationException SIN_PERMISO =
      new ProyectoValidationException("No tienes permisos para modificar este proyecto.");

  @Test
  void apiUpdateProyectoAjenoDa404() throws Exception {
    when(proyectoService.update(eq("ajeno"), any(), any(), any())).thenThrow(SIN_PERMISO);
    mvc.perform(put("/api/proyectos/ajeno").session(authenticated())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void apiDeleteProyectoAjenoDa404() throws Exception {
    when(proyectoService.delete(eq("ajeno"), any(), any())).thenThrow(SIN_PERMISO);
    mvc.perform(delete("/api/proyectos/ajeno").session(authenticated())).andExpect(status().isNotFound());
  }

  @Test
  void apiEstadoProyectoAjenoDa404() throws Exception {
    when(proyectoService.transicionarEstado(eq("ajeno"), any(), any(), any(), any(), any())).thenThrow(SIN_PERMISO);
    mvc.perform(post("/api/proyectos/ajeno/estado").session(authenticated())
            .contentType(MediaType.APPLICATION_JSON).content("{\"estado\":\"EN_CONSTRUCCION\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void apiInvitarEnProyectoAjenoDa404() throws Exception {
    when(proyectoService.invitar(eq("ajeno"), any(), any(), any(), any())).thenThrow(SIN_PERMISO);
    mvc.perform(post("/api/proyectos/ajeno/miembros").session(authenticated())
            .contentType(MediaType.APPLICATION_JSON).content("{\"email\":\"a@x.cl\",\"permiso\":\"LECTURA\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void apiCambiarPermisoEnProyectoAjenoDa404() throws Exception {
    when(proyectoService.cambiarPermisoMiembro(eq("ajeno"), any(), any(), any(), any())).thenThrow(SIN_PERMISO);
    mvc.perform(put("/api/proyectos/ajeno/miembros/u2").session(authenticated())
            .contentType(MediaType.APPLICATION_JSON).content("{\"permiso\":\"EDICION\"}"))
        .andExpect(status().isNotFound());
  }

  @Test
  void apiQuitarMiembroEnProyectoAjenoDa404() throws Exception {
    when(proyectoService.quitarMiembro(eq("ajeno"), any(), any(), any())).thenThrow(SIN_PERMISO);
    mvc.perform(delete("/api/proyectos/ajeno/miembros/u2").session(authenticated())).andExpect(status().isNotFound());
  }

  @Test
  void apiLectorQueIntentaEditarRecibe403() throws Exception {
    var p = new Proyecto();
    p.setId("visible");
    when(proyectoService.getVisible("visible", "user123", "USER")).thenReturn(p);
    when(proyectoService.update(eq("visible"), any(), any(), any())).thenThrow(SIN_PERMISO);
    mvc.perform(put("/api/proyectos/visible").session(authenticated())
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"X\"}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void faltantesDeProyectoNoVisibleEs404() throws Exception {
    when(proyectoService.getVisible(eq("ajeno"), any(), any())).thenReturn(null);
    mvc.perform(get("/api/proyectos/ajeno/faltantes").session(authenticated()))
        .andExpect(status().isNotFound());
  }

  @Test
  void faltantesDeProyectoVisibleDevuelveArbol() throws Exception {
    var p = new Proyecto();
    p.setId("castillo");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);
    var n = new cl.grupo5.proyectominecraft.inventario.NodoCrafteo();
    n.setItemId("pala");
    n.setAFabricar(2);
    var r = new cl.grupo5.proyectominecraft.inventario.ResultadoFaltantes();
    r.setArbol(List.of(n));
    r.setFaltantes(List.of(new cl.grupo5.proyectominecraft.inventario.MaterialCantidad("tronco", "Tronco", 3)));
    when(calculator.faltantesProyecto(p, "user123")).thenReturn(r);

    mvc.perform(get("/api/proyectos/castillo/faltantes").session(authenticated()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.arbol[0].aFabricar").value(2))
        .andExpect(jsonPath("$.faltantes[0].itemId").value("tronco"));
  }

  @Test
  void faltantesConItemRotoEs400() throws Exception {
    var p = new Proyecto();
    p.setId("castillo");
    when(proyectoService.getVisible(eq("castillo"), any(), any())).thenReturn(p);
    when(calculator.faltantesProyecto(p, "user123"))
        .thenThrow(new cl.grupo5.proyectominecraft.inventario.InventoryException("El ítem 'x' no existe en el catálogo."));
    mvc.perform(get("/api/proyectos/castillo/faltantes").session(authenticated()))
        .andExpect(status().isBadRequest());
  }
}
