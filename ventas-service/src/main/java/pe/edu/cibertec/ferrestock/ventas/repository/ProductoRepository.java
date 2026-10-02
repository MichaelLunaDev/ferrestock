package pe.edu.cibertec.ferrestock.ventas.repository;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import pe.edu.cibertec.ferrestock.ventas.entity.Producto;

import java.util.Collection;
import java.util.List;

public interface ProductoRepository extends JpaRepository<Producto, Long> {

    /**
     * Bloquea las filas de los productos hasta que termine la transacción,
     * para que dos ventas simultáneas no descuenten el mismo stock.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Producto p where p.id in :ids")
    List<Producto> findAllByIdForUpdate(Collection<Long> ids);
}
