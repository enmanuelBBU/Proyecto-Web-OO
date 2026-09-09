package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.items.Item;
import cl.grupo5.proyectominecraft.items.ItemService;
import cl.grupo5.proyectominecraft.perfil.UserProfile;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static cl.grupo5.proyectominecraft.proyectos.ProyectoPdfService.FilaItem;
import static cl.grupo5.proyectominecraft.proyectos.ProyectoPdfService.ReporteProyecto;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@WebMvcTest(controllers = ProyectoPdfController.class)
class ProyectoPdfControllerTest {

  @Autowired
  private MockMvc mvc;

  @MockitoBean
  private ProyectoPdfService pdfService;

  @MockitoBean
  private ProyectoService proyectoService;

  @MockitoBean
  private ItemService itemService;

  @MockitoBean
  private UserProfileService profileService;

  private MockHttpSession session(String uid) {
    var session = new MockHttpSession();
    session.setAttribute("uid", uid);
    return session;
  }

  @Test
  void reporteWithoutSessionRedirectsToLogin() throws Exception {
    mvc.perform(get("/proyectos/reporte-pdf?id=casa"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
    verify(pdfService, never()).generar(any());
  }

  @Test
  void reportesWithoutSessionRedirectsToLogin() throws Exception {
    mvc.perform(get("/proyectos/reportes"))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/login"));
  }

  @Test
  void reporteWithoutIdRedirectsToReportes() throws Exception {
    mvc.perform(get("/proyectos/reporte-pdf").session(session("uid-1")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos/reportes"));
    verify(pdfService, never()).generar(any());
  }

  @Test
  void reporteWithUnknownIdRedirectsToReportes() throws Exception {
    when(proyectoService.get("no-existe")).thenReturn(null);

    mvc.perform(get("/proyectos/reporte-pdf").param("id", "no-existe").session(session("uid-1")))
        .andExpect(status().is3xxRedirection())
        .andExpect(redirectedUrl("/proyectos/reportes"));
    verify(pdfService, never()).generar(any());
  }

  @Test
  void reportesWithoutProjectsRendersFriendlyEmptyState() throws Exception {
    when(proyectoService.list()).thenReturn(List.of());

    mvc.perform(get("/proyectos/reportes").session(session("uid-1")))
        .andExpect(status().isOk())
        .andExpect(view().name("reportes"))
        .andExpect(model().attributeExists("proyectos"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("No hay proyectos registrados aún")));
  }

  @Test
  void reportesWithProjectsRendersExportButtons() throws Exception {
    var proyecto = new Proyecto();
    proyecto.setId("casa");
    proyecto.setNombre("Casa de Roble");
    proyecto.setEstado("En planificación");
    when(proyectoService.list()).thenReturn(List.of(proyecto));

    mvc.perform(get("/proyectos/reportes").session(session("uid-1")))
        .andExpect(status().isOk())
        .andExpect(view().name("reportes"))
        .andExpect(model().attributeExists("proyectos"))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Casa de Roble")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("Exportar PDF")))
        .andExpect(content().string(org.hamcrest.Matchers.containsString("/proyectos/reporte-pdf?id=casa")));
  }

  @Test
  void reporteWithSessionReturnsPdfBuiltFromRealProjectData() throws Exception {
    var proyecto = new Proyecto();
    proyecto.setId("casa");
    proyecto.setNombre("Casa de Roble");
    proyecto.setDescripcion("Vivienda de supervivencia.");
    proyecto.setObjetivo("Levantar 3 pisos.");
    proyecto.setEstado("En planificación");
    proyecto.setAutorUid("autor-1");
    var material = new MaterialProyecto();
    material.setItemId("tablones_de_roble");
    material.setCantidad(64);
    var materialPerdido = new MaterialProyecto();
    materialPerdido.setItemId("diamante");
    materialPerdido.setCantidad(1);
    proyecto.setMateriales(List.of(material, materialPerdido));

    var tablones = new Item();
    tablones.setId("tablones_de_roble");
    tablones.setNombre("Tablones de Roble");
    tablones.setCategoria("Bloques de Construcción");
    when(itemService.list(null, null)).thenReturn(List.of(tablones));

    var perfil = new UserProfile();
    perfil.setNombre("Ana Autor");
    when(profileService.get("autor-1")).thenReturn(perfil);

    when(proyectoService.get("casa")).thenReturn(proyecto);
    when(pdfService.generar(any())).thenReturn("%PDF-1.7\ncontenido".getBytes());

    mvc.perform(get("/proyectos/reporte-pdf").param("id", "casa").session(session("uid-1")))
        .andExpect(status().isOk())
        .andExpect(content().contentType("application/pdf"))
        .andExpect(header().string("Content-Disposition", "inline; filename=proyecto-casa.pdf"))
        .andExpect(content().bytes("%PDF-1.7\ncontenido".getBytes()));

    var captor = ArgumentCaptor.forClass(ReporteProyecto.class);
    verify(pdfService).generar(captor.capture());
    var reporte = captor.getValue();
    assertThat(reporte.titulo()).isEqualTo("Casa de Roble");
    assertThat(reporte.autor()).isEqualTo("Ana Autor");
    assertThat(reporte.estado()).isEqualTo("En planificación");
    assertThat(reporte.descripcion()).isEqualTo("Vivienda de supervivencia.");
    assertThat(reporte.filas())
        .contains(new FilaItem("Tablones de Roble", 64, "Bloques de Construcción"))
        .contains(new FilaItem("diamante", 1, null));
  }

  @Test
  void reporteFallsBackToUidWhenAuthorProfileHasNoName() throws Exception {
    var proyecto = new Proyecto();
    proyecto.setId("casa");
    proyecto.setNombre("Casa de Roble");
    proyecto.setAutorUid("autor-1");
    when(proyectoService.get("casa")).thenReturn(proyecto);
    when(profileService.get("autor-1")).thenReturn(null);
    when(pdfService.generar(any())).thenReturn("%PDF-1.7\ncontenido".getBytes());

    mvc.perform(get("/proyectos/reporte-pdf").param("id", "casa").session(session("uid-1")))
        .andExpect(status().isOk());

    var captor = ArgumentCaptor.forClass(ReporteProyecto.class);
    verify(pdfService).generar(captor.capture());
    assertThat(captor.getValue().autor()).isEqualTo("autor-1");
  }
}
