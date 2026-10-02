package pe.edu.cibertec.ferrestock.ventas.dto;

import pe.edu.cibertec.ferrestock.ventas.entity.DetalleVenta;

import java.math.BigDecimal;

public record DetalleVentaResponse(
        Long id,
        Long productoId,
        String productoNombre,
        String sku,
        Integer cantidad,
        BigDecimal precioUnitario,
        BigDecimal subtotal
) {
    public static DetalleVentaResponse from(DetalleVenta d) {
        return new DetalleVentaResponse(
                d.getId(),
                d.getProducto().getId(),
                d.getProducto().getNombre(),
                d.getProducto().getSku(),
                d.getCantidad(),
                d.getPrecioUnitario(),
                d.getSubtotal());
    }
}
