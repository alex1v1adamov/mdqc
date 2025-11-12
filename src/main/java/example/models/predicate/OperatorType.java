package example.models.predicate;

import example.models.meta.BasicType;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import lombok.Getter;

@Getter
public enum OperatorType {
  // === ЛОГИЧЕСКИЕ ОПЕРАТОРЫ ===
  AND(BasicType.BOOLEAN, List.of(BasicType.BOOLEAN), List.of(BasicType.BOOLEAN)),
  OR(BasicType.BOOLEAN, List.of(BasicType.BOOLEAN), List.of(BasicType.BOOLEAN)),
  NOT(BasicType.BOOLEAN, List.of(BasicType.BOOLEAN), null),
  // === ОПЕРАТОРЫ СРАВНЕНИЯ (универсальные) ===
  EQ(BasicType.BOOLEAN, getAllComparableTypes(), getAllComparableTypes()),
  NE(BasicType.BOOLEAN, getAllComparableTypes(), getAllComparableTypes()),
  GT(BasicType.BOOLEAN, getComparableTypes(), getComparableTypes()),
  LT(BasicType.BOOLEAN, getComparableTypes(), getComparableTypes()),
  GOE(BasicType.BOOLEAN, getComparableTypes(), getComparableTypes()),
  LOE(BasicType.BOOLEAN, getComparableTypes(), getComparableTypes()),
  // === СТРОКОВЫЕ ОПЕРАТОРЫ ===
  LIKE(BasicType.BOOLEAN, List.of(BasicType.STRING), List.of(BasicType.STRING)),
  STARTS_WITH(BasicType.BOOLEAN, List.of(BasicType.STRING), List.of(BasicType.STRING)),
  ENDS_WITH(BasicType.BOOLEAN, List.of(BasicType.STRING), List.of(BasicType.STRING)),
  CONTAINS(BasicType.BOOLEAN, List.of(BasicType.STRING), List.of(BasicType.STRING)),
  // === МНОЖЕСТВЕННЫЕ ОПЕРАТОРЫ ===
  // Правый операнд обрабатывается через inValues
  IN(BasicType.BOOLEAN, getAllEquatableTypes(), getAllEquatableTypes()),
  NOT_IN(BasicType.BOOLEAN, getAllEquatableTypes(), null),
  BETWEEN(BasicType.BOOLEAN, getComparableTypes(), null), // Обрабатывается через inValues
  // === NULL ОПЕРАТОРЫ ===
  IS_NULL(BasicType.BOOLEAN, getAllTypes(), null),
  IS_NOT_NULL(BasicType.BOOLEAN, getAllTypes(), null),
  // === GEOMETRY ОПЕРАТОРЫ ===
  DISTANCE_SPHERE(BasicType.DOUBLE, List.of(BasicType.POINT), List.of(BasicType.POINT));

  private final BasicType returnType;
  private final List<BasicType> allowedLeftTypes;
  private final List<BasicType> allowedRightTypes;

  OperatorType(
      BasicType returnType, List<BasicType> allowedLeftTypes, List<BasicType> allowedRightTypes) {
    this.returnType = returnType;
    this.allowedLeftTypes = allowedLeftTypes;
    this.allowedRightTypes = allowedRightTypes;
  }

  // === ВСПОМОГАТЕЛЬНЫЕ МЕТОДЫ ДЛЯ ГРУППИРОВКИ ТИПОВ ===

  private static List<BasicType> getAllTypes() {
    return Arrays.asList(BasicType.values());
  }

  private static List<BasicType> getAllEquatableTypes() {
    return Arrays.asList(
        BasicType.STRING,
        BasicType.BOOLEAN,
        BasicType.INTEGER,
        BasicType.DOUBLE,
        BasicType.OFFSET_DATE_TIME,
        BasicType.ENUM,
        BasicType.POINT);
  }

  private static List<BasicType> getAllComparableTypes() {
    return getAllTypes();
  }

  private static List<BasicType> getComparableTypes() {
    return Arrays.asList(BasicType.INTEGER, BasicType.DOUBLE, BasicType.OFFSET_DATE_TIME);
  }

  private static List<BasicType> getNumericTypes() {
    return Arrays.asList(BasicType.INTEGER, BasicType.DOUBLE);
  }

  // === МЕТОДЫ ПРОВЕРКИ СОВМЕСТИМОСТИ ===

  public boolean supportsLeftType(BasicType leftType) {
    return allowedLeftTypes != null && allowedLeftTypes.contains(leftType);
  }

  public boolean supportsRightType(BasicType rightType) {
    return allowedRightTypes != null && allowedRightTypes.contains(rightType);
  }

  public boolean supportsOperandTypes(BasicType leftType, BasicType rightType) {
    return supportsLeftType(leftType) && supportsRightType(rightType);
  }

  public boolean isUnary() {
    return allowedRightTypes.isEmpty();
  }

  public boolean isBinary() {
    return !allowedRightTypes.isEmpty();
  }

  public boolean requiresInValues() {
    return this == IN || this == NOT_IN || this == BETWEEN;
  }

  public boolean isLogical() {
    return this == AND || this == OR || this == NOT;
  }

  public boolean isComparison() {
    return Set.of(EQ, NE, GT, LT, GOE, LOE).contains(this);
  }

  public boolean isStringOperator() {
    return Set.of(LIKE, STARTS_WITH, ENDS_WITH, CONTAINS).contains(this);
  }

  public boolean isSpatialOperator() {
    return this == DISTANCE_SPHERE;
  }

  // === ВАЛИДАЦИЯ ===

  public void validateLeftType(BasicType leftType) {
    if (!supportsLeftType(leftType)) {
      throw new TypeValidationException(
          String.format(
              "Operator '%s' does not support left operand type '%s'. " + "Allowed types: %s",
              this, leftType, allowedLeftTypes));
    }
  }

  public void validateRightType(BasicType rightType) {
    if (!supportsRightType(rightType)) {
      throw new TypeValidationException(
          String.format(
              "Operator '%s' does not support right operand type '%s'. " + "Allowed types: %s",
              this, rightType, allowedRightTypes));
    }
  }

  public void validateOperandTypes(BasicType leftType, BasicType rightType) {
    validateLeftType(leftType);
    validateRightType(rightType);
  }

  public boolean supportsEntityAttributes() {
    return Set.of(IS_NULL, IS_NOT_NULL).contains(this);
  }

  // === ИСКЛЮЧЕНИЕ ДЛЯ ВАЛИДАЦИИ ТИПОВ ===
  public static class TypeValidationException extends RuntimeException {
    public TypeValidationException(String message) {
      super(message);
    }
  }
}
