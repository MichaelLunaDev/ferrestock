    package pe.edu.cibertec.ferrestock.ventas.dto;

    import jakarta.validation.constraints.DecimalMin;
    import jakarta.validation.constraints.Min;
    import jakarta.validation.constraints.NotNull;
    import lombok.AllArgsConstructor;
    import lombok.Builder;
    import lombok.Data;
    import lombok.NoArgsConstructor;

    import java.io.Serializable;
    import java.math.BigDecimal;

    @Data
    @AllArgsConstructor
    @NoArgsConstructor
    @Builder
    public class DetalleCompraDto implements Serializable {
        private static final long serialVersionUID = 1L;

        @NotNull(message = "El ID del producto es obligatorio")
        private Long productoId;

        @NotNull(message = "La cantidad es obligatoria")
        @Min(value = 1, message = "La cantidad a reponer debe ser al menos 1")
        private Integer cantidad;

        @NotNull(message = "El precio unitario es obligatorio")
        @DecimalMin(value = "0.01", message = "El precio unitario debe ser mayor a 0")
        private BigDecimal precioUnitario;
    }
