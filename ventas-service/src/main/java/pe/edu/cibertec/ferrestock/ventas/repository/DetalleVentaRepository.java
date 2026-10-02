package pe.edu.cibertec.ferrestock.ventas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.cibertec.ferrestock.ventas.entity.DetalleVenta;

public interface DetalleVentaRepository extends JpaRepository<DetalleVenta, Long> {
}
