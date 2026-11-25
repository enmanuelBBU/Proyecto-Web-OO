package cl.grupo5.proyectominecraft.home;

import cl.grupo5.proyectominecraft.proyectos.Proyecto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = WebHomeController.class)
class WebHomeControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private DashboardService dashboard;

  @BeforeEach
  void stubVacio() {
    stub(new DashboardResumen(0, 0, 0L, 0, 0, 0, List.of()));
  }

  private void stub(DashboardResumen r) {
    when(dashboard.resumen(any(), any())).thenReturn(r);
  }

  @Test
  void withoutSessionRedirectsToLogin() throws Exception {
    mvc.perform(get("/inicio"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void rootRedirectsToInicio() throws Exception {
    mvc.perform(get("/"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/inicio"));
  }

  @Test
  void withSessionRendersInicioWithIsAdminFalseForRegularUser() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "user-1");
    session.setAttribute("rol", "USUARIO");

    mvc.perform(get("/inicio").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("inicio"))
        .andExpect(model().attribute("isAdmin", false));
  }

  @Test
  void withSessionRendersInicioWithIsAdminTrueForAdmin() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "admin-1");
    session.setAttribute("rol", "ADMIN");

    mvc.perform(get("/inicio").session(session))
        .andExpect(status().isOk())
        .andExpect(view().name("inicio"))
        .andExpect(model().attribute("isAdmin", true));
  }

  private static MockHttpSession sesion(String uid, String rol, String nombre) {
    var session = new MockHttpSession();
    session.setAttribute("uid", uid);
    session.setAttribute("rol", rol);
    if (nombre != null) session.setAttribute("nombre", nombre);
    return session;
  }

  @Test
  void inicioMarksInicioAsActiveInTopbar() throws Exception {
    mvc.perform(get("/inicio").session(sesion("user-1", "USUARIO", "Steve")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("class=\"topbar\"")))
        .andExpect(content().string(containsString("aria-current=\"page\"")));
  }

  @Test
  void inicioHidesAdminMenuForRegularUser() throws Exception {
    mvc.perform(get("/inicio").session(sesion("user-1", "USUARIO", "Steve")))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("/admin/usuarios"))));
  }

  @Test
  void inicioShowsAdminMenuForAdmin() throws Exception {
    mvc.perform(get("/inicio").session(sesion("admin-1", "ADMIN", "Alex")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("/admin/usuarios")))
        .andExpect(content().string(containsString("/admin/auditoria")));
  }

  @Test
  void topbarFallsBackToConstructorWhenNombreMissing() throws Exception {
    mvc.perform(get("/inicio").session(sesion("user-1", "USUARIO", null)))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Constructor")));
  }

  @Test
  void topbarShowsSessionNombre() throws Exception {
    mvc.perform(get("/inicio").session(sesion("user-1", "USUARIO", "Steve")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Steve")));
  }

  @Test
  void inicioExponeResumen() throws Exception {
    var r = new DashboardResumen(2, 5, 40L, 3, 7, 1, List.of());
    stub(r);
    mvc.perform(get("/inicio").session(sesion("user-1", "USUARIO", "Steve")))
        .andExpect(status().isOk())
        .andExpect(model().attribute("resumen", r))
        .andExpect(content().string(containsString("40")))
        .andExpect(content().string(containsString("de 5 en total")));
  }

  @Test
  void inicioMuestraGuionCuandoKpiNulo() throws Exception {
    stub(new DashboardResumen(2, 5, null, null, 7, 1, List.of()));
    mvc.perform(get("/inicio").session(sesion("user-1", "USUARIO", "Steve")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("—")));
  }

  @Test
  void inicioSinProyectosMuestraEstadoVacio() throws Exception {
    mvc.perform(get("/inicio").session(sesion("user-1", "USUARIO", "Steve")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Crear proyecto")));
  }

  @Test
  void inicioListaRecientesConBadge() throws Exception {
    var p = new Proyecto();
    p.setId("p1");
    p.setNombre("Granja de hierro");
    p.setEstado("EN_CONSTRUCCION");
    p.setCreadorNombre("Steve");
    stub(new DashboardResumen(1, 1, 0L, 0, 0, 0, List.of(p)));
    mvc.perform(get("/inicio").session(sesion("user-1", "USUARIO", "Steve")))
        .andExpect(status().isOk())
        .andExpect(content().string(containsString("Granja de hierro")))
        .andExpect(content().string(containsString("/proyectos/p1")))
        .andExpect(content().string(containsString("En construcción")));
  }

  @Test
  void inicioNoUsaAcordeon() throws Exception {
    mvc.perform(get("/inicio").session(sesion("user-1", "USUARIO", "Steve")))
        .andExpect(status().isOk())
        .andExpect(content().string(not(containsString("home-card-group"))))
        .andExpect(content().string(not(containsString("Estados de Proyecto"))));
  }
}
