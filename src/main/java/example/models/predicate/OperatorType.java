// OperatorType.java
package example.models.predicate;

import example.models.meta.BasicType;
import example.models.meta.BasicTypeCategory;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum OperatorType {
  // Логические операторы
  AND(BasicTypeCategory.LOGICAL, BasicTypeCategory.LOGICAL),
  OR(BasicTypeCategory.LOGICAL, BasicTypeCategory.LOGICAL),
  NOT(BasicTypeCategory.LOGICAL, BasicTypeCategory.NONE, false, false),

  // Null-проверки
  IS_NULL(BasicTypeCategory.ALL, BasicTypeCategory.NONE, false, false),
  IS_NOT_NULL(BasicTypeCategory.ALL, BasicTypeCategory.NONE, false, false),

  // Операторы сравнения
  EQ(BasicTypeCategory.COMPARABLE, BasicTypeCategory.COMPARABLE),
  NE(BasicTypeCategory.COMPARABLE, BasicTypeCategory.COMPARABLE),

  // Операторы упорядочения
  GT(BasicTypeCategory.ORDERED, BasicTypeCategory.ORDERED),
  LT(BasicTypeCategory.ORDERED, BasicTypeCategory.ORDERED),
  GOE(BasicTypeCategory.ORDERED, BasicTypeCategory.ORDERED),
  LOE(BasicTypeCategory.ORDERED, BasicTypeCategory.ORDERED),

  // Строковые операторы
  LIKE(BasicTypeCategory.TEXT, BasicTypeCategory.TEXT),
  STARTS_WITH(BasicTypeCategory.TEXT, BasicTypeCategory.TEXT),
  ENDS_WITH(BasicTypeCategory.TEXT, BasicTypeCategory.TEXT),
  CONTAINS(BasicTypeCategory.TEXT, BasicTypeCategory.TEXT),

  // Операторы множеств
  IN(BasicTypeCategory.COMPARABLE, BasicTypeCategory.COMPARABLE),
  NOT_IN(BasicTypeCategory.COMPARABLE, BasicTypeCategory.COMPARABLE),

  // Географические операторы
  DISTANCE_SPHERE(
      BasicTypeCategory.SPATIAL, BasicTypeCategory.SPATIAL, true, false, BasicType.DOUBLE);
//TODO spatial operators
//    WITHIN(BasicTypeCategory.SPATIAL, BasicTypeCategory.SPATIAL)
//    INTERSECTS(BasicTypeCategory.SPATIAL, BasicTypeCategory.SPATIAL)
//    DISTANCE_WITHIN(BasicTypeCategory.SPATIAL, BasicTypeCategory.SPATIAL)

  private final BasicTypeCategory allowedLeftCategory;
  private final BasicTypeCategory allowedRightCategory;
  private final boolean requiresRightOperand;
  private final boolean typesMustMatch;
  private final BasicType resultType;

  // Конструктор с дефолтными значениями (requiresRightOperand = true, typesMustMatch = true,
  // resultType = BOOLEAN)
  OperatorType(BasicTypeCategory allowedLeftCategory, BasicTypeCategory allowedRightCategory) {
    this(allowedLeftCategory, allowedRightCategory, true, true, BasicType.BOOLEAN);
  }

  // Конструктор для операторов с кастомными флагами
  OperatorType(
      BasicTypeCategory allowedLeftCategory,
      BasicTypeCategory allowedRightCategory,
      boolean requiresRightOperand,
      boolean typesMustMatch) {
    this(
        allowedLeftCategory,
        allowedRightCategory,
        requiresRightOperand,
        typesMustMatch,
        BasicType.BOOLEAN);
  }
}
