package cl.grupo5.proyectominecraft.items;

@FunctionalInterface
interface ExistenceChecker {
  boolean existe(String id) throws Exception;
}
