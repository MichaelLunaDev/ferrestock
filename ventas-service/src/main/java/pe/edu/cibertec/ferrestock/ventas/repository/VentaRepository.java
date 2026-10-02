package pe.edu.cibertec.ferrestock.ventas.repository;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.cibertec.ferrestock.ventas.entity.Venta;

import java.util.List;
import java.util.Optional;

public interface VentaRepository extends JpaRepository<Venta, Long> {

    @EntityGraph(attributePaths = "cliente")
    List<Venta> findAllByOrderByFechaDesc();

    @EntityGraph(attributePaths = {"cliente", "detalles", "detalles.producto"})
    Optional<Venta> findWithDetallesById(Long id);
}
