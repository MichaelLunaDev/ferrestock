package pe.edu.cibertec.ferrestock.ventas.controller;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pe.edu.cibertec.ferrestock.ventas.dto.ClienteDto;
import pe.edu.cibertec.ferrestock.ventas.entity.Cliente;
import pe.edu.cibertec.ferrestock.ventas.repository.ClienteRepository;

import java.util.List;

@RestController
@RequestMapping("/api/clientes")
@RequiredArgsConstructor
public class ClienteController {

    private final ClienteRepository clienteRepository;

    @GetMapping
    public ResponseEntity<List<ClienteDto>> listarClientes() {
        List<ClienteDto> clientes = clienteRepository.findAll().stream()
                .map(this::aDto)
                .toList();
        return ResponseEntity.ok(clientes);
    }

    @PostMapping
    public ResponseEntity<ClienteDto> crearCliente(@Valid @RequestBody ClienteDto clienteDto) {
        try {
            Cliente cliente = new Cliente();
            cliente.setNombre(clienteDto.nombre());
            cliente.setDocumento(clienteDto.documento());
            cliente.setTipoDocumento(clienteDto.tipoDocumento());
            Cliente guardado = clienteRepository.save(cliente);
            return new ResponseEntity<>(aDto(guardado), HttpStatus.CREATED);
        } catch (DataIntegrityViolationException ex) {
            return ResponseEntity.status(HttpStatus.CONFLICT).build();
        }
    }

    private ClienteDto aDto(Cliente cliente) {
        return new ClienteDto(cliente.getId(), cliente.getNombre(), cliente.getDocumento(), cliente.getTipoDocumento());
    }
}
