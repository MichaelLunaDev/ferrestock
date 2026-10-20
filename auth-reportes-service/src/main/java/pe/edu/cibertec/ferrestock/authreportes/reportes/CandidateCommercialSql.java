package pe.edu.cibertec.ferrestock.authreportes.reportes;

/** Propuesta basada en JPA, NO contrato físico aprobado. Sin bean ni activación por entorno. */
public final class CandidateCommercialSql {
    private final String schema;
    public CandidateCommercialSql(String schema) {
        if (schema == null || !schema.matches("[A-Za-z_][A-Za-z0-9_]{0,127}"))
            throw new IllegalArgumentException("Esquema comercial invalido");
        this.schema = "[" + schema + "].";
    }
    public String sales() {
        return "SELECT TOP (?) v.id,v.fecha,v.vendedor,v.total,v.estado FROM " + schema + "[ventas] v "
            + "WHERE v.fecha>=? AND v.fecha<? AND v.estado COLLATE Latin1_General_100_BIN2=? "
            + "ORDER BY v.fecha DESC,v.id DESC";
    }
    public String topProducts() {
        // TOP se aplica DESPUES de agregar TODOS los detalles elegibles; no recortar ventas.
        return "SELECT TOP (?) p.id,p.sku,p.nombre,a.unidades,a.importe FROM ("
            + "SELECT d.producto_id,SUM(CONVERT(bigint,d.cantidad)) unidades,SUM(d.subtotal) importe FROM "
            + schema + "[detalle_ventas] d JOIN " + schema + "[ventas] v ON v.id=d.venta_id "
            + "WHERE v.fecha>=? AND v.fecha<? AND v.estado COLLATE Latin1_General_100_BIN2=? "
            + "GROUP BY d.producto_id) a JOIN " + schema + "[productos] p ON p.id=a.producto_id "
            + "ORDER BY a.unidades DESC,a.importe DESC,p.id ASC";
    }
    public String lowStock() {
        return "SELECT TOP (?) p.id,p.sku,p.nombre,p.stock FROM " + schema + "[productos] p "
            + "WHERE p.stock<=? ORDER BY p.stock ASC,p.id ASC";
    }
}
