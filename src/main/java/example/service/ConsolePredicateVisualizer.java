package example.service;

import example.models.meta.AttributeCategory;
import example.models.meta.MetaAttribute;
import example.models.meta.MetaEnumValue;
import example.models.predicate.*;
import example.repo.PredicateDefinitionRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class ConsolePredicateVisualizer {

  private static final String INDENT = "    ";
  private static final String CONNECTOR = "│   ";
  private static final String BRANCH = "├── ";
  private static final String LAST_BRANCH = "└── ";
  private static final String EMPTY_INDENT = "    ";
  private final PredicateDefinitionRepository predicateDefinitionRepository;

  @Transactional(readOnly = true)
  public String visualize(UUID id) {
    PredicateDefinition predicateDefinition = predicateDefinitionRepository.findById(id).get();

    StringBuilder sb = new StringBuilder();
    sb.append("PredicateDefinition: ")
        .append(predicateDefinition.getName())
        .append(". ID: ")
        .append(predicateDefinition.getId())
        .append("\n");
    sb.append("Target Entity: ")
        .append(predicateDefinition.getMetaEntity().getName())
        .append("\n\n");

    if (predicateDefinition.getRootNode() != null) {
      visualizeNode(sb, predicateDefinition.getRootNode(), "", true);
    } else {
      sb.append("(empty predicate)");
    }

    return sb.toString();
  }

  private void visualizeNode(StringBuilder sb, PredicateNode node, String prefix, boolean isLast) {
    String currentPrefix = prefix + (isLast ? LAST_BRANCH : BRANCH);
    String childPrefix = prefix + (isLast ? EMPTY_INDENT : CONNECTOR);

    // Основная информация об узле
    sb.append(currentPrefix).append(node.getNodeType());

    if (node.getOperatorType() != null) {
      sb.append(" (").append(node.getOperatorType()).append(")");
    }
    sb.append("\n");

    // Дополнительная информация в зависимости от типа узла
    switch (node.getNodeType()) {
      case PATH_EXPRESSION ->
          visualizePathExpression(sb, node.getPathExpression(), childPrefix, true);
      case VALUE_CONSTANT -> visualizeValueConstant(sb, node.getValue(), childPrefix, true);
      case EVALUATION_OPERATION -> visualizeComparisonOperator(sb, node, childPrefix);
    }
  }

  private void visualizePathExpression(
      StringBuilder sb, PredicatePathExpression pathExpr, String prefix, boolean isLast) {
    String currentPrefix = prefix + (isLast ? LAST_BRANCH : BRANCH);
    String childPrefix = prefix + (isLast ? EMPTY_INDENT : CONNECTOR);

    sb.append(currentPrefix)
        .append("PATH_EXPRESSION: ")
        .append(getPathString(pathExpr))
        .append("\n");

    // Корневой атрибут
    sb.append(childPrefix)
        .append(BRANCH)
        .append("rootAttribute: ")
        .append(visualizeMetaAttribute(pathExpr.getRootAttribute()))
        .append("\n");

    // Атрибуты пути
    List<MetaAttribute> pathAttributes = pathExpr.getPathAttributes();
    if (!pathAttributes.isEmpty()) {
      sb.append(childPrefix).append(BRANCH).append("pathAttributes:\n");
      for (int i = 0; i < pathAttributes.size(); i++) {
        boolean lastAttr = i == pathAttributes.size() - 1;
        String attrPrefix = childPrefix + CONNECTOR;
        sb.append(attrPrefix)
            .append(lastAttr ? LAST_BRANCH : BRANCH)
            .append(visualizeMetaAttribute(pathAttributes.get(i)))
            .append("\n");
      }
    } else {
      sb.append(childPrefix).append(LAST_BRANCH).append("pathAttributes: []\n");
    }
  }

  private void visualizeValueConstant(
      StringBuilder sb, PredicateNodeValue value, String prefix, boolean isLast) {
    String currentPrefix = prefix + (isLast ? LAST_BRANCH : BRANCH);

    sb.append(currentPrefix)
        .append("VALUE_CONSTANT: ")
        .append(visualizeNodeValue(value))
        .append("\n");
  }

  private void visualizeComparisonOperator(StringBuilder sb, PredicateNode node, String prefix) {
    // Левый операнд
    if (node.getLeftOperand() != null) {
      visualizeNode(sb, node.getLeftOperand(), prefix, false);
    }

    // Правый операнд (для бинарных операторов)
    if (node.getRightOperand() != null) {
      visualizeNode(sb, node.getRightOperand(), prefix, false);
    }

    // Множественные значения (IN, BETWEEN)
    if (node.getValues() != null && !node.getValues().isEmpty()) {
      String valuesPrefix = prefix + CONNECTOR;
      sb.append(valuesPrefix).append(BRANCH).append("inValues: ");

      sb.append("[IN - ").append(node.getValues().size()).append(" values]\n");

      for (int i = 0; i < node.getValues().size(); i++) {
        boolean lastValue = i == node.getValues().size() - 1;
        String valuePrefix = valuesPrefix + CONNECTOR;
        sb.append(valuePrefix)
            .append(lastValue ? LAST_BRANCH : BRANCH)
            .append("[")
            .append(i)
            .append("]: ")
            .append(visualizeNodeValue(node.getValues().get(i)))
            .append("\n");
      }
    }

    // Значение (для унарных операторов)
    if (node.getValue() != null) {
      visualizeValueConstant(sb, node.getValue(), prefix, true);
    }
  }

  private void visualizeLogicalOperator(StringBuilder sb, PredicateNode node, String prefix) {
    // Левый операнд
    if (node.getLeftOperand() != null) {
      visualizeNode(sb, node.getLeftOperand(), prefix, false);
    }

    // Правый операнд (для AND/OR)
    if (node.getRightOperand() != null) {
      visualizeNode(sb, node.getRightOperand(), prefix, true);
    }

    // Для NOT только левый операнд
    if (node.getOperatorType() == OperatorType.NOT && node.getLeftOperand() != null) {
      visualizeNode(sb, node.getLeftOperand(), prefix, true);
    }
  }

  private String getPathString(PredicatePathExpression pathExpr) {
    if (pathExpr == null) return "null";

    StringBuilder path = new StringBuilder(pathExpr.getRootAttribute().getName());

    for (MetaAttribute attr : pathExpr.getPathAttributes()) {
      path.append(".").append(attr.getName());
    }

    return path.toString();
  }

  private String visualizeMetaAttribute(MetaAttribute attribute) {
    if (attribute == null) return "null";

    StringBuilder sb = new StringBuilder();
    sb.append(attribute.getName()).append(" (").append(attribute.getAttributeCategory());

    if (attribute.getAttributeCategory() == AttributeCategory.BASIC) {
      sb.append(":").append(attribute.getBasicType());
    } else {
      sb.append("->").append(attribute.getAttributeEntityType().getName());
    }

    sb.append(")");
    return sb.toString();
  }

  private String visualizeNodeValue(PredicateNodeValue value) {
    if (value == null) return "null";

    StringBuilder sb = new StringBuilder();
    sb.append(value.getValueType()).append(": ");

    Object actualValue = value.getValue();
    if (actualValue instanceof MetaEnumValue enumValue) {
      sb.append(enumValue.getName()).append(" (").append(enumValue.getStorageValue()).append(")");
    } else {
      sb.append(actualValue);
    }

    return sb.toString();
  }
}
