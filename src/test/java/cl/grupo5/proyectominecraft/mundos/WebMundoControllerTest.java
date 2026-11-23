package cl.grupo5.proyectominecraft.mundos;

import cl.grupo5.proyectominecraft.proyectos.ProyectoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebMundoController.class)
class WebMundoControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private MundoService mundos;

  @MockitoBean
  private ProyectoService proyectos;

  private MockHttpSession session(String uid) {
    var s = new MockHttpSession();
    s.setAttribute("uid", uid);
    s.setAttribute("nombre", "Steve");
    s.setAttribute("rol", "USUARIO");
    return s;
  }

  private Mundo mundo(String id, String uid) {
    var m = new Mundo();
    m.setId(id);
    m.setUid(uid);
    m.setNombre("Survival");
    m.setVersion("1.21");
    m.setModoJuego("SUPERVIVENCIA");
    return m;
  }

  @Test
  void mundosSinSesionRedirigeALogin() throws Exception {
    mvc.perform(get("/mundos")).andExpect(redirectedUrl("/login"));
  }

  @Test
  void listaSoloMundosDelUsuario() throws Exception {
    var lista = List.of(mundo("m1", "u1"));
    when(mundos.listar("u1")).thenReturn(lista);
    mvc.perform(get("/mundos").session(session("u1")))
        .andExpect(status().isOk())
        .andExpect(view().name("mundos"))
        .andExpect(model().attribute("mundos", lista));
  }

  @Test
  void crearMundoRedirigeConToast() throws Exception {
    when(mundos.crear(any(), eq("u1"))).thenReturn(mundo("m1", "u1"));
    mvc.perform(post("/mundos").session(session("u1"))
            .param("nombre", "Survival").param("version", "1.21").param("modoJuego", "CREATIVO"))
        .andExpect(redirectedUrl("/mundos"))
        .andExpect(flash().attribute("toastSuccess", "Mundo creado correctamente"));
  }

  @Test
  void crearMundoInvalidoMuestraError() throws Exception {
    when(mundos.crear(any(), eq("u1"))).thenThrow(new MundoValidationException("La versión es obligatoria."));
    mvc.perform(post("/mundos").session(session("u1")).param("nombre", "Survival"))
        .andExpect(status().isOk())
        .andExpect(view().name("mundos"))
        .andExpect(model().attribute("error", "La versión es obligatoria."));
  }

  @Test
  void detalleDeMundoAjenoRedirige() throws Exception {
    when(mundos.get("m1")).thenReturn(mundo("m1", "u2"));
    mvc.perform(get("/mundos/m1").session(session("u1")))
        .andExpect(redirectedUrl("/mundos"))
        .andExpect(flash().attribute("toastError", "Mundo no encontrado."));
  }

  @Test
  void detalleDeMundoPropioMuestraProyectos() throws Exception {
    when(mundos.get("m1")).thenReturn(mundo("m1", "u1"));
    when(proyectos.listarPorMundo("m1")).thenReturn(List.of());
    mvc.perform(get("/mundos/m1").session(session("u1")))
        .andExpect(status().isOk())
        .andExpect(view().name("mundo-detalle"))
        .andExpect(model().attributeExists("mundo", "proyectos"));
  }

  @Test
  void eliminarConProyectosMuestraToastError() throws Exception {
    when(mundos.eliminar("m1", "u1", "USUARIO"))
        .thenThrow(new MundoValidationException("El mundo tiene 2 proyecto(s) asignado(s)."));
    mvc.perform(post("/mundos/m1/delete").session(session("u1")))
        .andExpect(redirectedUrl("/mundos/m1"))
        .andExpect(flash().attribute("toastError", "El mundo tiene 2 proyecto(s) asignado(s)."));
  }

  @Test
  void eliminarMundoRedirigeConToast() throws Exception {
    when(mundos.eliminar("m1", "u1", "USUARIO")).thenReturn(true);
    mvc.perform(post("/mundos/m1/delete").session(session("u1")))
        .andExpect(redirectedUrl("/mundos"))
        .andExpect(flash().attribute("toastSuccess", "Mundo eliminado correctamente"));
  }
}
