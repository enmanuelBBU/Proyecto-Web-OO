package cl.grupo5.proyectominecraft.items;

import java.util.List;

@FunctionalInterface
interface RecetaLookup {
  List<String> receta(String id) throws Exception;
}
