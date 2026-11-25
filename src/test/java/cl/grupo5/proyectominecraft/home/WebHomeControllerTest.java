package cl.grupo5.proyectominecraft.home;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
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
}
