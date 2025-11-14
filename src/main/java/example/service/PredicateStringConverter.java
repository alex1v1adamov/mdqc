package example.service;

import example.models.meta.BasicType;
import example.models.meta.MetaAttribute;
import example.models.predicate.PredicateDefinition;
import example.models.predicate.PredicateNode;
import example.models.predicate.PredicateNodeValue;
import example.models.predicate.PredicatePathExpression;
import example.repo.MetaAttributeRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PredicateStringConverter {

  private final MetaAttributeRepository metaAttributeRepository;

  /** Конвертирует PredicateDefinition в человеко-читаемое строковое представление */
  public String convertToString(PredicateDefinition predicateDefinition) {
    if (predicateDefinition == null || predicateDefinition.getRootNode() == null) {
      return "EMPTY";
    }

    try {
      return convertNodeToString(predicateDefinition.getRootNode());
    } catch (Exception e) {
      log.error("Error converting predicate to string", e);
      return "CONVERSION_ERROR";
    }
  }

  /** Рекурсивно конвертирует узел в строку */
  private String convertNodeToString(PredicateNode node) {
    if (node == null) {
      return "";
    }

    try {
      switch (node.getNodeType()) {
          case EVALUATION_OPERATION:
          return convertLogicalOperatorToString(node);
        case VALUE_CONSTANT:
          return convertValueConstantToString(node);

        case PATH_EXPRESSION:
          return convertPathExpressionToString(node);

        default:
          return "UNKNOWN_NODE_TYPE: " + node.getNodeType();
      }
    } catch (Exception e) {
      log.warn("Error converting node: {}", node.getId(), e);
      return "ERROR_NODE_" + node.getId();
    }
  }

  /** Конвертирует логический оператор */
  private String convertLogicalOperatorToString(PredicateNode node) {
    if (node.getOperatorType() == null) {
      return "UNKNOWN_LOGICAL_OPERATOR";
    }

    switch (node.getOperatorType()) {
      case AND:
        String leftAnd = convertNodeToString(node.getLeftOperand());
        String rightAnd = convertNodeToString(node.getRightOperand());
        return String.format("(%s && %s)", leftAnd, rightAnd);

      case OR:
        String leftOr = convertNodeToString(node.getLeftOperand());
        String rightOr = convertNodeToString(node.getRightOperand());
        return String.format("(%s || %s)", leftOr, rightOr);

      case NOT:
        String operand = convertNodeToString(node.getLeftOperand());
        return String.format("!%s", operand);

      default:
        return "UNSUPPORTED_LOGICAL_OPERATOR: " + node.getOperatorType();
    }
  }

  /** Конвертирует оператор сравнения */
  private String convertComparisonOperatorToString(PredicateNode node) {
    if (node.getOperatorType() == null) {
      return "UNKNOWN_COMPARISON_OPERATOR";
    }

    String left = convertNodeToString(node.getLeftOperand());

    switch (node.getOperatorType()) {
      case EQ:
        String rightEq = convertNodeToString(node.getRightOperand());
        return String.format("%s == %s", left, rightEq);

      case NE:
        String rightNe = convertNodeToString(node.getRightOperand());
        return String.format("%s != %s", left, rightNe);

      case GT:
        String rightGt = convertNodeToString(node.getRightOperand());
        return String.format("%s > %s", left, rightGt);

      case LT:
        String rightLt = convertNodeToString(node.getRightOperand());
        return String.format("%s < %s", left, rightLt);

      case GOE:
        String rightGoe = convertNodeToString(node.getRightOperand());
        return String.format("%s >= %s", left, rightGoe);

      case LOE:
        String rightLoe = convertNodeToString(node.getRightOperand());
        return String.format("%s <= %s", left, rightLoe);

      case LIKE:
        String rightLike = convertNodeToString(node.getRightOperand());
        return String.format("%s LIKE %s", left, rightLike);

      case CONTAINS:
        String rightContains = convertNodeToString(node.getRightOperand());
        return String.format("%s CONTAINS %s", left, rightContains);

      case STARTS_WITH:
        String rightStartsWith = convertNodeToString(node.getRightOperand());
        return String.format("%s STARTS_WITH %s", left, rightStartsWith);

      case ENDS_WITH:
        String rightEndsWith = convertNodeToString(node.getRightOperand());
        return String.format("%s ENDS_WITH %s", left, rightEndsWith);

      case IN:
        return convertInOperatorToString(node);

      case NOT_IN:
        return convertNotInOperatorToString(node);

      case IS_NULL:
        return String.format("%s IS NULL", left);

      case IS_NOT_NULL:
        return String.format("%s IS NOT NULL", left);

      default:
        return "UNSUPPORTED_COMPARISON_OPERATOR: " + node.getOperatorType();
    }
  }

  /** Конвертирует оператор IN */
  private String convertInOperatorToString(PredicateNode node) {
    String left = convertNodeToString(node.getLeftOperand());
    List<PredicateNodeValue> values = node.getValues();

    if (values == null || values.isEmpty()) {
      return String.format("%s IN ()", left);
    }

    String valuesString =
        values.stream()
            .map(this::convertNodeValueToString)
            .reduce((a, b) -> a + ", " + b)
            .orElse("");

    return String.format("%s IN (%s)", left, valuesString);
  }

  /** Конвертирует оператор NOT IN */
  private String convertNotInOperatorToString(PredicateNode node) {
    String left = convertNodeToString(node.getLeftOperand());
    List<PredicateNodeValue> values = node.getValues();

    if (values == null || values.isEmpty()) {
      return String.format("%s NOT IN ()", left);
    }

    String valuesString =
        values.stream()
            .map(this::convertNodeValueToString)
            .reduce((a, b) -> a + ", " + b)
            .orElse("");

    return String.format("%s NOT IN (%s)", left, valuesString);
  }

  /** Конвертирует оператор BETWEEN */
  private String convertBetweenOperatorToString(PredicateNode node) {
    String left = convertNodeToString(node.getLeftOperand());
    String from = convertNodeToString(node.getLeftOperand());
    String to = convertNodeToString(node.getRightOperand());

    return String.format("%s BETWEEN %s AND %s", left, from, to);
  }



  /** Конвертирует константное значение */
  private String convertValueConstantToString(PredicateNode node) {
    if (node.getValue() == null) {
      return "NULL";
    }

    return convertNodeValueToString(node.getValue());
  }

  /** Конвертирует PredicateNodeValue в строку */
  private String convertNodeValueToString(PredicateNodeValue value) {
    if (value == null) {
      return "NULL";
    }

    Object rawValue = value.getValue();
    if (rawValue == null) {
      return "NULL";
    }

    // Для строковых значений добавляем кавычки
    if (value.getValueType() == BasicType.STRING) {
      return String.format("\"%s\"", escapeString(rawValue.toString()));
    }

    // Для boolean используем true/false без кавычек
    if (value.getValueType() == BasicType.BOOLEAN) {
      return Boolean.TRUE.equals(rawValue) ? "true" : "false";
    }

    // Для enum тоже без кавычек
    if (value.getValueType() == BasicType.ENUM) {
      return rawValue.toString();
    }

    // Для чисел и дат - как есть
    return rawValue.toString();
  }

  /** Конвертирует выражение пути */
  private String convertPathExpressionToString(PredicateNode node) {
    PredicatePathExpression pathExpression = node.getPathExpression();
    if (pathExpression == null) {
      return "UNKNOWN_PATH";
    }

    StringBuilder path = new StringBuilder();

    // Добавляем корневой атрибут (через репозиторий)
    if (pathExpression.getRootAttribute() != null) {
      String rootAttrName =
          metaAttributeRepository
              .findById(pathExpression.getRootAttribute().getId())
              .map(MetaAttribute::getName)
              .orElse("MISSING_ROOT_ATTR");
      path.append(rootAttrName);
    } else {
      path.append("UNKNOWN_ROOT");
    }

    // Добавляем атрибуты пути (через репозиторий)
    if (pathExpression.getPathAttributes() != null
        && !pathExpression.getPathAttributes().isEmpty()) {
      for (MetaAttribute attribute : pathExpression.getPathAttributes()) {
        String attrName =
            metaAttributeRepository
                .findById(attribute.getId())
                .map(MetaAttribute::getName)
                .orElse("MISSING_ATTR");
        path.append(".").append(attrName);
      }
    }

    return path.toString();
  }

  /** Экранирует специальные символы в строках */
  private String escapeString(String value) {
    if (value == null) return "";
    return value
        .replace("\"", "\\\"")
        .replace("\n", "\\n")
        .replace("\t", "\\t")
        .replace("\r", "\\r");
  }

  /** Дополнительный метод для компактного представления (без лишних скобок) */
  public String convertToCompactString(PredicateDefinition predicateDefinition) {
    String fullString = convertToString(predicateDefinition);

    // Убираем внешние скобки если они есть и выражение сложное
    if (fullString.startsWith("(") && fullString.endsWith(")") && fullString.length() > 2) {
      String withoutBrackets = fullString.substring(1, fullString.length() - 1);
      // Проверяем, что внутри есть логические операторы
      if (withoutBrackets.contains("&&") || withoutBrackets.contains("||")) {
        return withoutBrackets;
      }
    }

    return fullString;
  }

  /** Метод для отладочного вывода с информацией о типах */
  public String convertToDebugString(PredicateDefinition predicateDefinition) {
    if (predicateDefinition == null || predicateDefinition.getRootNode() == null) {
      return "EMPTY_PREDICATE";
    }

    return String.format(
        "Predicate[%s]: %s", predicateDefinition.getName(), convertToString(predicateDefinition));
  }
}
