package pe.edu.cibertec.ferrestock.ventas.repository;


import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.cibertec.ferrestock.ventas.entity.Compra;

@Repository
public interface CompraRepository extends JpaRepository<Compra,Long> {


}
