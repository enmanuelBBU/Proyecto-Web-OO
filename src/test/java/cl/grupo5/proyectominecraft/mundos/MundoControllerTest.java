package cl.grupo5.proyectominecraft.mundos;

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
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = MundoController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class MundoControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private MundoService mundos;

  private MockHttpSession session(String uid) {
    var s = new MockHttpSession();
    s.setAttribute("uid", uid);
    s.setAttribute("rol", "USUARIO");
    return s;
  }

  private Mundo mundo(String id, String uid) {
    var m = new Mundo();
    m.setId(id);
    m.setUid(uid);
    m.setNombre("Survival");
    m.setVersion("1.21");
    return m;
  }

  @Test
  void apiSinSesionDa401() throws Exception {
    mvc.perform(get("/api/mundos")).andExpect(status().isUnauthorized());
  }

  @Test
  void apiGetMundoAjenoDa404() throws Exception {
    when(mundos.get("m1")).thenReturn(mundo("m1", "u2"));
    mvc.perform(get("/api/mundos/m1").session(session("u1"))).andExpect(status().isNotFound());
  }

  @Test
  void apiGetMundoPropioDa200() throws Exception {
    when(mundos.get("m1")).thenReturn(mundo("m1", "u1"));
    mvc.perform(get("/api/mundos/m1").session(session("u1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value("m1"));
  }

  @Test
  void apiCrearDevuelve201() throws Exception {
    when(mundos.crear(any(), eq("u1"))).thenReturn(mundo("m1", "u1"));
    mvc.perform(post("/api/mundos").session(session("u1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Survival\",\"version\":\"1.21\"}"))
        .andExpect(status().isCreated());
  }

  @Test
  void apiCrearInvalidoDa400() throws Exception {
    when(mundos.crear(any(), eq("u1"))).thenThrow(new MundoValidationException("La versión es obligatoria."));
    mvc.perform(post("/api/mundos").session(session("u1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Survival\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void apiEliminarConProyectosDa409() throws Exception {
    when(mundos.get("m1")).thenReturn(mundo("m1", "u1"));
    when(mundos.eliminar("m1", "u1", "USUARIO"))
        .thenThrow(new MundoValidationException("El mundo tiene 1 proyecto(s) asignado(s)."));
    mvc.perform(delete("/api/mundos/m1").session(session("u1"))).andExpect(status().isConflict());
  }
}
