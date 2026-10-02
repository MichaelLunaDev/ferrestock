package pe.edu.cibertec.ferrestock.ventas.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.cibertec.ferrestock.ventas.dto.ItemVentaRequest;
import pe.edu.cibertec.ferrestock.ventas.dto.VentaRequest;
import pe.edu.cibertec.ferrestock.ventas.dto.VentaResponse;
import pe.edu.cibertec.ferrestock.ventas.entity.Cliente;
import pe.edu.cibertec.ferrestock.ventas.entity.DetalleVenta;
import pe.edu.cibertec.ferrestock.ventas.entity.EstadoVenta;
import pe.edu.cibertec.ferrestock.ventas.entity.Producto;
import pe.edu.cibertec.ferrestock.ventas.entity.Venta;
import pe.edu.cibertec.ferrestock.ventas.exception.RecursoNoEncontradoException;
import pe.edu.cibertec.ferrestock.ventas.exception.StockInsuficienteException;
import pe.edu.cibertec.ferrestock.ventas.repository.ClienteRepository;
import pe.edu.cibertec.ferrestock.ventas.repository.ProductoRepository;
import pe.edu.cibertec.ferrestock.ventas.repository.VentaRepository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class VentaService {

    private final VentaRepository ventaRepository;
    private final ProductoRepository productoRepository;
    private final ClienteRepository clienteRepository;

    /**
     * Registra una venta completa en UNA sola transacción:
     * valida todo el stock primero y solo si todo alcanza descuenta y guarda.
     * Cualquier excepción provoca rollback, así que nunca queda una venta a medias.
     */
    @Transactional
    public VentaResponse registrarVenta(VentaRequest request) {
        Cliente cliente = clienteRepository.findById(request.clienteId())
                .orElseThrow(() -> new RecursoNoEncontradoException(
                        "No existe el cliente con id " + request.clienteId()));

        // Si el mismo producto viene en varias líneas, se suman las cantidades
        Map<Long, Integer> cantidades = new LinkedHashMap<>();
        for (ItemVentaRequest item : request.items()) {
            cantidades.merge(item.productoId(), item.cantidad(), Integer::sum);
        }

        Map<Long, Producto> productos = productoRepository.findAllByIdForUpdate(cantidades.keySet()).stream()
                .collect(Collectors.toMap(Producto::getId, Function.identity()));

        // 1) Validar TODO antes de tocar nada
        List<String> faltantes = new ArrayList<>();
        cantidades.forEach((productoId, cantidad) -> {
            Producto p = productos.get(productoId);
            if (p == null) {
                throw new RecursoNoEncontradoException("No existe el producto con id " + productoId);
            }
            if (p.getStock() < cantidad) {
                faltantes.add("%s (id %d): solicitado %d, disponible %d"
                        .formatted(p.getNombre(), p.getId(), cantidad, p.getStock()));
            }
        });
        if (!faltantes.isEmpty()) {
            throw new StockInsuficienteException(faltantes);
        }

        // 2) Todo es válido: descontar stock y armar la venta
        Venta venta = new Venta();
        venta.setFecha(LocalDateTime.now());
        venta.setCliente(cliente);
        venta.setVendedor(request.vendedor());
        venta.setEstado(EstadoVenta.COMPLETADA);

        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<Long, Integer> e : cantidades.entrySet()) {
            Producto producto = productos.get(e.getKey());
            int cantidad = e.getValue();
            producto.setStock(producto.getStock() - cantidad);

            DetalleVenta detalle = new DetalleVenta();
            detalle.setProducto(producto);
            detalle.setCantidad(cantidad);
            detalle.setPrecioUnitario(producto.getPrecio());
            detalle.setSubtotal(producto.getPrecio().multiply(BigDecimal.valueOf(cantidad)));
            venta.agregarDetalle(detalle);

            total = total.add(detalle.getSubtotal());
        }
        venta.setTotal(total);

        return VentaResponse.conDetalle(ventaRepository.save(venta));
    }

    @Transactional(readOnly = true)
    public List<VentaResponse> listar() {
        return ventaRepository.findAllByOrderByFechaDesc().stream()
                .map(VentaResponse::resumen)
                .toList();
    }

    @Transactional(readOnly = true)
    public VentaResponse obtener(Long id) {
        return ventaRepository.findWithDetallesById(id)
                .map(VentaResponse::conDetalle)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe la venta con id " + id));
    }
}
