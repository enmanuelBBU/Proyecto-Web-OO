package cl.grupo5.proyectominecraft.auditoria;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebAuditoriaController.class)
class WebAuditoriaControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private AuditService audit;

  private MockHttpSession session(String rol) {
    var s = new MockHttpSession();
    s.setAttribute("uid", "u1");
    s.setAttribute("rol", rol);
    return s;
  }

  @Test
  void auditoriaSinSesionRedirigeALogin() throws Exception {
    mvc.perform(get("/admin/auditoria")).andExpect(redirectedUrl("/login"));
  }

  @Test
  void auditoriaParaNoAdminRedirige() throws Exception {
    mvc.perform(get("/admin/auditoria").session(session("USUARIO")))
        .andExpect(redirectedUrl("/items"));
    verify(audit, never()).listar(any(), any(), any());
  }

  @Test
  void auditoriaParaAdminExponeEventos() throws Exception {
    var e = AuditService.construir("u1", "Steve", "CREAR", "ITEM", "palo", "Palo", null, "2026-11-23T10:00:00Z");
    when(audit.listar("ITEM", null, null)).thenReturn(List.of(e));
    mvc.perform(get("/admin/auditoria").param("entidad", "ITEM").session(session("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(view().name("admin-auditoria"))
        .andExpect(model().attribute("eventos", List.of(e)))
        .andExpect(model().attribute("entidad", "ITEM"));
  }
}
