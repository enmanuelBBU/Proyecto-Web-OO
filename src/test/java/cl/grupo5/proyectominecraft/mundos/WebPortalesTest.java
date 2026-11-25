package cl.grupo5.proyectominecraft.mundos;

import cl.grupo5.proyectominecraft.proyectos.ProyectoService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Portales y calculadora en el detalle de mundo (WebMundoController). */
@WebMvcTest(controllers = WebMundoController.class)
class WebPortalesTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private MundoService mundos;

  @MockitoBean
  private ProyectoService proyectos;

  private static MockHttpSession session(String uid) {
    var s = new MockHttpSession();
    s.setAttribute("uid", uid);
    s.setAttribute("rol", "USUARIO");
    return s;
  }

  private static Portal portal(String id, String nombre, String dim, int x, int y, int z) {
    var p = new Portal();
    p.setId(id);
    p.setNombre(nombre);
    p.setDimension(dim);
    p.setX(x);
    p.setY(y);
    p.setZ(z);
    return p;
  }

  private Mundo mundo(String uid, Portal... portales) throws Exception {
    var m = new Mundo();
    m.setId("m1");
    m.setUid(uid);
    m.setNombre("Survival");
    m.setVersion("1.21");
    m.setModoJuego("SUPERVIVENCIA");
    m.setPortales(new ArrayList<>(List.of(portales)));
    when(mundos.get("m1")).thenReturn(m);
    return m;
  }

  private Mundo guardado() throws Exception {
    var captor = ArgumentCaptor.forClass(Mundo.class);
    verify(mundos).guardarPortales(captor.capture());
    return captor.getValue();
  }

  @Test
  void detalleMuestraPortalesConSuParYCalculadora() throws Exception {
    var a = portal("a", "Base", "OVERWORLD", 100, 70, -9);
    var b = portal("b", "Base Nether", "NETHER", 12, 70, -2);
    a.setEnlazadoId("b");
    b.setEnlazadoId("a");
    mundo("u1", a, b);
    mvc.perform(get("/mundos/m1").session(session("u1"))
            .param("dimension", "OVERWORLD").param("x", "-9").param("y", "64").param("z", "0"))
        .andExpect(status().isOk())
        .andExpect(model().attribute("conversion", new Coordenadas("NETHER", -2, 64, 0)))
        .andExpect(content().string(containsString("Base Nether")))
        .andExpect(content().string(containsString("Guardar y crear pareado")))
        .andExpect(content().string(org.hamcrest.Matchers.not(containsString("/mundos/m1/portales/a/pareado"))))
        .andExpect(content().string(containsString("/mundos/m1/portales/a/update")));
  }

  @Test
  void portalSinParOfreceCrearPareado() throws Exception {
    mundo("u1", portal("a", "Base", "OVERWORLD", 0, 64, 0));
    mvc.perform(get("/mundos/m1").session(session("u1")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Crear portal pareado")))
        .andExpect(content().string(containsString("/mundos/m1/portales/a/pareado")));
  }

  @Test
  void detalleConPortalesNulosNoRompe() throws Exception {
    var m = mundo("u1");
    m.setPortales(null);
    mvc.perform(get("/mundos/m1").session(session("u1")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Aún no hay portales")));
  }

  @Test
  void crearPortalEnMundoAjenoRedirige() throws Exception {
    mundo("u2");
    mvc.perform(post("/mundos/m1/portales").session(session("u1"))
            .param("nombre", "P").param("dimension", "OVERWORLD").param("x", "0").param("y", "64").param("z", "0"))
        .andExpect(redirectedUrl("/mundos"))
        .andExpect(flash().attribute("toastError", "Mundo no encontrado."));
    verify(mundos, never()).guardarPortales(any());
  }

  @Test
  void crearPortalGuardaYRedirige() throws Exception {
    mundo("u1");
    mvc.perform(post("/mundos/m1/portales").session(session("u1"))
            .param("nombre", "Base").param("dimension", "OVERWORLD").param("x", "8").param("y", "64").param("z", "8"))
        .andExpect(redirectedUrl("/mundos/m1#portales"))
        .andExpect(flash().attribute("toastSuccess", "Portal guardado"));
    assertThat(guardado().getPortales()).extracting(Portal::getNombre).containsExactly("Base");
  }

  @Test
  void crearPortalPareadoCreaAmbos() throws Exception {
    mundo("u1");
    mvc.perform(post("/mundos/m1/portales").session(session("u1"))
            .param("nombre", "Base").param("dimension", "OVERWORLD").param("x", "100").param("y", "70").param("z", "-9")
            .param("pareado", "true"))
        .andExpect(flash().attribute("toastSuccess", "Portal y su par guardados"));
    var portales = guardado().getPortales();
    assertThat(portales).hasSize(2);
    assertThat(portales.get(1).getDimension()).isEqualTo("NETHER");
    assertThat(portales.get(0).getEnlazadoId()).isEqualTo(portales.get(1).getId());
  }

  @Test
  void crearPortalInvalidoMuestraError() throws Exception {
    mundo("u1");
    mvc.perform(post("/mundos/m1/portales").session(session("u1"))
            .param("nombre", "").param("dimension", "OVERWORLD").param("x", "0").param("y", "64").param("z", "0"))
        .andExpect(redirectedUrl("/mundos/m1#portales"))
        .andExpect(flash().attribute("toastError", "El nombre del portal es obligatorio."));
    verify(mundos, never()).guardarPortales(any());
  }

  @Test
  void pareadoDeExistente() throws Exception {
    mundo("u1", portal("a", "Base", "NETHER", 2, 40, 3));
    mvc.perform(post("/mundos/m1/portales/a/pareado").session(session("u1")))
        .andExpect(flash().attribute("toastSuccess", "Portal pareado creado"));
    var par = guardado().getPortales().get(1);
    assertThat(par.getDimension()).isEqualTo("OVERWORLD");
    assertThat(par.getX()).isEqualTo(16);
    assertThat(par.getZ()).isEqualTo(24);
  }

  @Test
  void actualizarEnlazaPortales() throws Exception {
    var a = portal("a", "A", "OVERWORLD", 0, 64, 0);
    var b = portal("b", "B", "NETHER", 0, 64, 0);
    mundo("u1", a, b);
    mvc.perform(post("/mundos/m1/portales/a/update").session(session("u1"))
            .param("nombre", "A").param("dimension", "OVERWORLD").param("x", "0").param("y", "64").param("z", "0")
            .param("enlazadoId", "b"))
        .andExpect(flash().attribute("toastSuccess", "Portal actualizado"));
    assertThat(b.getEnlazadoId()).isEqualTo("a");
  }

  @Test
  void eliminarPortalInexistenteMuestraError() throws Exception {
    mundo("u1");
    mvc.perform(post("/mundos/m1/portales/zz/delete").session(session("u1")))
        .andExpect(flash().attribute("toastError", "Portal no encontrado."));
  }

  @Test
  void eliminarPortalLimpiaEnlace() throws Exception {
    var a = portal("a", "A", "OVERWORLD", 0, 64, 0);
    var b = portal("b", "B", "NETHER", 0, 64, 0);
    a.setEnlazadoId("b");
    b.setEnlazadoId("a");
    mundo("u1", a, b);
    mvc.perform(post("/mundos/m1/portales/a/delete").session(session("u1")))
        .andExpect(flash().attribute("toastSuccess", "Portal eliminado"));
    assertThat(guardado().getPortales()).containsExactly(b);
    assertThat(b.getEnlazadoId()).isNull();
  }

  @Test
  void adminGestionaPortalesDeMundoAjeno() throws Exception {
    mundo("u2");
    var s = session("admin");
    s.setAttribute("rol", "ADMIN");
    mvc.perform(post("/mundos/m1/portales").session(s)
            .param("nombre", "Base").param("dimension", "OVERWORLD").param("x", "0").param("y", "64").param("z", "0"))
        .andExpect(flash().attribute("toastSuccess", "Portal guardado"));
  }
}
