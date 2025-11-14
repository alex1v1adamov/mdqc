package example.models.predicate;

public enum NodeType {

  /* Операция вычисления.
   */
  EVALUATION_OPERATION,
  /* Константное значение
   * Содержит конкретное значение для сравнений (строка, число, дата и т.д.)
   * Использует PredicateNodeValue для типобезопасного хранения
   */
  VALUE_CONSTANT,
  /*
   * тип для навигации по связям
   * Выражение пути через связи между сущностями
   * Например: clinic.isOpen
   */
  PATH_EXPRESSION
}
