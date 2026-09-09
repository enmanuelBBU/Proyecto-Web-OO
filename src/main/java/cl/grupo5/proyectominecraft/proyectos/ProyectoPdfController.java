package cl.grupo5.proyectominecraft.proyectos;

import cl.grupo5.proyectominecraft.items.Item;
import cl.grupo5.proyectominecraft.items.ItemService;
import cl.grupo5.proyectominecraft.perfil.UserProfileService;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static cl.grupo5.proyectominecraft.proyectos.ProyectoPdfService.FilaItem;
import static cl.grupo5.proyectominecraft.proyectos.ProyectoPdfService.ReporteProyecto;

@Controller
public class ProyectoPdfController {

  private final ProyectoPdfService pdf;
  private final ProyectoService proyectos;
  private final ItemService items;
  private final UserProfileService perfiles;

  public ProyectoPdfController(ProyectoPdfService pdf,
                               ProyectoService proyectos,
                               ItemService items,
                               UserProfileService perfiles) {
    this.pdf = pdf;
    this.proyectos = proyectos;
    this.items = items;
    this.perfiles = perfiles;
  }

  @GetMapping("/proyectos/reportes")
  public String reportes(Model m, HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) return "redirect:/login";
    m.addAttribute("proyectos", proyectos.list());
    m.addAttribute("isAdmin", "ADMIN".equals(s.getAttribute("rol")));
    return "reportes";
  }

  @GetMapping("/proyectos/reporte-pdf")
  public ResponseEntity<byte[]> reporte(@RequestParam(required = false) String id,
                                        HttpSession s) throws Exception {
    if (s.getAttribute("uid") == null) {
      return ResponseEntity.status(302)
          .header(HttpHeaders.LOCATION, "/login")
          .build();
    }
    if (id == null || id.isBlank()) {
      return ResponseEntity.status(302)
          .header(HttpHeaders.LOCATION, "/proyectos/reportes")
          .build();
    }

    var proyecto = proyectos.get(id);
    if (proyecto == null) {
      return ResponseEntity.status(302)
          .header(HttpHeaders.LOCATION, "/proyectos/reportes")
          .build();
    }

    var reporte = armar(proyecto, nombreAutor(proyecto.getAutorUid()));
    var bytes = pdf.generar(reporte);
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
        .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=proyecto-" + id + ".pdf")
        .body(bytes);
  }

  private ReporteProyecto armar(Proyecto proyecto, String autor) throws Exception {
    Map<String, Item> itemsPorId =
        items.list(null, null).stream()
            .collect(Collectors.toMap(Item::getId, Function.identity()));

    var filas = new ArrayList<FilaItem>();
    if (proyecto.getMateriales() != null) {
      for (var material : proyecto.getMateriales()) {
        if (material.getItemId() == null || material.getItemId().isBlank()) continue;
        var item = itemsPorId.get(material.getItemId());
        filas.add(new FilaItem(
            item != null && item.getNombre() != null ? item.getNombre() : material.getItemId(),
            material.getCantidad(),
            item != null ? item.getCategoria() : null));
      }
    }

    return new ReporteProyecto(
        proyecto.getNombre() != null ? proyecto.getNombre() : "Proyecto sin nombre",
        "Reporte de Proyecto · Minecraft Manager",
        LocalDate.now().format(DateTimeFormatter.ofPattern("dd/MM/yyyy")),
        autor,
        proyecto.getEstado(),
        null,
        proyecto.getDescripcion(),
        proyecto.getObjetivo(),
        List.copyOf(filas));
  }

  private String nombreAutor(String autorUid) throws Exception {
    if (autorUid == null || autorUid.isBlank()) return null;
    var perfil = perfiles.get(autorUid);
    if (perfil == null || perfil.getNombre() == null || perfil.getNombre().isBlank()) {
      return autorUid;
    }
    return perfil.getNombre();
  }
}
