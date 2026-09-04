package cl.grupo5.proyectominecraft.auth;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import java.util.Map;

@Service
public class FirebaseIdentityService {
  private final RestTemplate rt = new RestTemplate();
  @Value("${firebase.web-api-key}")
  private String key;

  public Map<String, Object> signIn(String email, String pass) {
    return call("signInWithPassword", Map.of("email", email, "password", pass, "returnSecureToken", true));
  }

  public Map<String, Object> signUp(String email, String pass) {
    return call("signUp", Map.of("email", email, "password", pass, "returnSecureToken", true));
  }

  private Map<String, Object> call(String mode, Map<String, Object> body) {
    String url = "https://identitytoolkit.googleapis.com/v1/accounts:" + mode + "?key=" + key;
    return rt.postForObject(url, body, Map.class);
  }
}
