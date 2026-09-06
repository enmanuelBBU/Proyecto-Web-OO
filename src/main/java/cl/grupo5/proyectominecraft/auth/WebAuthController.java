package cl.grupo5.proyectominecraft.auth;

import jakarta.servlet.http.HttpSession;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.HttpClientErrorException;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;

@Controller
public class WebAuthController {
  private final FirebaseIdentityService identity;
  private final UserProfileService profiles;

  public WebAuthController(FirebaseIdentityService identity, UserProfileService profiles) {
    this.identity = identity;
    this.profiles = profiles;
  }

  @GetMapping("/login")
  public String login(HttpSession s) {
    if (s.getAttribute("uid") != null) return "redirect:/items";
    return "login";
  }

  @PostMapping("/login")
  public String doLogin(@RequestParam String email, @RequestParam String password, HttpSession s, Model m) {
    try {
      var r = identity.signIn(email, password);
      s.setAttribute("uid", r.get("localId"));
      s.setAttribute("email", email);
      return "redirect:/items";
    } catch (Exception e) {
      m.addAttribute("error", "Login: " + causa(e));
      return "login";
    }
  }

  @GetMapping("/register")
  public String register(HttpSession s) {
    if (s.getAttribute("uid") != null) return "redirect:/items";
    return "register";
  }

  @PostMapping("/register")
  public String doRegister(@RequestParam String email, @RequestParam String password, HttpSession s, Model m) {
    try {
      var r = identity.signUp(email, password);
      String uid = String.valueOf(r.get("localId"));
      var p = new UserProfile();
      p.setNombre(email.split("@")[0]);
      p.setEmail(email);
      profiles.save(uid, p);
      s.setAttribute("uid", uid);
      s.setAttribute("email", email);
      return "redirect:/items";
    } catch (Exception e) {
      m.addAttribute("error", "Registro: " + causa(e));
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
