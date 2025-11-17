package example.models.predicate;

import com.yahoo.elide.annotation.Include;
import example.models.meta.BasicType;
import example.models.meta.MetaEnumValue;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;
import org.locationtech.jts.geom.Point;

/* Универсальное хранилище значений для предикатов
 */
@Include(rootLevel = false)
@Entity
@Table(name = "predicate_node_value", schema = "predicate")
@Getter
@Setter
public class PredicateNodeValue {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  // === ТИПОБЕЗОПАСНЫЕ ПОЛЯ ===
  /**
   * ПРАВИЛА ДЛЯ ЗНАЧЕНИЙ: - Заполняется ТОЛЬКО ОДНО поле в зависимости от valueType - Все остальные
   * поля должны быть null - Для ENUM используется enumValue, stringValue = null - Тип значения
   * должен соответствовать типу атрибута в сравнении
   */
  @Column(name = "string_value")
  private String stringValue;

  @Column(name = "boolean_value")
  private Boolean booleanValue;

  @Column(name = "integer_value")
  private Integer integerValue;

  @Column(name = "long_value")
  private Long longValue;

  @Column(name = "date_value")
  private OffsetDateTime offsetDateTimeValue;

  @Column(name = "timestamp_value")
  private LocalDateTime timestampValue;

  @Column(name = "double_value")
  private Double doubleValue;

  @Column(name = "point_value")
  private Point pointValue;

  /**
   * Значение перечисления ПРАВИЛА: - Используется ТОЛЬКО когда valueType = ENUM -
   * metaEnumValue.metaEnum должен соответствовать metaAttribute.metaEnum - Запрещено для других
   * valueType
   */
  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "enum_value_id")
  private MetaEnumValue enumValue;

  /**
   * Тип значения ПРАВИЛА: - Обязательное поле - Определяет, какое из полей значения активно -
   * Должен соответствовать basicType сравниваемого атрибута - Для ENUM должен совпадать с
   * metaAttribute.basicType
   */
  @Enumerated(EnumType.STRING)
  @Column(name = "value_type", nullable = false)
  private BasicType valueType;

  // === МЕТОДЫ ДЛЯ РАБОТЫ СО ЗНАЧЕНИЯМИ ===

  public void setValue(Object value, BasicType type) {
    this.valueType = type;

    // Очищаем все поля перед установкой нового значения
    this.stringValue = null;
    this.booleanValue = null;
    this.integerValue = null;
    this.longValue = null;
    this.offsetDateTimeValue = null;
    this.timestampValue = null;
    this.enumValue = null;

    if (value == null) return;

    switch (type) {
      case STRING -> this.stringValue = (String) value;
      case BOOLEAN -> this.booleanValue = (Boolean) value;
      case INTEGER -> this.integerValue = (Integer) value;
      case OFFSET_DATE_TIME -> this.offsetDateTimeValue = (OffsetDateTime) value;
      case ENUM -> this.enumValue = (MetaEnumValue) value;
      case POINT -> this.pointValue = (Point) value;
    }
  }

  public Object getValue() {
    if (valueType == null) return null;

    return switch (valueType) {
      case STRING -> stringValue;
      case BOOLEAN -> booleanValue;
      case INTEGER -> integerValue;
      case OFFSET_DATE_TIME -> offsetDateTimeValue;
      case ENUM -> enumValue;
      case DOUBLE -> doubleValue;
      case POINT -> pointValue;
    };
  }
}
