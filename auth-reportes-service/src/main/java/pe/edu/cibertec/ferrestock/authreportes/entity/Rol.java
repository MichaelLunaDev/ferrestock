package pe.edu.cibertec.ferrestock.authreportes.entity;

public enum Rol {
    ADMINISTRADOR(1), VENDEDOR(2), ALMACENERO(3);
    private final int id;
    Rol(int id) { this.id = id; }
    public int id() { return id; }
    public static Rol fromId(int id) {
        for (Rol rol : values()) if (rol.id == id) return rol;
        throw new IllegalArgumentException("Rol no reconocido");
    }
}
