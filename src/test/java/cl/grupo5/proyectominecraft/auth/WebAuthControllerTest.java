package cl.grupo5.proyectominecraft.auth;

import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = WebAuthController.class)
class WebAuthControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private FirebaseIdentityService identity;

  @MockitoBean
  private UserProfileService profiles;

  @Test
  void loginRendersFormWithoutSession() throws Exception {
    mvc.perform(get("/login"))
        .andExpect(status().isOk())
        .andExpect(view().name("login"));
  }

  @Test
  void loginRedirectsToItemsWhenAlreadyAuthenticated() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "uid-1");

    mvc.perform(get("/login").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));
  }

  @Test
  void registerRedirectsToItemsWhenAlreadyAuthenticated() throws Exception {
    var session = new MockHttpSession();
    session.setAttribute("uid", "uid-1");

    mvc.perform(get("/register").session(session))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/items"));
  }
}
