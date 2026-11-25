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

import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = PortalController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class PortalControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private MundoService mundos;

  private static MockHttpSession session(String uid) {
    var s = new MockHttpSession();
    s.setAttribute("uid", uid);
    s.setAttribute("rol", "USUARIO");
    return s;
  }

  private Mundo mundo(String uid, Portal... portales) throws Exception {
    var m = new Mundo();
    m.setId("m1");
    m.setUid(uid);
    m.setPortales(new ArrayList<>(List.of(portales)));
    when(mundos.get("m1")).thenReturn(m);
    return m;
  }

  private static Portal portal(String id, String dim) {
    var p = new Portal();
    p.setId(id);
    p.setNombre("P-" + id);
    p.setDimension(dim);
    p.setY(64);
    return p;
  }

  private static final String BODY = "{\"nombre\":\"Base\",\"dimension\":\"OVERWORLD\",\"x\":100,\"y\":70,\"z\":-9}";

  @Test
  void sinSesionEs401() throws Exception {
    mvc.perform(get("/api/mundos/m1/portales")).andExpect(status().isUnauthorized());
  }

  @Test
  void listarMundoAjenoEs404() throws Exception {
    mundo("u2");
    mvc.perform(get("/api/mundos/m1/portales").session(session("u1"))).andExpect(status().isNotFound());
  }

  @Test
  void listarDevuelvePortales() throws Exception {
    mundo("u1", portal("a", "OVERWORLD"));
    mvc.perform(get("/api/mundos/m1/portales").session(session("u1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].id").value("a"));
  }

  @Test
  void crearDevuelve201() throws Exception {
    mundo("u1");
    mvc.perform(post("/api/mundos/m1/portales").session(session("u1"))
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.nombre").value("Base"));
    verify(mundos).guardarPortales(any());
  }

  @Test
  void crearPareadoDevuelvePortalEnlazado() throws Exception {
    mundo("u1");
    mvc.perform(post("/api/mundos/m1/portales").param("pareado", "true").session(session("u1"))
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.enlazadoId").isNotEmpty());
  }

  @Test
  void crearInvalidoEs400() throws Exception {
    mundo("u1");
    mvc.perform(post("/api/mundos/m1/portales").session(session("u1"))
            .contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\",\"dimension\":\"OVERWORLD\",\"y\":64}"))
        .andExpect(status().isBadRequest());
    verify(mundos, never()).guardarPortales(any());
  }

  @Test
  void actualizarInexistenteEs404() throws Exception {
    mundo("u1");
    mvc.perform(put("/api/mundos/m1/portales/zz").session(session("u1"))
            .contentType(MediaType.APPLICATION_JSON).content(BODY))
        .andExpect(status().isNotFound());
  }

  @Test
  void actualizarEnlazaMismaDimensionEs400() throws Exception {
    mundo("u1", portal("a", "OVERWORLD"), portal("b", "OVERWORLD"));
    mvc.perform(put("/api/mundos/m1/portales/a").session(session("u1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"A\",\"dimension\":\"OVERWORLD\",\"x\":0,\"y\":64,\"z\":0,\"enlazadoId\":\"b\"}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void eliminarDevuelve204() throws Exception {
    mundo("u1", portal("a", "OVERWORLD"));
    mvc.perform(delete("/api/mundos/m1/portales/a").session(session("u1"))).andExpect(status().isNoContent());
  }

  @Test
  void eliminarEnMundoAjenoEs404() throws Exception {
    mundo("u2", portal("a", "OVERWORLD"));
    mvc.perform(delete("/api/mundos/m1/portales/a").session(session("u1"))).andExpect(status().isNotFound());
    verify(mundos, never()).guardarPortales(any());
  }
}
