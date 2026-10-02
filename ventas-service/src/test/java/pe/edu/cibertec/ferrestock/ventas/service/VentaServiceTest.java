package pe.edu.cibertec.ferrestock.ventas.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import pe.edu.cibertec.ferrestock.ventas.dto.ItemVentaRequest;
import pe.edu.cibertec.ferrestock.ventas.dto.VentaRequest;
import pe.edu.cibertec.ferrestock.ventas.dto.VentaResponse;
import pe.edu.cibertec.ferrestock.ventas.entity.Cliente;
import pe.edu.cibertec.ferrestock.ventas.entity.EstadoVenta;
import pe.edu.cibertec.ferrestock.ventas.entity.Producto;
import pe.edu.cibertec.ferrestock.ventas.entity.Venta;
import pe.edu.cibertec.ferrestock.ventas.exception.StockInsuficienteException;
import pe.edu.cibertec.ferrestock.ventas.repository.ClienteRepository;
import pe.edu.cibertec.ferrestock.ventas.repository.ProductoRepository;
import pe.edu.cibertec.ferrestock.ventas.repository.VentaRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VentaServiceTest {

    @Mock
    VentaRepository ventaRepository;
    @Mock
    ProductoRepository productoRepository;
    @Mock
    ClienteRepository clienteRepository;

    @InjectMocks
    VentaService ventaService;

    Producto martillo;
    Producto rodillo;

    @BeforeEach
    void setUp() {
        martillo = new Producto(1L, "Martillo", null, "HER-MAR-16", new BigDecimal("34.50"), 40, "Herramientas");
        rodillo = new Producto(2L, "Rodillo", null, "PIN-ROD-9", new BigDecimal("22.50"), 2, "Pinturas");
        when(clienteRepository.findById(1L)).thenReturn(Optional.of(new Cliente(1L, "Juan", "45879632", "DNI")));
        when(productoRepository.findAllByIdForUpdate(anyCollection())).thenReturn(List.of(martillo, rodillo));
    }

    @Test
    void registraVentaDescontandoStockYCalculandoTotal() {
        when(ventaRepository.save(any(Venta.class))).thenAnswer(inv -> inv.getArgument(0));

        VentaResponse venta = ventaService.registrarVenta(new VentaRequest(1L, "vendedor1", List.of(
                new ItemVentaRequest(1L, 3),
                new ItemVentaRequest(2L, 2))));

        assertThat(venta.estado()).isEqualTo(EstadoVenta.COMPLETADA);
        assertThat(venta.total()).isEqualByComparingTo("148.50"); // 3*34.50 + 2*22.50
        assertThat(venta.detalles()).hasSize(2);
        assertThat(martillo.getStock()).isEqualTo(37);
        assertThat(rodillo.getStock()).isZero();
    }

    @Test
    void rechazaTodaLaVentaSiUnProductoNoTieneStock() {
        VentaRequest request = new VentaRequest(1L, "vendedor1", List.of(
                new ItemVentaRequest(1L, 3),     // hay stock
                new ItemVentaRequest(2L, 5)));   // solo hay 2

        assertThatThrownBy(() -> ventaService.registrarVenta(request))
                .isInstanceOf(StockInsuficienteException.class);

        // No se tocó el stock de NINGÚN producto, ni siquiera del que sí alcanzaba
        assertThat(martillo.getStock()).isEqualTo(40);
        assertThat(rodillo.getStock()).isEqualTo(2);
        verify(ventaRepository, never()).save(any());
    }

    @Test
    void sumaCantidadesSiElMismoProductoVieneRepetido() {
        VentaRequest request = new VentaRequest(1L, "vendedor1", List.of(
                new ItemVentaRequest(2L, 1),
                new ItemVentaRequest(2L, 2)));   // total 3 > stock 2

        assertThatThrownBy(() -> ventaService.registrarVenta(request))
                .isInstanceOf(StockInsuficienteException.class);
        assertThat(rodillo.getStock()).isEqualTo(2);
    }
}
