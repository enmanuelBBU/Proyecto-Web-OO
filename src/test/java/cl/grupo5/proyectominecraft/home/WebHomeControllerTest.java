package cl.grupo5.proyectominecraft.home;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
}
