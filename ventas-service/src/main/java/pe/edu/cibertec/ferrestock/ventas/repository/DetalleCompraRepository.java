package pe.edu.cibertec.ferrestock.ventas.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.cibertec.ferrestock.ventas.entity.DetalleCompra;

@Repository
public interface DetalleCompraRepository  extends JpaRepository<DetalleCompra,Long> {
}
