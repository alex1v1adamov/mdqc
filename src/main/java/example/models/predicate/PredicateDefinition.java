package example.models.predicate;

import example.models.meta.MetaEntity;
import example.models.meta.MetaAttribute;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Определение предиката - корневая сущность
 */
@Entity
@Table(name = "predicate_definition", schema = "predicate")
@Getter
@Setter
public class PredicateDefinition {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "name", nullable = false)
    private String name;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meta_entity_id", nullable = false)
    private MetaEntity metaEntity;

    @OneToOne(cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @JoinColumn(name = "root_node_id")
    private PredicateNode rootNode;
}