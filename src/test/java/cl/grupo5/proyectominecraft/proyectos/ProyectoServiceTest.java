package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.mundos.Mundo;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProyectoServiceTest {

  private Proyecto proyecto(String id, String nombre, String descripcion, String estado, String creadorUid) {
    var p = new Proyecto();
    p.setId(id);
    p.setNombre(nombre);
    p.setDescripcion(descripcion);
    p.setEstado(estado);
    p.setCreadorUid(creadorUid);
    return p;
  }

  @Test
  void filterByNombreOrDescripcion() {
    var lista = List.of(
        proyecto("p1", "Castillo de Piedra", "Gran fortaleza medieval", "PLANIFICACION", "u1"),
        proyecto("p2", "Granja de Hierro", "Mecanismo redstone", "EN_CONSTRUCCION", "u2"),
        proyecto("p3", "Casa de Madera", "Hogar basico", "COMPLETADO", "u1")
    );

    var res = ProyectoService.filter(lista, "hierro", null, null);
    assertThat(res).extracting(Proyecto::getId).containsExactly("p2");

    var resDesc = ProyectoService.filter(lista, "fortaleza", null, null);
    assertThat(resDesc).extracting(Proyecto::getId).containsExactly("p1");
  }

  @Test
  void filterByEstado() {
    var lista = List.of(
        proyecto("p1", "Castillo", "Desc", "PLANIFICACION", "u1"),
        proyecto("p2", "Granja", "Desc", "EN_CONSTRUCCION", "u2")
    );

    var res = ProyectoService.filter(lista, null, "PLANIFICACION", null);
    assertThat(res).extracting(Proyecto::getId).containsExactly("p1");
  }

  @Test
  void filterByCreadorUid() {
    var lista = List.of(
        proyecto("p1", "Castillo", "Desc", "PLANIFICACION", "u1"),
        proyecto("p2", "Granja", "Desc", "EN_CONSTRUCCION", "u2")
    );

    var res = ProyectoService.filter(lista, null, null, "u2");
    assertThat(res).extracting(Proyecto::getId).containsExactly("p2");
  }

  @Test
  void validarEstadoAcceptsValidStates() {
    ProyectoService.validarEstado("PLANIFICACION");
    ProyectoService.validarEstado("EN_CONSTRUCCION");
    ProyectoService.validarEstado("COMPLETADO");
    ProyectoService.validarEstado("CANCELADO");
  }

  @Test
  void validarEstadoRejectsInvalidState() {
    assertThatThrownBy(() -> ProyectoService.validarEstado("DESTRUIDO"))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("El estado 'DESTRUIDO' no es válido.");
  }

  @Test
  void validarItemsRequeridosAcceptsValidItems() throws Exception {
    var items = List.of(new ItemRequerido("piedra", 64));
    ProyectoService.validarItemsRequeridos(items, itemId -> "piedra".equals(itemId));
  }

  @Test
  void validarItemsRequeridosRejectsNegativeOrZeroQuantity() {
    var items = List.of(new ItemRequerido("piedra", 0));

    assertThatThrownBy(() -> ProyectoService.validarItemsRequeridos(items, itemId -> true))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("La cantidad para el ítem 'piedra' debe ser mayor a 0.");
  }

  @Test
  void validarItemsRequeridosRejectsNonExistentItemInCatalog() {
    var items = List.of(new ItemRequerido("bloque_inexistente", 10));

    assertThatThrownBy(() -> ProyectoService.validarItemsRequeridos(items, itemId -> false))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("El ítem 'bloque_inexistente' no existe en el catálogo.");
  }

  // Tests para máquina de estados

  @Test
  void transicionarEstadoSuccess() throws Exception {
    var dbMock = org.mockito.Mockito.mock(com.google.cloud.firestore.Firestore.class);
    var collMock = org.mockito.Mockito.mock(com.google.cloud.firestore.CollectionReference.class);
    var docRefMock = org.mockito.Mockito.mock(com.google.cloud.firestore.DocumentReference.class);
    var futureMock = org.mockito.Mockito.mock(com.google.api.core.ApiFuture.class);
    var snapMock = org.mockito.Mockito.mock(com.google.cloud.firestore.DocumentSnapshot.class);
    
    org.mockito.Mockito.when(dbMock.collection("proyectos")).thenReturn(collMock);
    org.mockito.Mockito.when(collMock.document("p1")).thenReturn(docRefMock);
    org.mockito.Mockito.when(docRefMock.get()).thenReturn(futureMock);
    org.mockito.Mockito.when(futureMock.get()).thenReturn(snapMock);
    org.mockito.Mockito.when(snapMock.exists()).thenReturn(true);
    
    var p = new Proyecto();
    p.setId("p1");
    p.setEstado("PLANIFICACION");
    p.setCreadorUid("uid123");
    
    org.mockito.Mockito.when(snapMock.toObject(Proyecto.class)).thenReturn(p);
    org.mockito.Mockito.when(docRefMock.set(org.mockito.ArgumentMatchers.any(Proyecto.class))).thenReturn(futureMock);

    var service = new ProyectoService(dbMock, null, null, null);
    var actualizado = service.transicionarEstado("p1", "EN_CONSTRUCCION", "Inicio obras", "uid123", "Steve", "USUARIO");
    
    assertThat(actualizado.getEstado()).isEqualTo("EN_CONSTRUCCION");
    assertThat(actualizado.getHistorialEstados()).hasSize(1);
    assertThat(actualizado.getHistorialEstados().get(0).getEstadoNuevo()).isEqualTo("EN_CONSTRUCCION");
    assertThat(actualizado.getHistorialEstados().get(0).getMotivo()).isEqualTo("Inicio obras");
  }

  @Test
  void transicionarEstadoInvalidTransitionThrows() throws Exception {
    var dbMock = org.mockito.Mockito.mock(com.google.cloud.firestore.Firestore.class);
    var collMock = org.mockito.Mockito.mock(com.google.cloud.firestore.CollectionReference.class);
    var docRefMock = org.mockito.Mockito.mock(com.google.cloud.firestore.DocumentReference.class);
    var futureMock = org.mockito.Mockito.mock(com.google.api.core.ApiFuture.class);
    var snapMock = org.mockito.Mockito.mock(com.google.cloud.firestore.DocumentSnapshot.class);
    
    org.mockito.Mockito.when(dbMock.collection("proyectos")).thenReturn(collMock);
    org.mockito.Mockito.when(collMock.document("p1")).thenReturn(docRefMock);
    org.mockito.Mockito.when(docRefMock.get()).thenReturn(futureMock);
    org.mockito.Mockito.when(futureMock.get()).thenReturn(snapMock);
    org.mockito.Mockito.when(snapMock.exists()).thenReturn(true);
    
    var p = new Proyecto();
    p.setId("p1");
    p.setEstado("PLANIFICACION");
    p.setCreadorUid("uid123");
    
    org.mockito.Mockito.when(snapMock.toObject(Proyecto.class)).thenReturn(p);

    var service = new ProyectoService(dbMock, null, null, null);
    
    assertThatThrownBy(() -> service.transicionarEstado("p1", "COMPLETADO", null, "uid123", "Steve", "USUARIO"))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("Transición inválida: de PLANIFICACION a COMPLETADO");
  }

  @Test
  void transicionarEstadoForbiddenThrows() throws Exception {
    var dbMock = org.mockito.Mockito.mock(com.google.cloud.firestore.Firestore.class);
    var collMock = org.mockito.Mockito.mock(com.google.cloud.firestore.CollectionReference.class);
    var docRefMock = org.mockito.Mockito.mock(com.google.cloud.firestore.DocumentReference.class);
    var futureMock = org.mockito.Mockito.mock(com.google.api.core.ApiFuture.class);
    var snapMock = org.mockito.Mockito.mock(com.google.cloud.firestore.DocumentSnapshot.class);
    
    org.mockito.Mockito.when(dbMock.collection("proyectos")).thenReturn(collMock);
    org.mockito.Mockito.when(collMock.document("p1")).thenReturn(docRefMock);
    org.mockito.Mockito.when(docRefMock.get()).thenReturn(futureMock);
    org.mockito.Mockito.when(futureMock.get()).thenReturn(snapMock);
    org.mockito.Mockito.when(snapMock.exists()).thenReturn(true);
    
    var p = new Proyecto();
    p.setId("p1");
    p.setEstado("PLANIFICACION");
    p.setCreadorUid("uid123");
    
    org.mockito.Mockito.when(snapMock.toObject(Proyecto.class)).thenReturn(p);

    var service = new ProyectoService(dbMock, null, null, null);
    
    assertThatThrownBy(() -> service.transicionarEstado("p1", "EN_CONSTRUCCION", null, "otherUid", "Alex", "USUARIO"))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("No tienes permisos para cambiar el estado de este proyecto.");
  }

  // ---- Ubicación en mundo ----

  private Mundo mundoDe(String uid) {
    var m = new Mundo();
    m.setId("m1");
    m.setUid(uid);
    return m;
  }

  @Test
  void sinMundoLimpiaCoordenadas() throws Exception {
    var p = new Proyecto();
    p.setMundoId(" ");
    p.setX(5);
    p.setY(70);
    p.setZ(5);
    ProyectoService.validarUbicacion(p, "u1", id -> null);
    assertThat(p.getMundoId()).isNull();
    assertThat(p.getX()).isNull();
    assertThat(p.getY()).isNull();
    assertThat(p.getZ()).isNull();
  }

  @Test
  void mundoInexistenteSeRechaza() {
    var p = new Proyecto();
    p.setMundoId("m9");
    assertThatThrownBy(() -> ProyectoService.validarUbicacion(p, "u1", id -> null))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("El mundo seleccionado no existe.");
  }

  @Test
  void mundoDeOtroUsuarioSeRechaza() {
    var p = new Proyecto();
    p.setMundoId("m1");
    assertThatThrownBy(() -> ProyectoService.validarUbicacion(p, "u1", id -> mundoDe("u2")))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("El mundo debe pertenecer al creador del proyecto.");
  }

  @Test
  void yFueraDeRangoSeRechaza() {
    var p = new Proyecto();
    p.setMundoId("m1");
    p.setY(321);
    assertThatThrownBy(() -> ProyectoService.validarUbicacion(p, "u1", id -> mundoDe("u1")))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("La coordenada Y debe estar entre -64 y 320.");
    p.setY(-65);
    assertThatThrownBy(() -> ProyectoService.validarUbicacion(p, "u1", id -> mundoDe("u1")))
        .hasMessage("La coordenada Y debe estar entre -64 y 320.");
  }

  @Test
  void xzFueraDeRangoSeRechaza() {
    var p = new Proyecto();
    p.setMundoId("m1");
    p.setX(30_000_001);
    assertThatThrownBy(() -> ProyectoService.validarUbicacion(p, "u1", id -> mundoDe("u1")))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("Las coordenadas X y Z deben estar entre -30.000.000 y 30.000.000.");
    p.setX(0);
    p.setZ(-30_000_001);
    assertThatThrownBy(() -> ProyectoService.validarUbicacion(p, "u1", id -> mundoDe("u1")))
        .hasMessage("Las coordenadas X y Z deben estar entre -30.000.000 y 30.000.000.");
  }

  @Test
  void mundoPropioConYSinCoordenadasEsValido() throws Exception {
    var p = new Proyecto();
    p.setMundoId(" m1 ");
    ProyectoService.validarUbicacion(p, "u1", id -> mundoDe("u1"));
    assertThat(p.getMundoId()).isEqualTo("m1");
    p.setX(-100); p.setY(-64); p.setZ(320);
    ProyectoService.validarUbicacion(p, "u1", id -> mundoDe("u1"));
    assertThat(p.getY()).isEqualTo(-64);
  }

  // ---- Colaborativos ----

  private Proyecto conMiembros() {
    var p = proyecto("p1", "Castillo", "", "PLANIFICACION", "creador");
    p.setMiembros(new ArrayList<>(List.of(
        new Miembro("lector", "L", "l@x.cl", "LECTURA", "t"),
        new Miembro("editor", "E", "e@x.cl", "EDICION", "t"))));
    p.setMiembroUids(new ArrayList<>(List.of("lector", "editor")));
    return p;
  }

  private UserProfile perfil(String uid, String nombre, String email) {
    var u = new UserProfile();
    u.setUid(uid);
    u.setNombre(nombre);
    u.setEmail(email);
    return u;
  }

  @Test
  void reglasDeAcceso() {
    var p = conMiembros();
    assertThat(ProyectoService.canView(p, "lector", "USUARIO")).isTrue();
    assertThat(ProyectoService.canEdit(p, "lector", "USUARIO")).isFalse();
    assertThat(ProyectoService.canEdit(p, "editor", "USUARIO")).isTrue();
    assertThat(ProyectoService.canManage(p, "editor", "USUARIO")).isFalse();
    assertThat(ProyectoService.canManage(p, "creador", "USUARIO")).isTrue();
    assertThat(ProyectoService.canEdit(p, "creador", "USUARIO")).isTrue();
    assertThat(ProyectoService.canManage(p, "otro", "ADMIN")).isTrue();
    assertThat(ProyectoService.canView(p, "otro", "USUARIO")).isFalse();
    assertThat(ProyectoService.canEdit(p, "otro", "USUARIO")).isFalse();
    assertThat(ProyectoService.canView(null, "creador", "ADMIN")).isFalse();
  }

  @Test
  void proyectoSinCampoMiembrosSoloLoVeElCreador() {
    var p = proyecto("p1", "C", "", "PLANIFICACION", "creador");
    p.setMiembros(null);
    p.setMiembroUids(null);
    assertThat(ProyectoService.canView(p, "creador", "USUARIO")).isTrue();
    assertThat(ProyectoService.canView(p, "otro", "USUARIO")).isFalse();
    assertThat(ProyectoService.permisoDe(p, "otro")).isNull();
  }

  @Test
  void visibleParaFiltraPrivados() {
    var propio = proyecto("p1", "A", "", "PLANIFICACION", "u1");
    var compartido = conMiembros();
    compartido.setId("p2");
    compartido.getMiembros().add(new Miembro("u1", "U", "u@x.cl", "LECTURA", "t"));
    var ajeno = proyecto("p3", "C", "", "PLANIFICACION", "u9");
    var todos = List.of(propio, compartido, ajeno);
    assertThat(ProyectoService.visiblePara(todos, "u1", "USUARIO"))
        .extracting(Proyecto::getId).containsExactly("p1", "p2");
    assertThat(ProyectoService.visiblePara(todos, "u1", "ADMIN")).hasSize(3);
  }

  @Test
  void agregarMiembroSincronizaUidsYActualizaPermiso() {
    var p = proyecto("p1", "C", "", "PLANIFICACION", "creador");
    ProyectoService.agregarMiembro(p, perfil("u2", "Alex", "alex@x.cl"), "edicion", "t");
    assertThat(p.getMiembroUids()).containsExactly("u2");
    assertThat(p.getMiembros()).singleElement().extracting(Miembro::getPermiso).isEqualTo("EDICION");
    ProyectoService.agregarMiembro(p, perfil("u2", "Alex", "alex@x.cl"), "LECTURA", "t");
    assertThat(p.getMiembros()).singleElement().extracting(Miembro::getPermiso).isEqualTo("LECTURA");
    assertThat(p.getMiembroUids()).containsExactly("u2");
  }

  @Test
  void agregarMiembroToleraListasNulas() {
    var p = proyecto("p1", "C", "", "PLANIFICACION", "creador");
    p.setMiembros(null);
    p.setMiembroUids(null);
    ProyectoService.agregarMiembro(p, perfil("u2", "Alex", "alex@x.cl"), "LECTURA", "t");
    assertThat(p.getMiembroUids()).containsExactly("u2");
  }

  @Test
  void noSePuedeInvitarAlCreador() {
    var p = proyecto("p1", "C", "", "PLANIFICACION", "creador");
    assertThatThrownBy(() -> ProyectoService.agregarMiembro(p, perfil("creador", "C", "c@x.cl"), "LECTURA", "t"))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("No puedes invitar al creador del proyecto.");
  }

  @Test
  void permisoInvalidoSeRechaza() {
    var p = proyecto("p1", "C", "", "PLANIFICACION", "creador");
    assertThatThrownBy(() -> ProyectoService.agregarMiembro(p, perfil("u2", "A", "a@x.cl"), "ADMIN", "t"))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("Permiso inválido.");
  }

  @Test
  void cambiarPermisoDeNoMiembroFalla() {
    var p = conMiembros();
    assertThatThrownBy(() -> ProyectoService.cambiarPermiso(p, "otro", "EDICION"))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("El usuario no es miembro del proyecto.");
    ProyectoService.cambiarPermiso(p, "lector", "EDICION");
    assertThat(ProyectoService.permisoDe(p, "lector")).isEqualTo("EDICION");
  }

  @Test
  void quitarMiembroSincronizaUids() {
    var p = conMiembros();
    ProyectoService.quitarMiembro(p, "lector");
    assertThat(p.getMiembros()).extracting(Miembro::getUid).containsExactly("editor");
    assertThat(p.getMiembroUids()).containsExactly("editor");
    assertThatThrownBy(() -> ProyectoService.quitarMiembro(p, "lector"))
        .hasMessage("El usuario no es miembro del proyecto.");
  }

  @Test
  void nombreDuplicadoIgnoraMayusculasYAcentosYExcluyePropio() {
    var lista = List.of(proyecto("abc", "Casa Árbol", "", "PLANIFICACION", "u1"));
    assertThat(ProyectoService.nombreDuplicado(lista, "casa arbol", null)).isTrue();
    assertThat(ProyectoService.nombreDuplicado(lista, "casa arbol", "abc")).isFalse();
    assertThat(ProyectoService.nombreDuplicado(lista, "Granja", null)).isFalse();
  }

  @Test
  void duplicadoMuestraMensajeConNombre() {
    assertThat(new ProyectoAlreadyExistsException("Casa").getMessage())
        .isEqualTo("Ya tienes un proyecto llamado 'Casa'.");
  }

  @Test
  void prepararActualizacionConservaMiembrosHistorialYCreador() {
    var actual = conMiembros();
    actual.setCreadorNombre("Creador");
    actual.setFechaCreacion("2026-01-01");
    actual.setHistorialEstados(new ArrayList<>(List.of(
        new HistorialEstado("PLANIFICACION", "EN_CONSTRUCCION", "creador", "C", "t", null))));
    var cambios = new Proyecto();
    cambios.setNombre("Castillo 2");
    cambios.setMiembros(null);
    cambios.setHistorialEstados(new ArrayList<>());
    var resultado = ProyectoService.prepararActualizacion(actual, cambios, "p1");
    assertThat(resultado.getId()).isEqualTo("p1");
    assertThat(resultado.getNombre()).isEqualTo("Castillo 2");
    assertThat(resultado.getCreadorUid()).isEqualTo("creador");
    assertThat(resultado.getCreadorNombre()).isEqualTo("Creador");
    assertThat(resultado.getFechaCreacion()).isEqualTo("2026-01-01");
    assertThat(resultado.getMiembros()).hasSize(2);
    assertThat(resultado.getMiembroUids()).containsExactly("lector", "editor");
    assertThat(resultado.getHistorialEstados()).hasSize(1);
  }

  // ---- Revisión final: invitaciones resueltas por Firebase Auth ----

  @Test
  void resolverInvitadoUsaLaCuentaDeFirebaseNoElEmailDelPerfil() throws Exception {
    var alice = perfil("alice", "Alice", "alice@x.cl");
    var mallory = perfil("mallory", "Mallory", "alice@x.cl"); // se puso el email de Alice en su perfil
    java.util.Map<String, UserProfile> perfiles = java.util.Map.of("alice", alice, "mallory", mallory);
    var invitado = ProyectoService.resolverInvitado(" Alice@X.cl ",
        email -> email.equalsIgnoreCase("alice@x.cl") ? "alice" : null, perfiles::get);
    assertThat(invitado.getUid()).isEqualTo("alice");
  }

  @Test
  void resolverInvitadoSinCuentaEnFirebaseFalla() {
    assertThatThrownBy(() -> ProyectoService.resolverInvitado("nadie@x.cl", email -> null, uid -> null))
        .isInstanceOf(ProyectoValidationException.class)
        .hasMessage("No existe un usuario registrado con ese email.");
  }

  @Test
  void resolverInvitadoSinPerfilUsaDatosDeLaCuenta() throws Exception {
    var invitado = ProyectoService.resolverInvitado("bob@x.cl", email -> "bob", uid -> null);
    assertThat(invitado.getUid()).isEqualTo("bob");
    assertThat(invitado.getEmail()).isEqualTo("bob@x.cl");
    assertThat(invitado.getNombre()).isEqualTo("bob");
  }
}
