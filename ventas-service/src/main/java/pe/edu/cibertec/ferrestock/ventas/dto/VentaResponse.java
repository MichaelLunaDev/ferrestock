package pe.edu.cibertec.ferrestock.ventas.dto;

import pe.edu.cibertec.ferrestock.ventas.entity.EstadoVenta;
import pe.edu.cibertec.ferrestock.ventas.entity.Venta;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** {@code detalles} es null en el listado y viene lleno al consultar una venta por id. */
public record VentaResponse(
        Long id,
        LocalDateTime fecha,
        Long clienteId,
        String clienteNombre,
        String clienteDocumento,
        String vendedor,
        BigDecimal total,
        EstadoVenta estado,
        List<DetalleVentaResponse> detalles
) {
    public static VentaResponse resumen(Venta v) {
        return of(v, null);
    }

    public static VentaResponse conDetalle(Venta v) {
        return of(v, v.getDetalles().stream().map(DetalleVentaResponse::from).toList());
    }

    private static VentaResponse of(Venta v, List<DetalleVentaResponse> detalles) {
        return new VentaResponse(
                v.getId(),
                v.getFecha(),
                v.getCliente().getId(),
                v.getCliente().getNombre(),
                v.getCliente().getDocumento(),
                v.getVendedor(),
                v.getTotal(),
                v.getEstado(),
                detalles);
    }
}
