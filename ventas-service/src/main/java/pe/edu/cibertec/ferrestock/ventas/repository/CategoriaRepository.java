package pe.edu.cibertec.ferrestock.ventas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import pe.edu.cibertec.ferrestock.ventas.entity.Categoria;

@Repository
public interface CategoriaRepository extends JpaRepository<Categoria,Long> {


}
