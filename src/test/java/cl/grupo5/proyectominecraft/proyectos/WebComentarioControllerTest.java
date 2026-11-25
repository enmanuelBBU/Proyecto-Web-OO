package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.auditoria.AuditService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.flash;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;

@WebMvcTest(controllers = WebComentarioController.class)
class WebComentarioControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private ProyectoService proyectoService;

  @MockitoBean
  private ComentarioService comentarioService;

  @MockitoBean
  private AuditService audit;

  private static MockHttpSession sesion(String uid) {
    var s = new MockHttpSession();
    s.setAttribute("uid", uid);
    s.setAttribute("nombre", "Steve");
    s.setAttribute("rol", "USER");
    return s;
  }

  private Proyecto proyecto() throws Exception {
    var p = new Proyecto();
    p.setId("p1");
    p.setNombre("Castillo");
    p.setCreadorUid("creador");
    when(proyectoService.getVisible(eq("p1"), any(), any())).thenReturn(p);
    return p;
  }

  private Comentario comentarioDe(String autor) throws Exception {
    var c = new Comentario();
    c.setId("c1");
    c.setAutorUid(autor);
    c.setAutorNombre("Alex");
    c.setTexto("hola");
    when(comentarioService.get("p1", "c1")).thenReturn(c);
    return c;
  }

  @Test
  void sinSesionRedirigeALogin() throws Exception {
    mvc.perform(post("/proyectos/p1/comentarios").param("texto", "hola"))
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void proyectoNoVisibleRedirigeAProyectos() throws Exception {
    mvc.perform(post("/proyectos/p1/comentarios").session(sesion("u1")).param("texto", "hola"))
        .andExpect(redirectedUrl("/proyectos"))
        .andExpect(flash().attribute("toastError", "Proyecto no encontrado."));
    verify(comentarioService, never()).crear(anyString(), anyString(), anyString(), anyString());
  }

  @Test
  void crearComentarioRedirigeAlDetalle() throws Exception {
    proyecto();
    mvc.perform(post("/proyectos/p1/comentarios").session(sesion("lector")).param("texto", "hola"))
        .andExpect(redirectedUrl("/proyectos/p1#comentarios"))
        .andExpect(flash().attribute("toastSuccess", "Comentario publicado"));
    verify(comentarioService).crear("p1", "lector", "Steve", "hola");
  }

  @Test
  void crearComentarioVacioMuestraError() throws Exception {
    proyecto();
    when(comentarioService.crear(eq("p1"), any(), any(), eq("  ")))
        .thenThrow(new ComentarioValidationException("El comentario no puede estar vacío."));
    mvc.perform(post("/proyectos/p1/comentarios").session(sesion("lector")).param("texto", "  "))
        .andExpect(redirectedUrl("/proyectos/p1#comentarios"))
        .andExpect(flash().attribute("toastError", "El comentario no puede estar vacío."));
  }

  @Test
  void editarComentarioAjenoSeRechaza() throws Exception {
    proyecto();
    comentarioDe("otro");
    mvc.perform(post("/proyectos/p1/comentarios/c1/update").session(sesion("creador")).param("texto", "x"))
        .andExpect(redirectedUrl("/proyectos/p1#comentarios"))
        .andExpect(flash().attribute("toastError", "Solo el autor puede editar este comentario."));
    verify(comentarioService, never()).editar(any(), any(), any());
  }

  @Test
  void editarComentarioPropio() throws Exception {
    proyecto();
    var c = comentarioDe("u1");
    mvc.perform(post("/proyectos/p1/comentarios/c1/update").session(sesion("u1")).param("texto", "nuevo"))
        .andExpect(flash().attribute("toastSuccess", "Comentario actualizado"));
    verify(comentarioService).editar("p1", c, "nuevo");
  }

  @Test
  void editarComentarioInexistente() throws Exception {
    proyecto();
    mvc.perform(post("/proyectos/p1/comentarios/c9/update").session(sesion("u1")).param("texto", "x"))
        .andExpect(flash().attribute("toastError", "Comentario no encontrado."));
  }

  @Test
  void borrarComentarioAjenoPorCreadorSeAudita() throws Exception {
    proyecto();
    comentarioDe("otro");
    mvc.perform(post("/proyectos/p1/comentarios/c1/delete").session(sesion("creador")))
        .andExpect(redirectedUrl("/proyectos/p1#comentarios"))
        .andExpect(flash().attribute("toastSuccess", "Comentario eliminado"));
    verify(comentarioService).eliminar("p1", "c1");
    verify(audit).registrar(any(), eq(AuditService.ELIMINAR), eq(AuditService.COMENTARIO), eq("c1"), eq("Castillo"), anyString());
  }

  @Test
  void borrarComentarioPropioNoSeAudita() throws Exception {
    proyecto();
    comentarioDe("u1");
    mvc.perform(post("/proyectos/p1/comentarios/c1/delete").session(sesion("u1")))
        .andExpect(flash().attribute("toastSuccess", "Comentario eliminado"));
    verify(comentarioService).eliminar("p1", "c1");
    verify(audit, never()).registrar(any(), any(), any(), any(), any(), any());
  }

  @Test
  void borrarComentarioAjenoSinPermisoSeRechaza() throws Exception {
    proyecto();
    comentarioDe("otro");
    mvc.perform(post("/proyectos/p1/comentarios/c1/delete").session(sesion("lector")))
        .andExpect(flash().attribute("toastError", "No tienes permisos para borrar este comentario."));
    verify(comentarioService, never()).eliminar(any(), any());
  }
}
