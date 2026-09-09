package cl.grupo5.proyectominecraft.proyectos;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class Proyecto {
  private String id;

  @NotBlank(message = "El nombre del proyecto es obligatorio.")
  private String nombre;

  private String descripcion;

  private String estado = "PLANIFICACION"; // Default state

  private List<ItemRequerido> itemsRequeridos = new ArrayList<>();

  private String creadorUid;

  private String creadorNombre;

  private String fechaCreacion;

  private List<HistorialEstado> historialEstados = new ArrayList<>();
}
