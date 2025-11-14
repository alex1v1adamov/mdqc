package example.models.predicate;

import example.models.meta.BasicType;
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
import jakarta.persistence.JoinTable;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OneToOne;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

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
   * Тип узла дерева предиката ПРАВИЛА: Обязательное поле для всех узлов; Определяет семантику узла
   * и набор допустимых полей
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "node_type", nullable = false)
  private NodeType nodeType;

  /**
   * Тип оператора ПРАВИЛА: Обязательно для nodeType = EVALUATION_OPERATION; Запрещено для остальных
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "operator_type")
  private OperatorType operatorType;

  /**
   * Путь к атрибуту через связи ПРАВИЛА: Обязательно для nodeType = PATH_EXPRESSION; Запрещено для
   * остальных
   */
  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "path_expression_id")
  private PredicatePathExpression pathExpression;

  // === СВЯЗИ МЕЖДУ УЗЛАМИ ===

  /**
   * Левый операнд ПРАВИЛА: - Обязательно для EVALUATION_OPERATION; Запрещено для остальных; у
   * объекта этого поля BasicTypeCategory (вычисляется из operatorType) должна соответствовать
   * operatorType.allowedLeftCategory этого объекта this;
   */
  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "left_operand_id")
  private PredicateNode leftOperand;

  /**
   * Правый операнд ПРАВИЛА: - Возможно для EVALUATION_OPERATION; Запрещено для остальных; у объекта
   * этого поля BasicTypeCategory (вычисляется из operatorType) должна соответствовать
   * operatorType.allowedRightCategory этого объекта this;
   */
  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "right_operand_id")
  private PredicateNode rightOperand;

  // === ЕДИНОЕ ХРАНЕНИЕ ЗНАЧЕНИЯ ===
  /**
   * Путь к атрибуту через связи ПРАВИЛА: Разрешено для nodeType = VALUE_CONSTANT; Запрещено для
   * остальных; Может быть заполнено либо это поле, либо поле values
   */
  @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "value_id")
  private PredicateNodeValue value;

  /**
   * Значения атрибута связи ПРАВИЛА: Разрешено для nodeType = VALUE_CONSTANT; Запрещено для
   * остальных; Может быть заполнено либо это поле, либо поле value
   */
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @JoinTable(
      name = "predicate_node_in_values",
      schema = "predicate",
      joinColumns = @JoinColumn(name = "predicate_node_id"),
      inverseJoinColumns = @JoinColumn(name = "node_value_id"))
  @OrderColumn(name = "value_order")
  private List<PredicateNodeValue> values = new ArrayList<>();


    public BasicType getNodeReturnType() {
        return switch (nodeType) {
            case VALUE_CONSTANT -> value != null ?  value.getValueType() : values.stream().map(PredicateNodeValue::getValueType).findAny().get();
            case PATH_EXPRESSION -> pathExpression.getRootAttribute().getBasicType();
            case EVALUATION_OPERATION -> operatorType.getResultType();
        };
    }

}
