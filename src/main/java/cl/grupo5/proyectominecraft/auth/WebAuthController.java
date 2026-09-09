package cl.grupo5.proyectominecraft.auth;

import cl.grupo5.proyectominecraft.config.AdminEmails;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;

@Controller
public class WebAuthController {
  private final FirebaseIdentityService identity;
  private final UserProfileService profiles;
  private final AdminEmails adminEmails;

  public WebAuthController(FirebaseIdentityService identity, UserProfileService profiles, AdminEmails adminEmails) {
    this.identity = identity;
    this.profiles = profiles;
    this.adminEmails = adminEmails;
  }

  @GetMapping("/login")
  public String login(HttpSession s) {
    if (s.getAttribute("uid") != null) return "redirect:/inicio";
    return "login";
  }

  @PostMapping("/login")
  public String doLogin(@RequestParam String email, @RequestParam String password, HttpSession s, Model m,
                        RedirectAttributes ra) {
    try {
      var r = identity.signIn(email, password);
      String uid = String.valueOf(r.get("localId"));
      var profile = resolveRoleOnLogin(uid, email);
      s.setAttribute("uid", uid);
      s.setAttribute("email", email);
      s.setAttribute("rol", profile != null ? profile.getRol() : "USUARIO");
      ra.addFlashAttribute("toastSuccess", "¡Bienvenido!");
      return "redirect:/inicio";
    } catch (Exception e) {
      m.addAttribute("error", "Login: " + causa(e));
      m.addAttribute("toastError", "Login: " + causa(e));
      return "login";
    }
  }

  private UserProfile resolveRoleOnLogin(String uid, String email) throws Exception {
    var profile = profiles.get(uid);
    if (profile != null && adminEmails.isAdmin(email) && !"ADMIN".equals(profile.getRol())) {
      profile.setRol("ADMIN");
      profiles.save(uid, profile);
    }
    return profile;
  }

  @GetMapping("/register")
  public String register(HttpSession s) {
    if (s.getAttribute("uid") != null) return "redirect:/inicio";
    return "register";
  }

  @PostMapping("/register")
  public String doRegister(@RequestParam String email, @RequestParam String password, HttpSession s, Model m,
                           RedirectAttributes ra) {
    try {
      var r = identity.signUp(email, password);
      String uid = String.valueOf(r.get("localId"));
      var p = new UserProfile();
      p.setNombre(email.split("@")[0]);
      p.setEmail(email);
      if (adminEmails.isAdmin(email)) p.setRol("ADMIN");
      profiles.save(uid, p);
      s.setAttribute("uid", uid);
      s.setAttribute("email", email);
      s.setAttribute("rol", p.getRol());
      ra.addFlashAttribute("toastSuccess", "¡Cuenta creada!");
      return "redirect:/inicio";
    } catch (Exception e) {
      m.addAttribute("error", "Registro: " + causa(e));
      m.addAttribute("toastError", "Registro: " + causa(e));
      return "register";
    }
  }

  private String causa(Exception e) {
    if (e instanceof HttpClientErrorException h) return h.getResponseBodyAsString();
    return String.valueOf(e.getMessage());
  }

  @PostMapping("/logout")
  public String logout(HttpSession s) {
    s.invalidate();
    return "redirect:/login";
  }
}
