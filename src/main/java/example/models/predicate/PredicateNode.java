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

    /**
     * Тип узла дерева предиката
     * ПРАВИЛА:
     * - Обязательное поле для всех узлов
     * - Определяет семантику узла и набор допустимых полей
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "node_type", nullable = false)
    private NodeType nodeType;

    /**
     * Тип оператора
     * ПРАВИЛА:
     * - Обязательно для LOGICAL_OPERATOR и COMPARISON_OPERATOR
     * - Запрещено для VALUE_CONSTANT и PATH_EXPRESSION
     * - Для LOGICAL_OPERATOR: AND, OR, NOT
     * - Для COMPARISON_OPERATOR: EQ, GT, LT, LIKE, IN, IS_NULL и т.д.
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "operator_type")
    private OperatorType operatorType;

    // === ССЫЛКА НА АТТРИБУТ ===
    /**
     * Прямая ссылка на мета-атрибут
     * ПРАВИЛА:
     * - Используется ТОЛЬКО когда pathExpression = null
     * - Для COMPARISON_OPERATOR: атрибут для сравнения
     * - Для PATH_EXPRESSION: корневой атрибут (если путь состоит из одного сегмента)
     * - Запрещено для LOGICAL_OPERATOR и VALUE_CONSTANT
     * - Приоритет: metaAttribute > pathExpression
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meta_attribute_id")
    private MetaAttribute metaAttribute;

    /**
     * Путь к атрибуту через связи
     * ПРАВИЛА:
     * - Обязательно для nodeType = PATH_EXPRESSION
     * - Для COMPARISON_OPERATOR: альтернатива metaAttribute для сложных путей
     * - Запрещено для LOGICAL_OPERATOR и VALUE_CONSTANT
     * - Если заполнено, то metaAttribute должен быть null
     */
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "path_expression_id")
    private PredicatePathExpression pathExpression;

    // === ЕДИНОЕ ХРАНЕНИЕ ЗНАЧЕНИЯ ===
    /**
     * Значение константы или правого операнда
     * ПРАВИЛА:
     * - Обязательно для nodeType = VALUE_CONSTANT
     * - Для COMPARISON_OPERATOR: правое значение сравнения (кроме IS_NULL/IS_NOT_NULL)
     * - Запрещено для LOGICAL_OPERATOR и PATH_EXPRESSION
     * - Для оператора IN используйте inValues вместо value
     */
    @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "value_id")
    private PredicateNodeValue value;

    // === СВЯЗИ МЕЖДУ УЗЛАМИ ===

    /**
     * Левый операнд
     * ПРАВИЛА:
     * - Обязательно для LOGICAL_OPERATOR (AND, OR) и COMPARISON_OPERATOR
     * - Для NOT: один левый операнд
     * - Запрещено для VALUE_CONSTANT и PATH_EXPRESSION
     * - Тип узла: любой кроме VALUE_CONSTANT как самостоятельное выражение
     */
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "left_operand_id")
    private PredicateNode leftOperand;

    /**
     * Правый операнд
     * ПРАВИЛА:
     * - Обязательно для LOGICAL_OPERATOR (AND, OR) и COMPARISON_OPERATOR
     * - Для NOT: правый операнд должен быть null
     * - Запрещено для VALUE_CONSTANT и PATH_EXPRESSION
     * - Для COMPARISON_OPERATOR: обычно VALUE_CONSTANT или PATH_EXPRESSION
     */
    @OneToOne(cascade = CascadeType.ALL)
    @JoinColumn(name = "right_operand_id")
    private PredicateNode rightOperand;

    /**
     * Множественные значения для оператора IN
     * ПРАВИЛА:
     * - Обязательно для COMPARISON_OPERATOR с operatorType = IN или NOT_IN
     * - Минимум одно значение
     * - Все значения должны быть одного типа
     * - Запрещено для других типов операторов
     * - Если заполнено, то value должен быть null
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinColumn(name = "predicate_node_id")
    private List<PredicateNodeValue> inValues = new ArrayList<>();
}