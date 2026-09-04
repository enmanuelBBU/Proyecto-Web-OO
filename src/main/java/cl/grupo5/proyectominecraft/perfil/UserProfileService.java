package cl.grupo5.proyectominecraft.perfil;

import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;

@Service
public class UserProfileService {
  private final Firestore db;
  public UserProfileService(Firestore db) { this.db = db; }

  public UserProfile get(String uid) throws Exception {
    var snap = db.collection("users").document(uid).get().get();
    return snap.exists() ? snap.toObject(UserProfile.class) : null;
  }

  public UserProfile save(String uid, UserProfile p) throws Exception {
    db.collection("users").document(uid).set(p).get();
    return p;
  }
}
