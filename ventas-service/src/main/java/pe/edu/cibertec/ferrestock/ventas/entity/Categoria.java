package pe.edu.cibertec.ferrestock.ventas.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

    @Entity
    @Table(name = "categorias")
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public class Categoria {

        @Id
        @GeneratedValue(strategy = GenerationType.IDENTITY)
        private Long id;

        @Column(name = "nombre_categoria", nullable = false, unique = true, length = 100)
        private String nombre;

        @Column(name = "descripcion", length = 250)
        private String descripcion;

        @OneToMany(mappedBy = "categoria", cascade = CascadeType.ALL)
        @Builder.Default
        private List<Producto> productos = new ArrayList<>();
    }

