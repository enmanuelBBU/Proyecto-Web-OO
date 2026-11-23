package cl.grupo5.proyectominecraft.auth;

import com.google.firebase.auth.AuthErrorCode;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseToken;
import org.springframework.stereotype.Service;
import java.util.Map;

@Service
public class AuthService {
  public Map<String, String> verify(String idToken) throws Exception {
    FirebaseToken t = FirebaseAuth.getInstance().verifyIdToken(idToken);
    return Map.of("uid", t.getUid(), "email", String.valueOf(t.getClaims().getOrDefault("email", "")), "name", String.valueOf(t.getClaims().getOrDefault("name", "")));
  }

  /** uid de la cuenta de Firebase Auth con ese email (único y autoritativo), o null si no existe. */
  public String uidPorEmail(String email) throws Exception {
    if (email == null || email.isBlank()) return null;
    try {
      return FirebaseAuth.getInstance().getUserByEmail(email.trim()).getUid();
    } catch (FirebaseAuthException e) {
      if (e.getAuthErrorCode() == AuthErrorCode.USER_NOT_FOUND) return null;
      throw e;
    }
  }

  public void deleteUser(String uid) throws Exception {
    com.google.firebase.auth.FirebaseAuth.getInstance().deleteUser(uid);
  }
}
