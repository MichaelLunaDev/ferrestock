package pe.edu.cibertec.ferrestock.ventas.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record VentaRequest(
        @NotNull Long clienteId,
        @NotBlank String vendedor,
        @NotEmpty List<@Valid @NotNull ItemVentaRequest> items
) {
}
