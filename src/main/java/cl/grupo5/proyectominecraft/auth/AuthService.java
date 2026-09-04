package cl.grupo5.proyectominecraft.auth;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class AuthService {
  public Map<String, String> verify(String idToken) throws Exception {
    FirebaseToken t = FirebaseAuth.getInstance().verifyIdToken(idToken);
    return Map.of("uid", t.getUid(), "email", String.valueOf(t.getClaims().getOrDefault("email", "")), "name", String.valueOf(t.getClaims().getOrDefault("name", "")));
  }
}
