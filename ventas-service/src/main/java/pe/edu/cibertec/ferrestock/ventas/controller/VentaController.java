package pe.edu.cibertec.ferrestock.ventas.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import pe.edu.cibertec.ferrestock.ventas.dto.VentaRequest;
import pe.edu.cibertec.ferrestock.ventas.dto.VentaResponse;
import pe.edu.cibertec.ferrestock.ventas.entity.Producto;
import pe.edu.cibertec.ferrestock.ventas.repository.ProductoRepository;
import pe.edu.cibertec.ferrestock.ventas.service.VentaService;

import java.util.List;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class VentaController {

    private final VentaService ventaService;
    private final ProductoRepository productoRepository;

    @PostMapping("/ventas")
    @ResponseStatus(HttpStatus.CREATED)
    public VentaResponse registrar(@Valid @RequestBody VentaRequest request) {
        return ventaService.registrarVenta(request);
    }

    @GetMapping("/ventas")
    public List<VentaResponse> listar() {
        return ventaService.listar();
    }

    @GetMapping("/ventas/{id}")
    public VentaResponse obtener(@PathVariable Long id) {
        return ventaService.obtener(id);
    }

    @GetMapping("/productos")
    public List<Producto> listarProductos() {
        return productoRepository.findAll();
    }
}
