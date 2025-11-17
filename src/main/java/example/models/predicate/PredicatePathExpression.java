package example.models.predicate;

import com.yahoo.elide.annotation.Include;
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
@Include(rootLevel = false)
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
   * Конечный атрибут (конец пути) ПРАВИЛА: Обязательное поле; attributeCategory должен быть типа
   * BASIC;
   */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "final_path_attribute", nullable = false)
  private MetaAttribute finalPathAttribute;

  /**
   * Цепочка атрибутов для навигации ПРАВИЛА: Может быть пустым (путь из одного сегмента); Все
   * атрибуты должны иметь category = ENTITY; Порядок определяет последовательность навигации; Все
   * атрибуты должны быть совместимы по типам
   */
  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinTable(
      name = "predicate_path_expression_attributes",
      schema = "predicate",
      joinColumns = @JoinColumn(name = "path_expression_id"),
      inverseJoinColumns = @JoinColumn(name = "meta_attribute_id"))
  @OrderColumn(name = "attribute_order")
  private List<MetaAttribute> pathAttributes = new ArrayList<>();
}
