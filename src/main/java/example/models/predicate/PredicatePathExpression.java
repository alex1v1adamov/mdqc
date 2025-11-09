package example.models.predicate;

import example.models.meta.MetaAttribute;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/* Выражение пути для навигации по связям между сущностями
 */
@Entity
@Table(name = "predicate_path_expression", schema = "predicate")
@Getter
@Setter
public class PredicatePathExpression {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  /**
   * Корневой атрибут (начало пути) ПРАВИЛА: - Обязательное поле - attributeCategory должен быть
   * ENTITY - Должен принадлежать той же сущности, что и корень предиката - Тип: всегда
   * MetaAttribute с category = ENTITY
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "root_attribute_id", nullable = false)
  private MetaAttribute rootAttribute;

  /**
   * Цепочка атрибутов для навигации ПРАВИЛА: - Может быть пустым (путь из одного сегмента) - Все
   * атрибуты кроме последнего должны иметь category = ENTITY - Последний атрибут должен иметь
   * category = BASIC - Порядок определяет последовательность навигации - Все атрибуты должны быть
   * совместимы по типам
   */
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinTable(
      name = "predicate_path_expression_attributes",
      schema = "predicate",
      joinColumns = @JoinColumn(name = "path_expression_id"),
      inverseJoinColumns = @JoinColumn(name = "meta_attribute_id"))
  @OrderColumn(name = "attribute_order")
  private List<MetaAttribute> pathAttributes = new ArrayList<>();

  /**
   * Конечный атрибут (последний в цепочке) ПРАВИЛА: - Вычисляемое поле, должно соответствовать
   * последнему pathAttributes - Если pathAttributes пуст, то равен rootAttribute - Всегда должен
   * иметь category = BASIC (конечный атрибут) - Используется для оптимизации запросов
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "target_attribute_id")
  private MetaAttribute targetAttribute;
}
