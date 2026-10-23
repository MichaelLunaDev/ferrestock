package pe.edu.cibertec.ferrestock.ventas.dto;

import jakarta.validation.constraints.NotBlank;

public record ClienteDto(
        Long id,
        @NotBlank String nombre,
        @NotBlank String documento,
        @NotBlank String tipoDocumento
) {
}
