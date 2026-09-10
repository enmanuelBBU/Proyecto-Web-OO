package cl.grupo5.proyectominecraft.perfil;

import com.google.cloud.firestore.Firestore;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class UserProfileService {
  private final Firestore db;
  public UserProfileService(Firestore db) { this.db = db; }

  public UserProfile get(String uid) throws Exception {
    var snap = db.collection("users").document(uid).get().get();
    if (!snap.exists()) return null;
    var p = snap.toObject(UserProfile.class);
    p.setUid(uid);
    return p;
  }

  public List<UserProfile> list() throws Exception {
    var docs = db.collection("users").get().get().getDocuments();
    return docs.stream().map(d -> {
      var p = d.toObject(UserProfile.class);
      p.setUid(d.getId());
      return p;
    }).toList();
  }

  public UserProfile save(String uid, UserProfile p) throws Exception {
    db.collection("users").document(uid).set(p).get();
    return p;
  }

  public void delete(String uid) throws Exception {
    db.collection("users").document(uid).delete().get();
  }
}
