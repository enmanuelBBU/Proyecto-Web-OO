package cl.grupo5.proyectominecraft.perfil;

import cl.grupo5.proyectominecraft.auth.AuthService;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = UserProfileController.class)
@Import({ApiAuthInterceptor.class, CorsConfig.class})
class UserProfileControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private UserProfileService profileService;

  @MockitoBean
  private AuthService authService;

  private MockHttpSession authenticated(String uid) {
    var session = new MockHttpSession();
    session.setAttribute("uid", uid);
    return session;
  }

  @Test
  void getWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(get("/api/perfil/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void putWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(put("/api/perfil/me")
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"x\",\"email\":\"x@x.com\"}"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void deleteWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(delete("/api/perfil/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void saveDefaultsRoleToUsuarioWhenNoExistingProfile() throws Exception {
    when(profileService.get("uid-2")).thenReturn(null);
    when(profileService.save(eq("uid-2"), any())).thenAnswer(inv -> inv.getArgument(1));

    mvc.perform(put("/api/perfil/me")
            .session(authenticated("uid-2"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Nuevo\",\"email\":\"nuevo@x.com\"}"))
        .andExpect(status().isOk());

    var captor = org.mockito.ArgumentCaptor.forClass(UserProfile.class);
    verify(profileService).save(eq("uid-2"), captor.capture());
    assertThat(captor.getValue().getRol()).isEqualTo("USUARIO");
  }

  @Test
  void savePreservesExistingRoleIgnoringSubmittedValue() throws Exception {
    var existing = new UserProfile();
    existing.setNombre("Ana");
    existing.setEmail("ana@x.com");
    existing.setRol("USUARIO");
    when(profileService.get("uid-1")).thenReturn(existing);
    when(profileService.save(eq("uid-1"), any())).thenAnswer(inv -> inv.getArgument(1));

    mvc.perform(put("/api/perfil/me")
            .session(authenticated("uid-1"))
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Ana\",\"email\":\"ana@x.com\",\"rol\":\"ADMIN\"}"))
        .andExpect(status().isOk());

    var captor = org.mockito.ArgumentCaptor.forClass(UserProfile.class);
    verify(profileService).save(eq("uid-1"), captor.capture());
    assertThat(captor.getValue().getRol()).isEqualTo("USUARIO");
  }

  @Test
  void getReturnsStoredProfileForOwnUid() throws Exception {
    var stored = new UserProfile();
    stored.setNombre("Ana");
    stored.setEmail("ana@x.com");
    stored.setRol("USUARIO");
    when(profileService.get("uid-1")).thenReturn(stored);

    mvc.perform(get("/api/perfil/me").session(authenticated("uid-1")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombre").value("Ana"))
        .andExpect(jsonPath("$.email").value("ana@x.com"));
  }

  @Test
  void deleteInvalidatesSessionAndDeletesAuthUser() throws Exception {
    var session = authenticated("uid-1");

    mvc.perform(delete("/api/perfil/me").session(session))
        .andExpect(status().isNoContent());

    verify(profileService).delete("uid-1");
    verify(authService).deleteUser("uid-1");
    assertThat(session.isInvalid()).isTrue();
  }

  @Test
  void deleteInvalidatesSessionEvenWhenAuthDeletionFails() throws Exception {
    var session = authenticated("uid-1");
    org.mockito.Mockito.doThrow(new RuntimeException("firebase down"))
        .when(authService).deleteUser("uid-1");

    assertThatThrownBy(() -> mvc.perform(delete("/api/perfil/me").session(session)))
        .hasRootCauseMessage("firebase down");

    assertThat(session.isInvalid()).isTrue();
  }
}
