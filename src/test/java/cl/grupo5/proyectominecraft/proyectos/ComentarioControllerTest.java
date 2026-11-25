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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = ComentarioController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class ComentarioControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private ProyectoService proyectoService;

  @MockitoBean
  private ComentarioService comentarioService;

  @MockitoBean
  private AuditService audit;

  private static MockHttpSession sesion(String uid, String rol) {
    var s = new MockHttpSession();
    s.setAttribute("uid", uid);
    s.setAttribute("nombre", "Steve");
    s.setAttribute("rol", rol);
    return s;
  }

  private void proyectoVisible() throws Exception {
    var p = new Proyecto();
    p.setId("p1");
    p.setNombre("Castillo");
    p.setCreadorUid("creador");
    when(proyectoService.getVisible(eq("p1"), any(), any())).thenReturn(p);
  }

  private void comentarioDe(String autor) throws Exception {
    var c = new Comentario();
    c.setId("c1");
    c.setAutorUid(autor);
    when(comentarioService.get("p1", "c1")).thenReturn(c);
  }

  private static String json(String texto) {
    return "{\"texto\":\"" + texto + "\"}";
  }

  @Test
  void sinSesionEs401() throws Exception {
    mvc.perform(get("/api/proyectos/p1/comentarios")).andExpect(status().isUnauthorized());
  }

  @Test
  void listarProyectoNoVisibleEs404() throws Exception {
    mvc.perform(get("/api/proyectos/p1/comentarios").session(sesion("u1", "USER")))
        .andExpect(status().isNotFound());
  }

  @Test
  void listarDevuelveComentarios() throws Exception {
    proyectoVisible();
    var c = new Comentario();
    c.setTexto("hola");
    when(comentarioService.listar("p1")).thenReturn(List.of(c));
    mvc.perform(get("/api/proyectos/p1/comentarios").session(sesion("u1", "USER")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].texto").value("hola"));
  }

  @Test
  void crearDevuelve201() throws Exception {
    proyectoVisible();
    var c = new Comentario();
    c.setTexto("hola");
    when(comentarioService.crear("p1", "u1", "Steve", "hola")).thenReturn(c);
    mvc.perform(post("/api/proyectos/p1/comentarios").session(sesion("u1", "USER"))
            .contentType(MediaType.APPLICATION_JSON).content(json("hola")))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.texto").value("hola"));
  }

  @Test
  void crearEnProyectoNoVisibleEs404() throws Exception {
    mvc.perform(post("/api/proyectos/p1/comentarios").session(sesion("u1", "USER"))
            .contentType(MediaType.APPLICATION_JSON).content(json("hola")))
        .andExpect(status().isNotFound());
  }

  @Test
  void crearVacioEs400() throws Exception {
    proyectoVisible();
    when(comentarioService.crear(eq("p1"), any(), any(), any()))
        .thenThrow(new ComentarioValidationException("El comentario no puede estar vacío."));
    mvc.perform(post("/api/proyectos/p1/comentarios").session(sesion("u1", "USER"))
            .contentType(MediaType.APPLICATION_JSON).content(json(" ")))
        .andExpect(status().isBadRequest());
  }

  @Test
  void editarAjenoEs403() throws Exception {
    proyectoVisible();
    comentarioDe("otro");
    mvc.perform(put("/api/proyectos/p1/comentarios/c1").session(sesion("creador", "USER"))
            .contentType(MediaType.APPLICATION_JSON).content(json("x")))
        .andExpect(status().isForbidden());
  }

  @Test
  void editarPropioEs200() throws Exception {
    proyectoVisible();
    comentarioDe("u1");
    mvc.perform(put("/api/proyectos/p1/comentarios/c1").session(sesion("u1", "USER"))
            .contentType(MediaType.APPLICATION_JSON).content(json("x")))
        .andExpect(status().isOk());
  }

  @Test
  void borrarInexistenteEs404() throws Exception {
    proyectoVisible();
    mvc.perform(delete("/api/proyectos/p1/comentarios/c9").session(sesion("u1", "USER")))
        .andExpect(status().isNotFound());
  }

  @Test
  void borrarAjenoPorAdminEs204YSeAudita() throws Exception {
    proyectoVisible();
    comentarioDe("otro");
    mvc.perform(delete("/api/proyectos/p1/comentarios/c1").session(sesion("admin", "ADMIN")))
        .andExpect(status().isNoContent());
    verify(audit).registrar(any(), eq(AuditService.ELIMINAR), eq(AuditService.COMENTARIO), eq("c1"), eq("Castillo"), anyString());
  }

  @Test
  void borrarAjenoSinPermisoEs403() throws Exception {
    proyectoVisible();
    comentarioDe("otro");
    mvc.perform(delete("/api/proyectos/p1/comentarios/c1").session(sesion("lector", "USER")))
        .andExpect(status().isForbidden());
    verify(comentarioService, never()).eliminar(any(), any());
  }
}
