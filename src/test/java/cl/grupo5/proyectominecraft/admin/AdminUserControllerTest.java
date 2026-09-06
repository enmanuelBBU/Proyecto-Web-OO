package cl.grupo5.proyectominecraft.admin;

import cl.grupo5.proyectominecraft.auth.AuthService;
import cl.grupo5.proyectominecraft.config.AdminAuthInterceptor;
import cl.grupo5.proyectominecraft.config.ApiAuthInterceptor;
import cl.grupo5.proyectominecraft.config.CorsConfig;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = AdminUserController.class)
@Import({ApiAuthInterceptor.class, AdminAuthInterceptor.class, CorsConfig.class})
class AdminUserControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private UserProfileService profileService;

  @MockitoBean
  private AuthService authService;

  private MockHttpSession adminSession() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "admin-1");
    session.setAttribute("rol", "ADMIN");
    return session;
  }

  private MockHttpSession userSession() {
    var session = new MockHttpSession();
    session.setAttribute("uid", "user-1");
    session.setAttribute("rol", "USUARIO");
    return session;
  }

  @Test
  void listWithoutSessionIsUnauthorized() throws Exception {
    mvc.perform(get("/api/admin/usuarios")).andExpect(status().isUnauthorized());
  }

  @Test
  void listWithNonAdminSessionIsForbidden() throws Exception {
    mvc.perform(get("/api/admin/usuarios").session(userSession())).andExpect(status().isForbidden());
  }

  @Test
  void listWithAdminSessionSucceeds() throws Exception {
    when(profileService.list()).thenReturn(List.of());
    mvc.perform(get("/api/admin/usuarios").session(adminSession())).andExpect(status().isOk());
  }

  @Test
  void listIncludesUidInResponse() throws Exception {
    var user = new UserProfile();
    user.setUid("user-2");
    user.setNombre("Ana");
    user.setEmail("ana@x.com");
    user.setRol("USUARIO");
    when(profileService.list()).thenReturn(List.of(user));

    mvc.perform(get("/api/admin/usuarios").session(adminSession()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].uid").value("user-2"));
  }

  @Test
  void getReturnsNotFoundForUnknownUid() throws Exception {
    when(profileService.get("missing-1")).thenReturn(null);

    mvc.perform(get("/api/admin/usuarios/missing-1").session(adminSession()))
        .andExpect(status().isNotFound());
  }

  @Test
  void getReturnsRequestedUserProfile() throws Exception {
    var target = new UserProfile();
    target.setUid("user-2");
    target.setNombre("Ana");
    target.setEmail("ana@x.com");
    target.setRol("USUARIO");
    when(profileService.get("user-2")).thenReturn(target);

    mvc.perform(get("/api/admin/usuarios/user-2").session(adminSession()))
        .andExpect(status().isOk());
  }

  @Test
  void updateRejectsInvalidRole() throws Exception {
    var target = new UserProfile();
    target.setUid("user-2");
    target.setNombre("Ana");
    target.setEmail("ana@x.com");
    target.setRol("USUARIO");
    when(profileService.get("user-2")).thenReturn(target);

    mvc.perform(put("/api/admin/usuarios/user-2")
            .session(adminSession())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Ana\",\"email\":\"ana@x.com\",\"rol\":\"SUPERADMIN\"}"))
        .andExpect(status().isBadRequest());

    verify(profileService, never()).save(any(), any());
  }

  @Test
  void updateRejectsNullRole() throws Exception {
    var target = new UserProfile();
    target.setUid("user-2");
    target.setNombre("Ana");
    target.setEmail("ana@x.com");
    target.setRol("USUARIO");
    when(profileService.get("user-2")).thenReturn(target);

    mvc.perform(put("/api/admin/usuarios/user-2")
            .session(adminSession())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Ana\",\"email\":\"ana@x.com\",\"rol\":null}"))
        .andExpect(status().isBadRequest());

    verify(profileService, never()).save(any(), any());
  }

  @Test
  void updateChangesTargetRole() throws Exception {
    var target = new UserProfile();
    target.setUid("user-2");
    target.setNombre("Ana");
    target.setEmail("ana@x.com");
    target.setRol("USUARIO");
    when(profileService.get("user-2")).thenReturn(target);
    when(profileService.save(eq("user-2"), any())).thenAnswer(inv -> inv.getArgument(1));

    mvc.perform(put("/api/admin/usuarios/user-2")
            .session(adminSession())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Ana\",\"email\":\"ana@x.com\",\"rol\":\"ADMIN\"}"))
        .andExpect(status().isOk());

    var captor = org.mockito.ArgumentCaptor.forClass(UserProfile.class);
    verify(profileService).save(eq("user-2"), captor.capture());
    assertThat(captor.getValue().getRol()).isEqualTo("ADMIN");
  }

  @Test
  void updateRejectsSelfTargeting() throws Exception {
    mvc.perform(put("/api/admin/usuarios/admin-1")
            .session(adminSession())
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"nombre\":\"Admin\",\"email\":\"admin@x.com\",\"rol\":\"ADMIN\"}"))
        .andExpect(status().isBadRequest());

    verify(profileService, never()).save(any(), any());
  }

  @Test
  void deleteRejectsSelfTargeting() throws Exception {
    mvc.perform(delete("/api/admin/usuarios/admin-1").session(adminSession()))
        .andExpect(status().isBadRequest());

    verify(authService, never()).deleteUser(any());
    verify(profileService, never()).delete(any());
  }

  @Test
  void deleteRemovesTargetAuthAndProfileInSafeOrder() throws Exception {
    mvc.perform(delete("/api/admin/usuarios/user-2").session(adminSession()))
        .andExpect(status().isNoContent());

    var order = inOrder(authService, profileService);
    order.verify(authService).deleteUser("user-2");
    order.verify(profileService).delete("user-2");
  }
}
