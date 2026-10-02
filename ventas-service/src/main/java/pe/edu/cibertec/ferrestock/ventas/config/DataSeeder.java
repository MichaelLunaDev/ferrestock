package pe.edu.cibertec.ferrestock.ventas.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.cibertec.ferrestock.ventas.entity.Cliente;
import pe.edu.cibertec.ferrestock.ventas.entity.Producto;
import pe.edu.cibertec.ferrestock.ventas.repository.ClienteRepository;
import pe.edu.cibertec.ferrestock.ventas.repository.ProductoRepository;

import java.math.BigDecimal;
import java.util.List;

/** Carga datos de ejemplo solo si la tabla productos está vacía. */
@Slf4j
@Component
@RequiredArgsConstructor
public class DataSeeder implements CommandLineRunner {

    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;

    @Override
    @Transactional
    public void run(String... args) {
        if (productoRepository.count() > 0) {
            log.info("Productos ya cargados, se omite la carga de datos de ejemplo");
            return;
        }

        productoRepository.saveAll(List.of(
                producto("Taladro percutor 650W", "Taladro percutor 1/2 pulgada con maletín y 5 brocas", "HER-TAL-650", "289.90", 12, "Herramientas eléctricas"),
                producto("Amoladora angular 4 1/2 pulg. 850W", "Amoladora con disco de corte y guarda", "HER-AMO-850", "199.00", 8, "Herramientas eléctricas"),
                producto("Martillo de uña 16 oz", "Mango de fibra de vidrio antivibración", "HER-MAR-16", "34.50", 40, "Herramientas manuales"),
                producto("Juego de destornilladores x6", "Punta plana y estrella, mango ergonómico", "HER-DES-06", "29.90", 25, "Herramientas manuales"),
                producto("Cemento Portland Tipo I 42.5 kg", "Bolsa de cemento para uso general", "CON-CEM-425", "32.80", 150, "Construcción"),
                producto("Arena gruesa (saco 40 kg)", "Arena para mezcla de concreto", "CON-ARE-40", "9.50", 3, "Construcción"),
                producto("Tornillo drywall 6x1 pulg. (caja x100)", "Tornillo punta fina fosfatado", "FIJ-TDW-100", "12.00", 80, "Fijaciones"),
                producto("Clavo de acero 2 pulg. (kg)", "Clavo para madera, venta por kilo", "FIJ-CLA-2", "7.90", 60, "Fijaciones"),
                producto("Pintura látex blanco 4 L", "Pintura lavable acabado mate para interiores", "PIN-LAT-4L", "64.90", 20, "Pinturas"),
                producto("Rodillo de felpa 9 pulg. con bandeja", "Kit de pintado para muros", "PIN-ROD-9", "22.50", 2, "Pinturas")
        ));

        if (clienteRepository.count() == 0) {
            clienteRepository.saveAll(List.of(
                    new Cliente(null, "Juan Pérez Quispe", "45879632", "DNI"),
                    new Cliente(null, "María Torres Huamán", "70214589", "DNI"),
                    new Cliente(null, "Construcciones Andinas S.A.C.", "20601234567", "RUC")
            ));
        }

        log.info("Datos de ejemplo cargados: 10 productos y 3 clientes");
    }

    private static Producto producto(String nombre, String descripcion, String sku,
                                     String precio, int stock, String categoria) {
        return new Producto(null, nombre, descripcion, sku, new BigDecimal(precio), stock, categoria);
    }
}
