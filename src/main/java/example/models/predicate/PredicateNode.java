package example.models.predicate;

import example.models.meta.MetaAttribute;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/*
 * Узел дерева предиката
 */
@Entity
@Table(name = "predicate_node", schema = "predicate")
@Getter
@Setter
public class PredicateNode {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false)
    private NodeType nodeType;

    @Enumerated(EnumType.STRING)
    @Column(name = "operator_type")
    private OperatorType operatorType;

    // === ССЫЛКА НА АТТРИБУТ ===

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meta_attribute_id")
    private MetaAttribute metaAttribute;

    /**
     * Путь к атрибуту через связи (для nodeType = PATH_EXPRESSION)
     */
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "path_expression_id")
    private PredicatePathExpression pathExpression;


    // === ЕДИНОЕ ХРАНЕНИЕ ЗНАЧЕНИЯ ===
    /* Значение для VALUE_CONSTANT и правой части сравнений
     * Используется когда nodeType = VALUE_CONSTANT
     * или для rightOperand в сравнениях
     */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "value_id")
    private PredicateNodeValue value;

    // === СВЯЗИ МЕЖДУ УЗЛАМИ ===

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "left_operand_id")
    private PredicateNode leftOperand;

    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "right_operand_id")
    private PredicateNode rightOperand;

    /**
     * Множественные значения для оператора IN
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "predicate_node_id")
    private List<PredicateNodeValue> inValues = new ArrayList<>();

}