package example.models.predicate;

public enum OperatorType {
  // Логические операторы
  AND,
  OR,
  NOT,

  // Операторы сравнения
  EQ,
  NE,
  GT,
  LT,
  GOE,
  LOE,

  // Строковые оператор
  LIKE,
  STARTS_WITH,
  ENDS_WITH,
  CONTAINS,

  // Множественные операторы
  IN,
  NOT_IN,
  BETWEEN,

  // Null операторы
  IS_NULL,
  IS_NOT_NULL,

  // GEOMETRY операторы
  DISTANCE_SPHERE
}
