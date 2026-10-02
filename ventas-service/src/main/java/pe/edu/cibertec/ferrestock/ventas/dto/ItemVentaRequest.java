package pe.edu.cibertec.ferrestock.ventas.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record ItemVentaRequest(
        @NotNull Long productoId,
        @NotNull @Positive Integer cantidad
) {
}
