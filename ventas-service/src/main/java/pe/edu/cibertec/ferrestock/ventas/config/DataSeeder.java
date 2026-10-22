
package pe.edu.cibertec.ferrestock.ventas.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.cibertec.ferrestock.ventas.entity.Categoria;
import pe.edu.cibertec.ferrestock.ventas.entity.Cliente;
import pe.edu.cibertec.ferrestock.ventas.entity.Producto;
import pe.edu.cibertec.ferrestock.ventas.repository.CategoriaRepository;
import pe.edu.cibertec.ferrestock.ventas.repository.ClienteRepository;
import pe.edu.cibertec.ferrestock.ventas.repository.ProductoRepository;

import java.math.BigDecimal;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;
    private final CategoriaRepository categoriaRepository;

    @Override
    @Transactional
    public void run(String... args) {

        if (productoRepository.count() == 0) {

            Categoria herramientas = obtenerCategoria(
                    "Herramientas", "Herramientas manuales y eléctricas");

            Categoria construccion = obtenerCategoria(
                    "Construcción", "Materiales para construcción");

            Categoria ferreteria = obtenerCategoria(
                    "Ferretería", "Tornillos, clavos y fijaciones");

            Categoria pintura = obtenerCategoria(
                    "Pinturas", "Pinturas y accesorios para pintar");

            productoRepository.saveAll(List.of(
                    producto("Taladro percutor 650W",
                            "Taladro con maletín y 5 brocas",
                            "289.90", 12, 3, herramientas),

                    producto("Amoladora angular 4 1/2 pulg. 850W",
                            "Amoladora con disco de corte y guarda",
                            "199.00", 8, 2, herramientas),

                    producto("Martillo de uña 16 oz",
                            "Mango de fibra de vidrio antivibración",
                            "34.50", 40, 5, herramientas),

                    producto("Juego de destornilladores x6",
                            "Punta plana y estrella, mango ergonómico",
                            "29.90", 25, 5, herramientas),

                    producto("Cemento Portland Tipo I 42.5 kg",
                            "Bolsa de cemento para uso general",
                            "32.80", 150, 20, construccion),

                    producto("Arena gruesa (saco 40 kg)",
                            "Arena para mezcla de concreto",
                            "9.50", 3, 10, construccion),

                    producto("Tornillo drywall 6x1 pulg. (caja x100)",
                            "Tornillo punta fina fosfatado",
                            "12.00", 80, 15, ferreteria),

                    producto("Clavo de acero 2 pulg. (kg)",
                            "Clavo para madera, venta por kilo",
                            "7.90", 60, 10, ferreteria),

                    producto("Pintura látex blanco 4 L",
                            "Pintura lavable para interiores",
                            "64.90", 20, 4, pintura),

                    producto("Rodillo de felpa 9 pulg. con bandeja",
                            "Kit de pintado para muros",
                            "22.50", 2, 5, pintura)
            ));

            log.info("Se cargaron 10 productos y sus categorías.");
        } else {
            log.info("Los productos ya existen; se omite su carga.");
        }

        if (clienteRepository.count() == 0) {
            clienteRepository.saveAll(List.of(
                    new Cliente(null, "Juan Pérez Quispe", "45879632", "DNI"),
                    new Cliente(null, "María Torres Huamán", "70214589", "DNI"),
                    new Cliente(null, "Construcciones Andinas S.A.C.",
                            "20601234567", "RUC")
            ));

            log.info("Se cargaron 3 clientes.");
        }
    }

    private Categoria obtenerCategoria(String nombre, String descripcion) {
        return categoriaRepository.findAll().stream()
                .filter(c -> c.getNombre().equalsIgnoreCase(nombre))
                .findFirst()
                .orElseGet(() -> categoriaRepository.save(
                        Categoria.builder()
                                .nombre(nombre)
                                .descripcion(descripcion)
                                .build()
                ));
    }

    private Producto producto(
            String nombre,
            String descripcion,
            String precio,
            int stock,
            int stockMinimo,
            Categoria categoria) {

        Producto p = new Producto();
        p.setNombre(nombre);
        p.setDescripcion(descripcion);
        p.setPrecio(new BigDecimal(precio));
        p.setStock(stock);
        p.setStockMinimo(stockMinimo);
        p.setCategoria(categoria);

        return p;
    }
}
