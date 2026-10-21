package pe.edu.cibertec.ferrestock.ventas.dto;

import jakarta.persistence.Column;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class CompraRequestDto implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "El proveedor es obligatorio")
    @Size(min = 2, max = 100, message = "El proveedor debe tener entre 2 y 100 caracteres")
    private String proveedor;

    @NotEmpty(message = "La compra debe contener al menos un producto")
    @Valid
    private List<DetalleCompraDto> detalles;
}
