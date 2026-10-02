package pe.edu.cibertec.ferrestock.ventas.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import pe.edu.cibertec.ferrestock.ventas.entity.Cliente;

public interface ClienteRepository extends JpaRepository<Cliente, Long> {
}
