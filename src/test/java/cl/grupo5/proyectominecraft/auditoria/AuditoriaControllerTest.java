package cl.grupo5.proyectominecraft.auditoria;

import cl.grupo5.proyectominecraft.config.AdminAuthInterceptor;
import cl.grupo5.proyectominecraft.config.ApiAuthInterceptor;
import cl.grupo5.proyectominecraft.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AuditoriaController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class AuditoriaControllerTest {

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
  void apiAuditoriaNoAdminDa403() throws Exception {
    mvc.perform(get("/api/admin/auditoria").session(session("USUARIO"))).andExpect(status().isForbidden());
  }

  @Test
  void apiAuditoriaAdminDevuelveEventos() throws Exception {
    var e = AuditService.construir("u1", "Steve", "ELIMINAR", "PROYECTO", "p1", "Castillo", null, "t");
    when(audit.listar("PROYECTO", "ELIMINAR", null)).thenReturn(List.of(e));
    mvc.perform(get("/api/admin/auditoria").param("entidad", "PROYECTO").param("accion", "ELIMINAR")
            .session(session("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].entidadNombre").value("Castillo"));
  }
}
