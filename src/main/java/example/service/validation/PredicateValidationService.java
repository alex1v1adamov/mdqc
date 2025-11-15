package example.service.validation;

import example.models.meta.AttributeCategory;
import example.models.meta.AttributeType;
import example.models.meta.BasicType;
import example.models.meta.BasicTypeCategory;
import example.models.meta.MetaAttribute;
import example.models.meta.MetaEntity;
import example.models.meta.MetaEnumValue;
import example.models.predicate.EvaluationOperationNode;
import example.models.predicate.OperatorType;
import example.models.predicate.PathExpressionNode;
import example.models.predicate.PredicateDefinition;
import example.models.predicate.PredicateNode;
import example.models.predicate.PredicateNodeValue;
import example.models.predicate.PredicatePathExpression;
import example.models.predicate.ValueConstantNode;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PredicateValidationService implements Validate<PredicateDefinition> {

  public ValidationResult validate(PredicateDefinition predicateDefinition) {
    List<String> errors = new ArrayList<>();

    // Базовые проверки
    validateBasicStructure(predicateDefinition, errors);

    if (errors.isEmpty()) {
      validateRootNode(
          predicateDefinition.getRootNode(), predicateDefinition.getMetaEntity(), errors);
    }

    return new ValidationResult(errors.isEmpty(), errors);
  }

  private void validateBasicStructure(PredicateDefinition definition, List<String> errors) {
    if (definition == null) {
      errors.add("PredicateDefinition cannot be null");
      return;
    }

    if (definition.getMetaEntity() == null) {
      errors.add("MetaEntity cannot be null");
    }

    if (definition.getRootNode() == null) {
      errors.add("Root node cannot be null");
    }

    if (definition.getName() == null || definition.getName().trim().isEmpty()) {
      errors.add("Predicate name cannot be empty");
    }
  }

  private void validateRootNode(
      PredicateNode rootNode, MetaEntity metaEntity, List<String> errors) {
    if (rootNode == null) return;

    // Корневой узел должен возвращать Boolean
    if (rootNode.getNodeReturnType() != BasicType.BOOLEAN) {
      errors.add("Root node must return BOOLEAN type, got: " + rootNode.getNodeReturnType());
    }

    validateNode(rootNode, metaEntity, errors, new ValidationContext());
  }

  private void validateNode(
      PredicateNode node,
      MetaEntity currentEntity,
      List<String> errors,
      ValidationContext context) {
    if (node == null) return;

    context.visitNode(node);
    PredicateNode unproxiedNode = (PredicateNode) Hibernate.unproxy(node);
    try {
      switch (unproxiedNode) {
        case EvaluationOperationNode evalNode ->
            validateEvaluationNode(evalNode, currentEntity, errors, context);
        case PathExpressionNode pathNode ->
            validatePathExpressionNode(pathNode, currentEntity, errors, context);
        case ValueConstantNode valueNode ->
            validateValueConstantNode(valueNode, currentEntity, errors, context);
        default -> errors.add("Unsupported node type: " + node.getClass().getSimpleName());
      }
    } finally {
      context.leaveNode();
    }
  }

  private void validateEvaluationNode(
      EvaluationOperationNode node,
      MetaEntity currentEntity,
      List<String> errors,
      ValidationContext context) {
    OperatorType operator = node.getOperatorType();

    // Проверка левого операнда
    if (node.getLeftOperand() == null) {
      errors.add("Left operand is required for operator: " + operator);
      return;
    }

    BasicType leftType = node.getLeftOperand().getNodeReturnType();

    // Проверка категории левого операнда
    if (!operator.getAllowedLeftCategory().contains(leftType)) {
      errors.add(
          String.format(
              "Operator %s requires left operand of category %s, got: %s",
              operator, operator.getAllowedLeftCategory(), leftType));
    }

    // Проверка правого операнда
    if (operator.isRequiresRightOperand()) {
      if (node.getRightOperand() == null) {
        errors.add("Right operand is required for operator: " + operator);
      } else {
        BasicType rightType = node.getRightOperand().getNodeReturnType();

        // Проверка категории правого операнда
        if (!operator.getAllowedRightCategory().contains(rightType)) {
          errors.add(
              String.format(
                  "Operator %s requires right operand of category %s, got: %s",
                  operator, operator.getAllowedRightCategory(), rightType));
        }

        // Проверка совпадения типов
        if (operator.isTypesMustMatch() && !leftType.equals(rightType)) {
          errors.add(
              String.format(
                  "Operator %s requires matching types, got: %s and %s",
                  operator, leftType, rightType));
        }

        validateNode(node.getRightOperand(), currentEntity, errors, context);
      }
    } else if (node.getRightOperand() != null) {
      errors.add("Right operand is not allowed for operator: " + operator);
    }

    validateNode(node.getLeftOperand(), currentEntity, errors, context);

    // Специфичные проверки для операторов
    validateSpecificOperators(node, currentEntity, errors, context);
  }

  private void validatePathExpressionNode(
      PathExpressionNode node,
      MetaEntity currentEntity,
      List<String> errors,
      ValidationContext context) {
    PredicatePathExpression pathExpression = node.getPathExpression();

    if (pathExpression == null) {
      errors.add("PathExpression cannot be null");
      return;
    }

    if (pathExpression.getFinalPathAttribute() == null) {
      errors.add("FinalPathAttribute cannot be null");
      return;
    }

    // Валидация цепочки атрибутов
    MetaEntity currentPathEntity = currentEntity;

    // Проверка промежуточных атрибутов (навигация)
    for (MetaAttribute pathAttr : pathExpression.getPathAttributes()) {
      validatePathAttribute(pathAttr, currentPathEntity, errors, "path attribute");

      if (pathAttr.getAttributeCategory() != AttributeCategory.ENTITY) {
        errors.add(
            "Path navigation attributes must be ENTITY category, got: "
                + pathAttr.getAttributeCategory()
                + " for attribute: "
                + pathAttr.getName());
      } else {
        currentPathEntity = pathAttr.getAttributeEntityType();
      }
    }

    // Проверка конечного атрибута
    MetaAttribute finalAttr = pathExpression.getFinalPathAttribute();
    validatePathAttribute(finalAttr, currentPathEntity, errors, "final path attribute");

    // Конечный атрибут должен быть BASIC типа
    if (finalAttr.getAttributeCategory() != AttributeCategory.BASIC) {
      errors.add(
          "Final path attribute must be BASIC category, got: "
              + finalAttr.getAttributeCategory()
              + " for attribute: "
              + finalAttr.getName());
    }
  }

  private void validatePathAttribute(
      MetaAttribute attribute, MetaEntity expectedEntity, List<String> errors, String context) {
    if (!attribute.getEntity().getId().equals(expectedEntity.getId())) {
      errors.add(
          String.format(
              "Path %s '%s' does not belong to entity '%s'",
              context, attribute.getName(), expectedEntity.getName()));
    }
  }

  private void validateValueConstantNode(
      ValueConstantNode node,
      MetaEntity currentEntity,
      List<String> errors,
      ValidationContext context) {
    // Для IN операторов используем множественные значения
    if (node.getValue() != null) {
      // Для обычных операторов используем одиночное значение
      validateSingleValue(node, errors);
    } else {
      validateInValues(node, errors);
    }
  }

  private void validateSingleValue(ValueConstantNode node, List<String> errors) {
    if (node.getValue() == null) {
      errors.add("Value cannot be null for VALUE_CONSTANT node");
      return;
    }

    PredicateNodeValue value = node.getValue();

    // Проверка соответствия типа и значения
    validateValueTypeConsistency(value, errors);

    // Для ENUM проверяем наличие enumValue
    if (value.getValueType() == BasicType.ENUM && value.getEnumValue() == null) {
      errors.add("ENUM value requires enumValue to be set");
    }
  }

  private void validateInValues(ValueConstantNode node, List<String> errors) {
    if (node.getValues() == null || node.getValues().isEmpty()) {
      errors.add("operator requires at least one value");
      return;
    }

    // Все значения в IN должны быть одного типа
    BasicType firstType = null;
    for (PredicateNodeValue value : node.getValues()) {
      if (firstType == null) {
        firstType = value.getValueType();
      } else if (!firstType.equals(value.getValueType())) {
        errors.add("All values  must be of the same type");
        break;
      }

      validateValueTypeConsistency(value, errors);
    }
  }

  private void validateValueTypeConsistency(PredicateNodeValue value, List<String> errors) {
    // Проверяем, что заполнено только одно поле в соответствии с valueType
    switch (value.getValueType()) {
      case STRING -> validateOnlyOneFieldSet(value, "stringValue", errors);
      case BOOLEAN -> validateOnlyOneFieldSet(value, "booleanValue", errors);
      case INTEGER -> validateOnlyOneFieldSet(value, "integerValue", errors);
      case DOUBLE -> validateOnlyOneFieldSet(value, "doubleValue", errors);
      case OFFSET_DATE_TIME -> validateOnlyOneFieldSet(value, "offsetDateTimeValue", errors);
      case POINT -> validateOnlyOneFieldSet(value, "pointValue", errors);
      case ENUM -> validateOnlyOneFieldSet(value, "enumValue", errors);
    }
  }

  private void validateOnlyOneFieldSet(
      PredicateNodeValue value, String expectedField, List<String> errors) {
    int setFieldsCount = 0;

    // Проверяем все возможные поля значений
    if (value.getStringValue() != null) setFieldsCount++;
    if (value.getBooleanValue() != null) setFieldsCount++;
    if (value.getIntegerValue() != null) setFieldsCount++;
    if (value.getLongValue() != null) setFieldsCount++;
    if (value.getDoubleValue() != null) setFieldsCount++;
    if (value.getOffsetDateTimeValue() != null) setFieldsCount++;
    if (value.getTimestampValue() != null) setFieldsCount++;
    if (value.getPointValue() != null) setFieldsCount++;
    if (value.getEnumValue() != null) setFieldsCount++;

    if (setFieldsCount == 0) {
      errors.add("No value field is set for PredicateNodeValue with type: " + value.getValueType());
      return;
    }

    if (setFieldsCount > 1) {
      errors.add(
          "Multiple value fields set for PredicateNodeValue. Only one field should be set based on valueType: "
              + value.getValueType());
      return;
    }

    // Проверяем, что установлено правильное поле для типа
    boolean expectedFieldSet =
        switch (value.getValueType()) {
          case STRING -> value.getStringValue() != null;
          case BOOLEAN -> value.getBooleanValue() != null;
          case INTEGER -> value.getIntegerValue() != null || value.getLongValue() != null;
          case DOUBLE -> value.getDoubleValue() != null;
          case OFFSET_DATE_TIME -> value.getOffsetDateTimeValue() != null;
          case POINT -> value.getPointValue() != null;
          case ENUM -> value.getEnumValue() != null;
        };

    if (!expectedFieldSet) {
      errors.add(
          String.format(
              "Value type %s requires %s to be set, but it's null",
              value.getValueType(), expectedField));
    }

    // Дополнительные проверки для конкретных типов
    validateSpecificValueRules(value, errors);
  }

  private void validateSpecificValueRules(PredicateNodeValue value, List<String> errors) {
    switch (value.getValueType()) {
      case ENUM -> {
        if (value.getEnumValue() != null && value.getEnumValue().getMetaEnum() == null) {
          errors.add("Enum value must have metaEnum reference");
        }
      }
      case INTEGER -> {
        // Для INTEGER можно использовать integerValue или longValue, но не оба одновременно
        if (value.getIntegerValue() != null && value.getLongValue() != null) {
          errors.add("INTEGER type should use either integerValue or longValue, not both");
        }
      }
      case OFFSET_DATE_TIME -> {
        // Не должно быть timestampValue для OFFSET_DATE_TIME
        if (value.getTimestampValue() != null) {
          errors.add("OFFSET_DATE_TIME type should not have timestampValue set");
        }
      }
    }
  }

  // Дополнительные вспомогательные методы для полной валидации

  private void validateBidirectionalAttributes(MetaAttribute attribute, List<String> errors) {
    if (Boolean.TRUE.equals(attribute.getIsBidirectional())) {
      if (attribute.getRelatedAttribute() == null) {
        errors.add(
            "Bidirectional attribute '" + attribute.getName() + "' must have relatedAttribute");
      } else {
        // Проверяем симметричность
        MetaAttribute related = attribute.getRelatedAttribute();
        if (!Boolean.TRUE.equals(related.getIsBidirectional())) {
          errors.add("Related attribute '" + related.getName() + "' must also be bidirectional");
        }
        if (!attribute.equals(related.getRelatedAttribute())) {
          errors.add(
              "Bidirectional relationship is not symmetric between '"
                  + attribute.getName()
                  + "' and '"
                  + related.getName()
                  + "'");
        }
      }
    }
  }

  private void validateMetaEnumValue(PredicateNodeValue value, List<String> errors) {
    if (value.getValueType() == BasicType.ENUM && value.getEnumValue() != null) {
      MetaEnumValue enumValue = value.getEnumValue();

      if (enumValue.getName() == null || enumValue.getName().trim().isEmpty()) {
        errors.add("Enum value name cannot be empty");
      }

      if (enumValue.getStorageValue() == null || enumValue.getStorageValue().trim().isEmpty()) {
        errors.add("Enum storage value cannot be empty");
      }

      if (enumValue.getMetaEnum() == null) {
        errors.add("Enum value must reference MetaEnum");
      }
    }
  }

  private void validateSpatialOperations(EvaluationOperationNode node, List<String> errors) {
    if (node.getOperatorType() == OperatorType.DISTANCE_SPHERE) {
      // DISTANCE_SPHERE должен использоваться в сравнениях, а не как корневой узел
      if (node.getNodeReturnType() != BasicType.DOUBLE) {
        errors.add("DISTANCE_SPHERE must return DOUBLE type");
      }

      // Проверяем, что DISTANCE_SPHERE используется в правильном контексте
      // (обычно внутри оператора сравнения: DISTANCE_SPHERE < значение)
    }
  }

  private void validateOperatorCompatibility(
      OperatorType operator, BasicType leftType, BasicType rightType, List<String> errors) {
    // Дополнительные проверки совместимости типов для специфичных операторов
    switch (operator) {
      case LIKE, STARTS_WITH, ENDS_WITH, CONTAINS -> {
        if (leftType != BasicType.STRING || rightType != BasicType.STRING) {
          errors.add("String operators require STRING types on both sides");
        }
      }
      case GT, LT, GOE, LOE -> {
        if (!BasicTypeCategory.ORDERED.contains(leftType)
            || !BasicTypeCategory.ORDERED.contains(rightType)) {
          errors.add("Ordering operators require ORDERED types (NUMERIC, TEMPORAL)");
        }
      }
    }
  }

  // Метод для проверки целостности дерева предикатов
  private void validateTreeStructure(
      PredicateNode node, ValidationContext context, List<String> errors) {
    if (node == null) return;

    // Проверка на циклические ссылки
    if (context.getVisitedNodes().contains(node.getId())) {
      errors.add("Cyclic reference detected in predicate tree at node: " + node.getId());
      return;
    }

    context.getVisitedNodes().add(node.getId());

    // Рекурсивная проверка дочерних узлов
    if (node instanceof EvaluationOperationNode evalNode) {
      validateTreeStructure(evalNode.getLeftOperand(), context, errors);
      validateTreeStructure(evalNode.getRightOperand(), context, errors);
    }

    context.getVisitedNodes().remove(node.getId());
  }

  private void validateSpecificOperators(
      EvaluationOperationNode node,
      MetaEntity currentEntity,
      List<String> errors,
      ValidationContext context) {
    OperatorType operator = node.getOperatorType();

    switch (operator) {
      case IN, NOT_IN -> {
        // Правый операнд должен быть ValueConstantNode с множественными значениями
        if (!(node.getRightOperand() instanceof ValueConstantNode)) {
          errors.add(operator + " operator requires VALUE_CONSTANT as right operand");
        } else {
          context.setInInOperator(true);
          validateNode(node.getRightOperand(), currentEntity, errors, context);
          context.setInInOperator(false);
        }
      }

      case DISTANCE_SPHERE -> {
        // Левый операнд должен быть геометрией (POINT)
        BasicType leftType = node.getLeftOperand().getNodeReturnType();
        if (leftType != BasicType.POINT) {
          errors.add("DISTANCE_SPHERE requires POINT as left operand, got: " + leftType);
        }

        // Правый операнд должен быть POINT
        BasicType rightType = node.getRightOperand().getNodeReturnType();
        if (rightType != BasicType.POINT) {
          errors.add("DISTANCE_SPHERE requires POINT as right operand, got: " + rightType);
        }
      }

      case IS_NULL, IS_NOT_NULL -> {
        // Не должны иметь правый операнд
        if (node.getRightOperand() != null) {
          errors.add(operator + " should not have right operand");
        }
      }
    }
  }

  private void validateComplexCases(
      PredicateNode node,
      MetaEntity currentEntity,
      List<String> errors,
      ValidationContext context) {
    // Проверка циклических ссылок
    if (context.isNodeVisited(node)) {
      errors.add("Cyclic reference detected in predicate tree");
      return;
    }

    // Проверка глубины дерева (защита от StackOverflow)
    if (context.getDepth() > 100) {
      errors.add("Predicate tree too deep (max 100 levels allowed)");
      return;
    }

    // Проверка навигации через коллекции
    validateCollectionNavigation(node, currentEntity, errors, context);
  }

  private void validateCollectionNavigation(
      PredicateNode node,
      MetaEntity currentEntity,
      List<String> errors,
      ValidationContext context) {
    if (node instanceof PathExpressionNode pathNode) {
      PredicatePathExpression pathExpr = pathNode.getPathExpression();

      for (MetaAttribute attr : pathExpr.getPathAttributes()) {
        if (attr.getType() == AttributeType.PLURAL) {
          errors.add("Navigation through PLURAL attributes is not supported: " + attr.getName());
        }
      }
    }
  }

  // Контекст валидации для отслеживания состояния
  @Data
  static class ValidationContext {
    private final Set<UUID> visitedNodes = new HashSet<>();
    private final List<PredicateNode> nodeStack = new ArrayList<>();
    private boolean inInOperator = false;

    public void visitNode(PredicateNode node) {
      visitedNodes.add(node.getId());
      nodeStack.add(node);
    }

    public void leaveNode() {
      if (!nodeStack.isEmpty()) {
        nodeStack.remove(nodeStack.size() - 1);
      }
    }

    public boolean isNodeVisited(PredicateNode node) {
      return visitedNodes.contains(node.getId());
    }

    public int getDepth() {
      return nodeStack.size();
    }
  }
}
