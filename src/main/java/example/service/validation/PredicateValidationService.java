package example.service.validation;

import example.models.meta.AttributeCategory;
import example.models.meta.BasicType;
import example.models.meta.BasicTypeCategory;
import example.models.meta.MetaAttribute;
import example.models.meta.MetaEnum;
import example.models.meta.MetaEnumValue;
import example.models.predicate.NodeType;
import example.models.predicate.OperatorType;
import example.models.predicate.PredicateDefinition;
import example.models.predicate.PredicateNode;
import example.models.predicate.PredicateNodeValue;
import example.models.predicate.PredicatePathExpression;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.Getter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
//TODO FULLY REMAKE
public class PredicateValidationService implements Validate<PredicateDefinition> {

    public ValidationResult validate(PredicateDefinition predicateDefinition) {
        return ValidationResult.success();
    }
//
//  /** Валидация PredicateDefinition */
//  public ValidationResult validate(PredicateDefinition predicateDefinition) {
//    List<String> errors = new ArrayList<>();
//
//    if (predicateDefinition == null) {
//      return ValidationResult.error("PredicateDefinition cannot be null");
//    }
//
//    if (predicateDefinition.getName() == null || predicateDefinition.getName().trim().isEmpty()) {
//      errors.add("Predicate name cannot be null or empty");
//    }
//
//    if (predicateDefinition.getMetaEntity() == null) {
//      errors.add("Predicate must reference a MetaEntity");
//    }
//
//    if (predicateDefinition.getRootNode() == null) {
//      errors.add("Predicate must have a root node");
//    } else {
//      ValidationResult nodeResult = validatePredicateNode(predicateDefinition.getRootNode());
//      if (!nodeResult.isValid()) {
//        errors.add("Root node: " + String.join(", ", nodeResult.getErrors()));
//      }
//    }
//
//    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
//  }
//
//  /** Валидация PredicateNode с учетом новых правил */
//  public ValidationResult validatePredicateNode(PredicateNode node) {
//    return validatePredicateNode(node, new ValidationContext());
//  }
//
//  private ValidationResult validatePredicateNode(PredicateNode node, ValidationContext context) {
//    List<String> errors = new ArrayList<>();
//
//    if (node == null) {
//      return ValidationResult.error("PredicateNode cannot be null");
//    }
//
//    if (node.getNodeType() == null) {
//      errors.add("Node type cannot be null");
//      return ValidationResult.error(errors);
//    }
//
//    // Проверка на циклические ссылки
//    if (context.getVisitedNodes().contains(node.getId())) {
//      errors.add("Cyclic reference detected in predicate nodes");
//      return ValidationResult.error(errors);
//    }
//    context.getVisitedNodes().add(node.getId());
//
//    // Валидация в зависимости от типа узла
//    switch (node.getNodeType()) {
//      case VALUE_CONSTANT -> errors.addAll(validateValueConstantNode(node));
//      case PATH_EXPRESSION -> errors.addAll(validatePathExpressionNode(node, context));
//      case EVALUATION_OPERATION -> errors.addAll(validateEvaluationOperationNode(node, context));
//    }
//
//    // Валидация взаимно исключающих полей
//    errors.addAll(validateMutuallyExclusiveFields(node));
//
//    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
//  }
//
//  /** Валидация EVALUATION_OPERATION (объединяет логические и операторы сравнения) */
//  private List<String> validateEvaluationOperationNode(
//      PredicateNode node, ValidationContext context) {
//    List<String> errors = new ArrayList<>();
//
//    // operatorType обязателен для EVALUATION_OPERATION
//    if (node.getOperatorType() == null) {
//      errors.add("EVALUATION_OPERATION must have operatorType defined");
//      return errors;
//    }
//    // pathExpression и leftOperand взаимоисключающие для EVALUATION_OPERATION
//    if (node.getPathExpression() != null && node.getLeftOperand() != null) {
//      errors.add("pathExpression and leftOperand cannot both be set for EVALUATION_OPERATION");
//    }
//    // Проверка наличия path expression или left operand
//    boolean hasPathExpression = node.getPathExpression() != null;
//    boolean hasLeftOperand = node.getLeftOperand() != null;
//
//    // Определяем тип оператора для разных сценариев валидации
//    if (isLogicalOperator(node.getOperatorType())) {
//      errors.addAll(validateLogicalOperator(node, context, hasPathExpression));
//    } else {
//      errors.addAll(validateComparisonOperator(node, context, hasPathExpression, hasLeftOperand));
//    }
//
//    return errors;
//  }
//
//  /** Проверяет является ли оператор логическим */
//  private boolean isLogicalOperator(OperatorType operatorType) {
//    return operatorType == OperatorType.AND
//        || operatorType == OperatorType.OR
//        || operatorType == OperatorType.NOT;
//  }
//
//  /** Валидация логических операторов (AND, OR, NOT) */
//  private List<String> validateLogicalOperator(
//      PredicateNode node, ValidationContext context, boolean hasPathExpression) {
//    List<String> errors = new ArrayList<>();
//
//    // Запрещенные поля для логических операторов
//    if (hasPathExpression) {
//      errors.add("Logical operator cannot have pathExpression");
//    }
//
//    if (node.getValue() != null) {
//      errors.add("Logical operator cannot have value");
//    }
//
//    if (!node.getValues().isEmpty()) {
//      errors.add("Logical operator cannot have inValues");
//    }
//
//    // Проверка операндов в зависимости от оператора
//    switch (node.getOperatorType()) {
//      case AND:
//      case OR:
//        if (node.getLeftOperand() == null || node.getRightOperand() == null) {
//          errors.add(node.getOperatorType() + " operator must have both left and right operands");
//        }
//        break;
//      case NOT:
//        if (node.getLeftOperand() == null) {
//          errors.add("NOT operator must have left operand");
//        }
//        if (node.getRightOperand() != null) {
//          errors.add("NOT operator cannot have right operand");
//        }
//        break;
//    }
//
//    // Рекурсивная валидация операндов и проверка типов
//    if (node.getLeftOperand() != null) {
//      ValidationResult leftResult = validatePredicateNode(node.getLeftOperand(), context);
//      if (!leftResult.isValid()) {
//        errors.add("Left operand: " + String.join(", ", leftResult.getErrors()));
//      } else {
//        // Для логических операторов операнды должны возвращать BOOLEAN
//        BasicType leftType = node.getLeftOperand().getNodeReturnType();
//        if (leftType != BasicType.BOOLEAN) {
//          errors.add("Left operand of logical operator must return BOOLEAN, got: " + leftType);
//        }
//      }
//    }
//
//    if (node.getRightOperand() != null) {
//      ValidationResult rightResult = validatePredicateNode(node.getRightOperand(), context);
//      if (!rightResult.isValid()) {
//        errors.add("Right operand: " + String.join(", ", rightResult.getErrors()));
//      } else {
//        BasicType rightType = node.getRightOperand().getNodeReturnType();
//        if (rightType != BasicType.BOOLEAN) {
//          errors.add("Right operand of logical operator must return BOOLEAN, got: " + rightType);
//        }
//      }
//    }
//
//    return errors;
//  }
//
//  /** Валидация операторов сравнения */
//  private List<String> validateComparisonOperator(
//      PredicateNode node,
//      ValidationContext context,
//      boolean hasPathExpression,
//      boolean hasLeftOperand) {
//    List<String> errors = new ArrayList<>();
//
//    // Проверка структуры для операторов сравнения
//    if (!hasPathExpression && !hasLeftOperand) {
//      errors.add("Comparison operator must have either pathExpression or leftOperand");
//    }
//
//    if (hasPathExpression && hasLeftOperand) {
//      errors.add("Comparison operator can have only one of: pathExpression or leftOperand");
//    }
//
//    // Получаем целевой атрибут для проверки совместимости типов
//    MetaAttribute targetAttribute = getTargetAttribute(node);
//    BasicTypeCategory expectedCategory = getExpectedCategoryForOperator(node.getOperatorType());
//
//    if (targetAttribute != null) {
//      errors.addAll(
//          validateOperatorAttributeCompatibility(
//              node.getOperatorType(), targetAttribute, expectedCategory));
//    }
//
//    // Валидация в зависимости от типа оператора
//    if (node.getOperatorType() == OperatorType.IS_NULL
//        || node.getOperatorType() == OperatorType.IS_NOT_NULL) {
//      errors.addAll(validateNullOperator(node));
//    } else if (node.getOperatorType() == OperatorType.IN
//        || node.getOperatorType() == OperatorType.NOT_IN) {
//      errors.addAll(validateValuesOperator(node, targetAttribute, expectedCategory));
//    } else {
//      errors.addAll(
//          validateBinaryComparisonOperator(node, context, targetAttribute, expectedCategory));
//    }
//
//    return errors;
//  }
//
//  /** Получает категорию типа, ожидаемую оператором */
//  private BasicTypeCategory getExpectedCategoryForOperator(OperatorType operatorType) {
//    return switch (operatorType) {
//      case EQ, NE, IN, NOT_IN -> BasicTypeCategory.ALL;
//      case GT, GOE, LT, LOE -> BasicTypeCategory.COMPARABLE;
//      case IS_NULL, IS_NOT_NULL -> BasicTypeCategory.ALL;
//      default -> BasicTypeCategory.ALL;
//    };
//  }
//
//  /** Валидация операторов IN/NOT_IN */
//  private List<String> validateValuesOperator(
//      PredicateNode node, MetaAttribute targetAttribute, BasicTypeCategory expectedCategory) {
//    List<String> errors = new ArrayList<>();
//
//    //TODO проверка на заполненность rightOperand.values
//
//    // Проверка типов в inValues
//    BasicType firstType = null;
//    for (PredicateNodeValue inValue : node.getValues()) {
//      ValidationResult valueResult = validatePredicateNodeValue(inValue);
//      if (!valueResult.isValid()) {
//        errors.add("inValues: " + String.join(", ", valueResult.getErrors()));
//      }
//
//      if (firstType == null) {
//        firstType = inValue.getValueType();
//      } else if (firstType != inValue.getValueType()) {
//        errors.add("All values in inValues must be of the same type");
//        break;
//      }
//    }
//
//    if (node.getValue() != null) {
//      errors.add(node.getOperatorType() + " operator cannot have value, use inValues instead");
//    }
//
//
//
//    return errors;
//  }
//
//  /** Валидация NULL операторов */
//  private List<String> validateNullOperator(PredicateNode node) {
//    List<String> errors = new ArrayList<>();
//
//    if (node.getRightOperand() != null) {
//      errors.add("IS_NULL/IS_NOT_NULL cannot have rightOperand");
//    }
//
//    if (node.getValue() != null) {
//      errors.add("IS_NULL/IS_NOT_NULL cannot have value");
//    }
//
//    if (!node.getValues().isEmpty()) {
//      errors.add("IS_NULL/IS_NOT_NULL cannot have inValues");
//    }
//
//    return errors;
//  }
//
//  /** Валидация бинарных операторов сравнения */
//  private List<String> validateBinaryComparisonOperator(
//      PredicateNode node,
//      ValidationContext context,
//      MetaAttribute targetAttribute,
//      BasicTypeCategory expectedCategory) {
//    List<String> errors = new ArrayList<>();
//
//    if (node.getRightOperand() == null) {
//      errors.add(
//          "Comparison operator must have rightOperand for operator: " + node.getOperatorType());
//    } else {
//      ValidationResult rightResult = validatePredicateNode(node.getRightOperand(), context);
//      if (!rightResult.isValid()) {
//        errors.add("Right operand: " + String.join(", ", rightResult.getErrors()));
//      }
//    }
//
//    if (!node.getValues().isEmpty()) {
//      errors.add(node.getOperatorType() + " operator cannot have inValues");
//    }
//
//    return errors;
//  }
//
//  /** Проверка совместимости типов через BasicTypeCategory */
//
//  /** Получает целевой атрибут для оператора сравнения */
//  private MetaAttribute getTargetAttribute(PredicateNode node) {
//    if (node.getPathExpression() != null) {
//      return getLastAttributeFromPath(node.getPathExpression());
//    } else if (node.getLeftOperand() != null
//        && node.getLeftOperand().getNodeType() == NodeType.PATH_EXPRESSION) {
//      return getLastAttributeFromPath(node.getLeftOperand().getPathExpression());
//    }
//    return null;
//  }
//
//  private MetaAttribute getLastAttributeFromPath(PredicatePathExpression pathExpression) {
//    if (pathExpression == null) return null;
//
//    // Новый способ построения пути - последний атрибут в цепочке
//    if (pathExpression.getPathAttributes().isEmpty()) {
//      return pathExpression.getFinalPathAttribute();
//    } else {
//      return pathExpression.getPathAttributes().get(pathExpression.getPathAttributes().size() - 1);
//    }
//  }
//
//  /** Проверка совместимости оператора и типа атрибута */
//  private List<String> validateOperatorAttributeCompatibility(
//      OperatorType operatorType, MetaAttribute attribute, BasicTypeCategory expectedCategory) {
//    List<String> errors = new ArrayList<>();
//    //
//    //    if (attribute.getAttributeCategory() != AttributeCategory.BASIC) {
//    //      return errors;
//    //    }
//    //
//    //    BasicType attributeType = attribute.getBasicType();
//    //    if (attributeType == null) return errors;
//    //
//    //    BasicTypeCategory attributeCategory = attributeType.getCategory();
//    //
//    //    // Проверяем что категория атрибута соответствует ожидаемой для оператора
//    //    if (expectedCategory != BasicTypeCategory.ALL && attributeCategory != expectedCategory) {
//    //      errors.add(
//    //          "Operator "
//    //              + operatorType
//    //              + " requires "
//    //              + expectedCategory
//    //              + " type, but attribute is "
//    //              + attributeCategory);
//    //    }
//
//    return errors;
//  }
//
//  // ... остальные методы (validateValueConstantNode, validatePathExpressionNode,
//  // validateMutuallyExclusiveFields, validatePredicateNodeValue, validatePathExpression)
//  // остаются в основном без изменений, но должны использовать новые подходы к построению путей
//
//  private List<String> validateValueConstantNode(PredicateNode node) {
//    List<String> errors = new ArrayList<>();
//    // value обязателен для VALUE_CONSTANT, если не используется values
//    if (node.getValue() == null && node.getValues().isEmpty()) {
//      errors.add("VALUE_CONSTANT must have either value or values defined");
//    }
//    if (node.getValue() != null) {
//      ValidationResult valueResult = validatePredicateNodeValue(node.getValue());
//      if (!valueResult.isValid()) {
//        errors.addAll(valueResult.getErrors());
//      }
//    }
//    // Валидация values если используется
//    if (!node.getValues().isEmpty()) {
//      node.getValues().stream()
//          .map(this::validatePredicateNodeValue)
//          .filter(valueResult -> !valueResult.isValid())
//          .map(valueResult -> "values: " + String.join(", ", valueResult.getErrors()))
//          .forEach(errors::add);
//    }
//    // Запрещенные поля для VALUE_CONSTANT
//    if (node.getOperatorType() != null) {
//      errors.add("VALUE_CONSTANT cannot have operatorType");
//    }
//    if (node.getPathExpression() != null) {
//      errors.add("VALUE_CONSTANT cannot have pathExpression");
//    }
//    if (node.getLeftOperand() != null) {
//      errors.add("VALUE_CONSTANT cannot have leftOperand");
//    }
//    if (node.getRightOperand() != null) {
//      errors.add("VALUE_CONSTANT cannot have rightOperand");
//    }
//
//    return errors;
//  }
//
//  private List<String> validatePathExpressionNode(PredicateNode node, ValidationContext context) {
//    List<String> errors = new ArrayList<>();
//
//    // pathExpression обязателен для PATH_EXPRESSION
//    if (node.getPathExpression() == null) {
//      errors.add("PATH_EXPRESSION must have pathExpression defined");
//    } else {
//      ValidationResult pathResult = validatePathExpression(node.getPathExpression());
//      if (!pathResult.isValid()) {
//        errors.addAll(pathResult.getErrors());
//      }
//    }
//
//    // Запрещенные поля для PATH_EXPRESSION
//    if (node.getOperatorType() != null) {
//      errors.add("PATH_EXPRESSION cannot have operatorType");
//    }
//
//    if (node.getValue() != null) {
//      errors.add("PATH_EXPRESSION cannot have value");
//    }
//
//    if (node.getLeftOperand() != null) {
//      errors.add("PATH_EXPRESSION cannot have leftOperand");
//    }
//
//    if (node.getRightOperand() != null) {
//      errors.add("PATH_EXPRESSION cannot have rightOperand");
//    }
//
//    if (!node.getValues().isEmpty()) {
//      errors.add("PATH_EXPRESSION cannot have values");
//    }
//
//    return errors;
//  }
//
//  private List<String> validateMutuallyExclusiveFields(PredicateNode node) {
//    List<String> errors = new ArrayList<>();
//    // value и values взаимоисключающие
//    if (node.getValue() != null && !node.getValues().isEmpty()) {
//      errors.add("value and values cannot both be set");
//    }
//    return errors;
//  }
//
//  /** Валидация PredicatePathExpression с новым способом построения пути */
//  public ValidationResult validatePathExpression(PredicatePathExpression pathExpression) {
//    List<String> errors = new ArrayList<>();
//    // rootAttribute обязателен
//    if (pathExpression.getFinalPathAttribute() == null) {
//      errors.add("Path expression must have rootAttribute defined");
//    }
//    if (AttributeCategory.BASIC != pathExpression.getFinalPathAttribute().getAttributeCategory()) {
//      errors.add("Path expression root must be BASIC");
//    }
//
//    // Валидация цепочки атрибутов с новым подходом
//    if (pathExpression.getPathAttributes() != null
//        && !pathExpression.getPathAttributes().isEmpty()) {
//      MetaAttribute previousAttribute = pathExpression.getFinalPathAttribute();
//      for (int i = 0; i < pathExpression.getPathAttributes().size(); i++) {
//        MetaAttribute currentAttribute = pathExpression.getPathAttributes().get(i);
//        if (currentAttribute == null) {
//          errors.add("Path attribute at index " + i + " cannot be null");
//          continue;
//        }
//        if (currentAttribute.getAttributeCategory() != AttributeCategory.ENTITY) {
//          errors.add("Path attribute at index " + i + " must be Entity");
//          continue;
//        }
//
//        // Проверка наличия атрибута в последующем Entity
//        if (previousAttribute.getEntity() != currentAttribute.getAttributeEntityType()) {
//          errors.add(
//              "Entity mismatch in path: attribute '"
//                  + currentAttribute.getName()
//                  + "' belongs to wrong entity. Expected: "
//                  + previousAttribute.getEntity().getName());
//        }
//
//        previousAttribute = currentAttribute;
//      }
//    }
//
//    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
//  }
//
//  public ValidationResult validateMetaEnumValue(MetaEnumValue enumValue) {
//    List<String> errors = new ArrayList<>();
//
//    if (enumValue == null) {
//      return ValidationResult.error("MetaEnumValue cannot be null");
//    }
//
//    if (enumValue.getName() == null || enumValue.getName().trim().isEmpty()) {
//      errors.add("Enum value name cannot be null or empty");
//    }
//
//    if (enumValue.getStorageValue() == null || enumValue.getStorageValue().trim().isEmpty()) {
//      errors.add("Enum storage value cannot be null or empty");
//    }
//
//    if (enumValue.getMetaEnum() == null) {
//      errors.add("Enum value must belong to a MetaEnum");
//    }
//
//    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
//  }
//
//  public ValidationResult validateMetaEnum(MetaEnum metaEnum) {
//    List<String> errors = new ArrayList<>();
//
//    if (metaEnum == null) {
//      return ValidationResult.error("MetaEnum cannot be null");
//    }
//
//    if (metaEnum.getName() == null || metaEnum.getName().trim().isEmpty()) {
//      errors.add("MetaEnum name cannot be null or empty");
//    }
//
//    if (metaEnum.getClassName() == null || metaEnum.getClassName().trim().isEmpty()) {
//      errors.add("MetaEnum class name cannot be null or empty");
//    }
//
//    // Валидация значений enum
//    if (metaEnum.getValues() != null) {
//      for (MetaEnumValue enumValue : metaEnum.getValues()) {
//        ValidationResult valueResult = validateMetaEnumValue(enumValue);
//        if (!valueResult.isValid()) {
//          errors.add(
//              "Enum value '"
//                  + enumValue.getName()
//                  + "': "
//                  + String.join(", ", valueResult.getErrors()));
//        }
//      }
//    }
//
//    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
//  }
//
//  public ValidationResult validatePredicateNodeValue(PredicateNodeValue value) {
//    List<String> errors = new ArrayList<>();
//
//    if (value == null) {
//      return ValidationResult.error("PredicateNodeValue cannot be null");
//    }
//
//    if (value.getValueType() == null) {
//      errors.add("Value type cannot be null");
//    }
//
//    // Проверка, что заполнено только одно поле в зависимости от valueType
//    errors.addAll(validateSingleValueField(value));
//
//    // Для ENUM типа должна быть ссылка на MetaEnumValue
//    if (value.getValueType() == BasicType.ENUM && value.getEnumValue() == null) {
//      errors.add("ENUM values must have enumValue defined");
//    }
//
//    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
//  }
//
//  private List<String> validateSingleValueField(PredicateNodeValue value) {
//    List<String> errors = new ArrayList<>();
//    // Проверяем, что только одно поле значения заполнено
//    int filledFields = 0;
//
//    if (value.getStringValue() != null) filledFields++;
//    if (value.getBooleanValue() != null) filledFields++;
//    if (value.getIntegerValue() != null) filledFields++;
//    if (value.getDoubleValue() != null) filledFields++;
//    if (value.getOffsetDateTimeValue() != null) filledFields++;
//    if (value.getTimestampValue() != null) filledFields++;
//    if (value.getEnumValue() != null) filledFields++;
//    if (value.getPointValue() != null) filledFields++;
//
//    if (filledFields == 0) {
//      errors.add("At least one value field must be filled");
//    } else if (filledFields > 1) {
//      errors.add("Only one value field can be filled");
//    } else {
//      // Проверка соответствия типа значения и заполненного поля
//      switch (value.getValueType()) {
//        case STRING:
//          if (value.getStringValue() == null) {
//            errors.add("STRING value type must have stringValue filled");
//          }
//          break;
//        case BOOLEAN:
//          if (value.getBooleanValue() == null) {
//            errors.add("BOOLEAN value type must have booleanValue filled");
//          }
//          break;
//        case INTEGER:
//          if (value.getIntegerValue() == null) {
//            errors.add("INTEGER value type must have integerValue filled");
//          }
//          break;
//        case DOUBLE:
//          if (value.getDoubleValue() == null) {
//            errors.add("DOUBLE value type must have doubleValue filled");
//          }
//          break;
//        case OFFSET_DATE_TIME:
//          if (value.getOffsetDateTimeValue() == null) {
//            errors.add("OFFSET_DATE_TIME value type must have offsetDateTimeValue filled");
//          }
//          break;
//        case ENUM:
//          if (value.getEnumValue() == null) {
//            errors.add("ENUM value type must have enumValue filled");
//          }
//          break;
//        case POINT:
//          if (value.getPointValue() == null) {
//            errors.add("POINT value type must have pointValue filled");
//          }
//          break;
//      }
//    }
//
//    return errors;
//  }

  // Контекст для отслеживания состояния валидации
  @Getter
  private static class ValidationContext {
    private final Set<UUID> visitedNodes = new HashSet<>();
  }
}
