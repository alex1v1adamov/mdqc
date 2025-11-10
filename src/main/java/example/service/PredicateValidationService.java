package example.service;

import example.models.meta.AttributeCategory;
import example.models.meta.BasicType;
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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class PredicateValidationService implements Validate<PredicateDefinition> {

  /** Валидация PredicateDefinition */
  public ValidationResult validate(PredicateDefinition predicateDefinition) {
    List<String> errors = new ArrayList<>();

    if (predicateDefinition == null) {
      return ValidationResult.error("PredicateDefinition cannot be null");
    }

    if (predicateDefinition.getName() == null || predicateDefinition.getName().trim().isEmpty()) {
      errors.add("Predicate name cannot be null or empty");
    }

    if (predicateDefinition.getMetaEntity() == null) {
      errors.add("Predicate must reference a MetaEntity");
    }

    if (predicateDefinition.getRootNode() == null) {
      errors.add("Predicate must have a root node");
    } else {
      ValidationResult nodeResult = validatePredicateNode(predicateDefinition.getRootNode());
      if (!nodeResult.isValid()) {
        errors.add("Root node: " + String.join(", ", nodeResult.getErrors()));
      }
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
  }

  /** Валидация PredicateNode с учетом всех правил из комментариев */
  public ValidationResult validatePredicateNode(PredicateNode node) {
    return validatePredicateNode(node, new ValidationContext());
  }

  private ValidationResult validatePredicateNode(PredicateNode node, ValidationContext context) {
    List<String> errors = new ArrayList<>();

    if (node == null) {
      return ValidationResult.error("PredicateNode cannot be null");
    }

    if (node.getNodeType() == null) {
      errors.add("Node type cannot be null");
      return ValidationResult.error(errors);
    }

    // Проверка на циклические ссылки
    if (context.getVisitedNodes().contains(node.getId())) {
      errors.add("Cyclic reference detected in predicate nodes");
      return ValidationResult.error(errors);
    }
    context.getVisitedNodes().add(node.getId());

    // Валидация в зависимости от типа узла
    switch (node.getNodeType()) {
      case LOGICAL_OPERATOR:
        errors.addAll(validateLogicalOperatorNode(node, context));
        break;
      case COMPARISON_OPERATOR:
        errors.addAll(validateComparisonOperatorNode(node, context));
        break;
      case VALUE_CONSTANT:
        errors.addAll(validateValueConstantNode(node));
        break;
      case PATH_EXPRESSION:
        errors.addAll(validatePathExpressionNode(node, context));
        break;
    }

    // Валидация взаимно исключающих полей
    errors.addAll(validateMutuallyExclusiveFields(node));

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
  }

  private List<String> validateLogicalOperatorNode(PredicateNode node, ValidationContext context) {
    List<String> errors = new ArrayList<>();

    // operatorType обязателен для LOGICAL_OPERATOR
    if (node.getOperatorType() == null) {
      errors.add("LOGICAL_OPERATOR must have operatorType defined");
    } else {
      // Проверка допустимых операторов
      switch (node.getOperatorType()) {
        case AND, OR:
          if (node.getLeftOperand() == null || node.getRightOperand() == null) {
            errors.add("AND/OR operators must have both left and right operands");
          }
          break;
        case NOT:
          if (node.getLeftOperand() == null) {
            errors.add("NOT operator must have left operand");
          }
          if (node.getRightOperand() != null) {
            errors.add("NOT operator cannot have right operand");
          }
          break;
        default:
          errors.add("Unsupported logical operator: " + node.getOperatorType());
      }
    }

    // Рекурсивная валидация операндов
    if (node.getLeftOperand() != null) {
      ValidationResult leftResult = validatePredicateNode(node.getLeftOperand(), context);
      if (!leftResult.isValid()) {
        errors.add("Left operand: " + String.join(", ", leftResult.getErrors()));
      }
    }

    if (node.getRightOperand() != null) {
      ValidationResult rightResult = validatePredicateNode(node.getRightOperand(), context);
      if (!rightResult.isValid()) {
        errors.add("Right operand: " + String.join(", ", rightResult.getErrors()));
      }
    }

    // Запрещенные поля для LOGICAL_OPERATOR
    if (node.getMetaAttribute() != null) {
      errors.add("LOGICAL_OPERATOR cannot have metaAttribute");
    }

    if (node.getPathExpression() != null) {
      errors.add("LOGICAL_OPERATOR cannot have pathExpression");
    }

    if (node.getValue() != null) {
      errors.add("LOGICAL_OPERATOR cannot have value");
    }

    if (!node.getInValues().isEmpty()) {
      errors.add("LOGICAL_OPERATOR cannot have inValues");
    }

    return errors;
  }

  private List<String> validateComparisonOperatorNode(
      PredicateNode node, ValidationContext context) {
    List<String> errors = new ArrayList<>();

    // operatorType обязателен для COMPARISON_OPERATOR
    if (node.getOperatorType() == null) {
      errors.add("COMPARISON_OPERATOR must have operatorType defined");
    }

    // Проверка наличия атрибута или path expression
    boolean hasAttribute = node.getMetaAttribute() != null;
    boolean hasPathExpression = node.getPathExpression() != null;
    boolean hasLeftOperand = node.getLeftOperand() != null;

    // Специальная логика для BETWEEN оператора
    if (node.getOperatorType() == OperatorType.BETWEEN) {
      // BETWEEN требует metaAttribute и оба операнда
      if (!hasAttribute) {
        errors.add("BETWEEN operator must have metaAttribute defined");
      }
      if (node.getLeftOperand() == null || node.getRightOperand() == null) {
        errors.add("BETWEEN operator must have both left and right operands");
      }
      if (hasPathExpression) {
        errors.add("BETWEEN operator cannot have pathExpression");
      }
    } else {
      // Для остальных операторов проверяем стандартные правила
      if (!hasAttribute && !hasPathExpression && !hasLeftOperand) {
        errors.add(
            "COMPARISON_OPERATOR must have either metaAttribute, pathExpression or leftOperand");
      }

      // Проверка взаимной исключительности (кроме BETWEEN)
      if ((hasAttribute && hasPathExpression)
          || (hasAttribute && hasLeftOperand)
          || (hasPathExpression && hasLeftOperand)) {
        errors.add(
            "COMPARISON_OPERATOR can have only one of: metaAttribute, pathExpression, or leftOperand");
      }
    }

    // Получаем целевой атрибут для проверки совместимости типов
    MetaAttribute targetAttribute = getTargetAttribute(node);
    if (targetAttribute != null && node.getOperatorType() != null) {
      errors.addAll(
          validateOperatorAttributeCompatibility(node.getOperatorType(), targetAttribute));
    }

    // Валидация операндов
    if (node.getOperatorType() != OperatorType.IS_NULL
        && node.getOperatorType() != OperatorType.IS_NOT_NULL) {

      // Для BETWEEN проверяем оба операнда
      if (node.getOperatorType() == OperatorType.BETWEEN) {
        if (node.getLeftOperand() == null || node.getRightOperand() == null) {
          errors.add("BETWEEN operator must have both left and right operands");
        } else {
          ValidationResult leftResult = validatePredicateNode(node.getLeftOperand(), context);
          if (!leftResult.isValid()) {
            errors.add("Left operand: " + String.join(", ", leftResult.getErrors()));
          }

          ValidationResult rightResult = validatePredicateNode(node.getRightOperand(), context);
          if (!rightResult.isValid()) {
            errors.add("Right operand: " + String.join(", ", rightResult.getErrors()));
          }

          // Проверка совместимости типов для BETWEEN
          if (targetAttribute != null) {
            errors.addAll(
                validateAttributeOperandCompatibility(targetAttribute, node.getLeftOperand()));
            errors.addAll(
                validateAttributeOperandCompatibility(targetAttribute, node.getRightOperand()));
          }
        }
      } else {
        // Для остальных операторов проверяем только правый операнд
        if (node.getRightOperand() == null) {
          errors.add("COMPARISON_OPERATOR must have rightOperand (except for IS_NULL/IS_NOT_NULL)");
        } else {
          ValidationResult rightResult = validatePredicateNode(node.getRightOperand(), context);
          if (!rightResult.isValid()) {
            errors.add("Right operand: " + String.join(", ", rightResult.getErrors()));
          }

          // Проверка совместимости типов атрибута и правого операнда
          if (targetAttribute != null) {
            errors.addAll(
                validateAttributeOperandCompatibility(targetAttribute, node.getRightOperand()));
          }
        }
      }
    } else {
      // Для IS_NULL/IS_NOT_NULL правый операнд должен быть null
      if (node.getRightOperand() != null) {
        errors.add("IS_NULL/IS_NOT_NULL cannot have rightOperand");
      }
    }

    // Валидация inValues для операторов IN/NOT_IN
    if (node.getOperatorType() == OperatorType.IN
        || node.getOperatorType() == OperatorType.NOT_IN) {
      if (node.getInValues().isEmpty()) {
        errors.add("IN/NOT_IN operators must have at least one value in inValues");
      }
      if (node.getValue() != null) {
        errors.add("IN/NOT_IN operators cannot have value, use inValues instead");
      }

      // Проверка типов в inValues
      BasicType firstType = null;
      for (PredicateNodeValue inValue : node.getInValues()) {
        ValidationResult valueResult = validatePredicateNodeValue(inValue);
        if (!valueResult.isValid()) {
          errors.add("inValues: " + String.join(", ", valueResult.getErrors()));
        }

        if (firstType == null) {
          firstType = inValue.getValueType();
        } else if (firstType != inValue.getValueType()) {
          errors.add("All values in inValues must be of the same type");
          break;
        }
      }

      // Проверка совместимости типа атрибута и типа значений IN
      if (targetAttribute != null && firstType != null) {
        errors.addAll(validateInValuesTypeCompatibility(targetAttribute, firstType));
      }
    } else {
      // Для других операторов inValues должен быть пуст
      if (!node.getInValues().isEmpty()) {
        errors.add("inValues can only be used with IN/NOT_IN operators");
      }
    }

    return errors;
  }

  /** Получает целевой атрибут для оператора сравнения */
  private MetaAttribute getTargetAttribute(PredicateNode node) {
    if (node.getMetaAttribute() != null) {
      return node.getMetaAttribute();
    } else if (node.getPathExpression() != null) {
      return node.getPathExpression().getTargetAttribute();
    } else if (node.getLeftOperand() != null
        && node.getLeftOperand().getNodeType() == NodeType.PATH_EXPRESSION) {
      return node.getLeftOperand().getPathExpression().getTargetAttribute();
    }
    return null;
  }

  /** Проверка совместимости оператора и типа атрибута */
  private List<String> validateOperatorAttributeCompatibility(
      OperatorType operatorType, MetaAttribute attribute) {
    List<String> errors = new ArrayList<>();

    if (attribute.getAttributeCategory() != AttributeCategory.BASIC) {
      // Для ENTITY атрибутов допустимы только определенные операторы
      switch (operatorType) {
        case EQ, NE, IS_NULL, IS_NOT_NULL:
          // Эти операторы допустимы для ENTITY атрибутов
          break;
        default:
          errors.add(
              String.format(
                  "Operator %s cannot be used with ENTITY attribute '%s'",
                  operatorType, attribute.getName()));
      }
      return errors;
    }

    // Для BASIC атрибутов проверяем в зависимости от basicType
    BasicType basicType = attribute.getBasicType();
    if (basicType == null) return errors;

    switch (basicType) {
      case BOOLEAN:
        if (!isBooleanCompatibleOperator(operatorType)) {
          errors.add(
              String.format(
                  "Operator %s cannot be used with BOOLEAN attribute '%s'. "
                      + "Allowed operators: EQ, NE, IS_NULL, IS_NOT_NULL",
                  operatorType, attribute.getName()));
        }
        break;

      case STRING:
        if (!isStringCompatibleOperator(operatorType)) {
          errors.add(
              String.format(
                  "Operator %s cannot be used with STRING attribute '%s'. "
                      + "Allowed operators: EQ, NE, LIKE, STARTS_WITH, ENDS_WITH, CONTAINS, IN, NOT_IN, IS_NULL, IS_NOT_NULL",
                  operatorType, attribute.getName()));
        }
        break;

      case INTEGER:
        if (!isNumericCompatibleOperator(operatorType)) {
          errors.add(
              String.format(
                  "Operator %s cannot be used with INTEGER attribute '%s'. "
                      + "Allowed operators: EQ, NE, GT, LT, GOE, LOE, IN, NOT_IN, BETWEEN, IS_NULL, IS_NOT_NULL",
                  operatorType, attribute.getName()));
        }
        break;

      case OFFSET_DATE_TIME:
        if (!isDateCompatibleOperator(operatorType)) {
          errors.add(
              String.format(
                  "Operator %s cannot be used with DATE attribute '%s'. "
                      + "Allowed operators: EQ, NE, GT, LT, GOE, LOE, BETWEEN, IS_NULL, IS_NOT_NULL",
                  operatorType, attribute.getName()));
        }
        break;

      case ENUM:
        if (!isEnumCompatibleOperator(operatorType)) {
          errors.add(
              String.format(
                  "Operator %s cannot be used with ENUM attribute '%s'. "
                      + "Allowed operators: EQ, NE, IN, NOT_IN, IS_NULL, IS_NOT_NULL",
                  operatorType, attribute.getName()));
        }
        break;
    }

    return errors;
  }

  /** Проверка совместимости типа атрибута и типа правого операнда */
  private List<String> validateAttributeOperandCompatibility(
      MetaAttribute attribute, PredicateNode rightOperand) {
    List<String> errors = new ArrayList<>();

    if (attribute.getAttributeCategory() != AttributeCategory.BASIC) {
      return errors; // Для ENTITY атрибутов проверка сложнее, пропускаем
    }

    BasicType attributeType = attribute.getBasicType();
    if (attributeType == null) return errors;

    // Определяем тип правого операнда
    BasicType operandType = getOperandType(rightOperand);
    if (operandType == null) return errors;

    // Проверяем совместимость типов
    if (!areTypesCompatible(attributeType, operandType)) {
      errors.add(
          String.format(
              "Type mismatch: attribute '%s' has type %s but operand has type %s",
              attribute.getName(), attributeType, operandType));
    }

    return errors;
  }

  /** Проверка совместимости типа атрибута и типа значений IN */
  private List<String> validateInValuesTypeCompatibility(
      MetaAttribute attribute, BasicType inValuesType) {
    List<String> errors = new ArrayList<>();

    if (attribute.getAttributeCategory() != AttributeCategory.BASIC) {
      return errors;
    }

    BasicType attributeType = attribute.getBasicType();
    if (attributeType == null) return errors;

    if (!areTypesCompatible(attributeType, inValuesType)) {
      errors.add(
          String.format(
              "Type mismatch: attribute '%s' has type %s but IN values have type %s",
              attribute.getName(), attributeType, inValuesType));
    }

    return errors;
  }

  /** Определяет тип правого операнда */
  private BasicType getOperandType(PredicateNode operand) {
    if (operand == null) return null;

    switch (operand.getNodeType()) {
      case VALUE_CONSTANT:
        return operand.getValue() != null ? operand.getValue().getValueType() : null;
      case PATH_EXPRESSION:
        MetaAttribute targetAttr =
            operand.getPathExpression() != null
                ? operand.getPathExpression().getTargetAttribute()
                : null;
        return targetAttr != null && targetAttr.getAttributeCategory() == AttributeCategory.BASIC
            ? targetAttr.getBasicType()
            : null;
      default:
        return null;
    }
  }

  /** Проверяет совместимость типов для операций сравнения */
  private boolean areTypesCompatible(BasicType attributeType, BasicType operandType) {
    if (attributeType == operandType) return true;

    // INTEGER и LONG считаются совместимыми
    if ((attributeType == BasicType.INTEGER && operandType == BasicType.INTEGER)) {
      return true;
    }

    // DATE и TIMESTAMP могут быть совместимы в некоторых случаях
    if ((attributeType == BasicType.OFFSET_DATE_TIME
        && operandType == BasicType.OFFSET_DATE_TIME)) {
      return true;
    }

    return false;
  }

  // Методы проверки совместимости операторов для разных типов

  private boolean isBooleanCompatibleOperator(OperatorType operatorType) {
    return switch (operatorType) {
      case EQ, NE, IS_NULL, IS_NOT_NULL -> true;
      default -> false;
    };
  }

  private boolean isStringCompatibleOperator(OperatorType operatorType) {
    return switch (operatorType) {
      case EQ, NE, LIKE, STARTS_WITH, ENDS_WITH, CONTAINS, IN, NOT_IN, IS_NULL, IS_NOT_NULL -> true;
      default -> false;
    };
  }

  private boolean isNumericCompatibleOperator(OperatorType operatorType) {
    return switch (operatorType) {
      case EQ, NE, GT, LT, GOE, LOE, IN, NOT_IN, BETWEEN, IS_NULL, IS_NOT_NULL -> true;
      default -> false;
    };
  }

  private boolean isDateCompatibleOperator(OperatorType operatorType) {
    return switch (operatorType) {
      case EQ, NE, GT, LT, GOE, LOE, BETWEEN, IS_NULL, IS_NOT_NULL -> true;
      default -> false;
    };
  }

  private boolean isEnumCompatibleOperator(OperatorType operatorType) {
    return switch (operatorType) {
      case EQ, NE, IN, NOT_IN, IS_NULL, IS_NOT_NULL -> true;
      default -> false;
    };
  }

  // Остальные методы остаются без изменений
  private List<String> validateValueConstantNode(PredicateNode node) {
    List<String> errors = new ArrayList<>();

    // value обязателен для VALUE_CONSTANT
    if (node.getValue() == null) {
      errors.add("VALUE_CONSTANT must have value defined");
    } else {
      ValidationResult valueResult = validatePredicateNodeValue(node.getValue());
      if (!valueResult.isValid()) {
        errors.addAll(valueResult.getErrors());
      }
    }

    // Запрещенные поля для VALUE_CONSTANT
    if (node.getOperatorType() != null) {
      errors.add("VALUE_CONSTANT cannot have operatorType");
    }

    if (node.getMetaAttribute() != null) {
      errors.add("VALUE_CONSTANT cannot have metaAttribute");
    }

    if (node.getPathExpression() != null) {
      errors.add("VALUE_CONSTANT cannot have pathExpression");
    }

    if (node.getLeftOperand() != null) {
      errors.add("VALUE_CONSTANT cannot have leftOperand");
    }

    if (node.getRightOperand() != null) {
      errors.add("VALUE_CONSTANT cannot have rightOperand");
    }

    if (!node.getInValues().isEmpty()) {
      errors.add("VALUE_CONSTANT cannot have inValues");
    }

    return errors;
  }

  private List<String> validatePathExpressionNode(PredicateNode node, ValidationContext context) {
    List<String> errors = new ArrayList<>();

    // pathExpression обязателен для PATH_EXPRESSION
    if (node.getPathExpression() == null) {
      errors.add("PATH_EXPRESSION must have pathExpression defined");
    } else {
      ValidationResult pathResult = validatePathExpression(node.getPathExpression());
      if (!pathResult.isValid()) {
        errors.addAll(pathResult.getErrors());
      }
    }

    // Запрещенные поля для PATH_EXPRESSION
    if (node.getOperatorType() != null) {
      errors.add("PATH_EXPRESSION cannot have operatorType");
    }

    if (node.getMetaAttribute() != null) {
      errors.add("PATH_EXPRESSION cannot have metaAttribute when pathExpression is defined");
    }

    if (node.getValue() != null) {
      errors.add("PATH_EXPRESSION cannot have value");
    }

    if (node.getLeftOperand() != null) {
      errors.add("PATH_EXPRESSION cannot have leftOperand");
    }

    if (node.getRightOperand() != null) {
      errors.add("PATH_EXPRESSION cannot have rightOperand");
    }

    if (!node.getInValues().isEmpty()) {
      errors.add("PATH_EXPRESSION cannot have inValues");
    }

    return errors;
  }

  private List<String> validateMutuallyExclusiveFields(PredicateNode node) {
    List<String> errors = new ArrayList<>();

    // metaAttribute и pathExpression взаимоисключающие
    if (node.getMetaAttribute() != null && node.getPathExpression() != null) {
      errors.add("metaAttribute and pathExpression cannot both be set");
    }

    // value и inValues взаимоисключающие
    if (node.getValue() != null && !node.getInValues().isEmpty()) {
      errors.add("value and inValues cannot both be set");
    }

    return errors;
  }

  /** Валидация PredicateNodeValue */
  public ValidationResult validatePredicateNodeValue(PredicateNodeValue value) {
    List<String> errors = new ArrayList<>();

    if (value == null) {
      return ValidationResult.error("PredicateNodeValue cannot be null");
    }

    if (value.getValueType() == null) {
      errors.add("Value type cannot be null");
    }

    // Проверка, что заполнено только одно поле в зависимости от valueType
    errors.addAll(validateSingleValueField(value));

    // Для ENUM типа должна быть ссылка на MetaEnumValue
    if (value.getValueType() == BasicType.ENUM && value.getEnumValue() == null) {
      errors.add("ENUM values must have enumValue defined");
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
  }

  private List<String> validateSingleValueField(PredicateNodeValue value) {
    List<String> errors = new ArrayList<>();

    if (value.getValueType() == null) return errors;

    // Проверяем, что только одно поле значения заполнено
    int filledFields = 0;

    if (value.getStringValue() != null) filledFields++;
    if (value.getBooleanValue() != null) filledFields++;
    if (value.getIntegerValue() != null) filledFields++;
    if (value.getLongValue() != null) filledFields++;
    if (value.getOffsetDateTimeValue() != null) filledFields++;
    if (value.getTimestampValue() != null) filledFields++;
    if (value.getEnumValue() != null) filledFields++;

    if (filledFields == 0) {
      errors.add("At least one value field must be filled");
    } else if (filledFields > 1) {
      errors.add("Only one value field can be filled");
    } else {
      // Проверка соответствия типа значения и заполненного поля
      switch (value.getValueType()) {
        case STRING:
          if (value.getStringValue() == null) {
            errors.add("STRING value type must have stringValue filled");
          }
          break;
        case BOOLEAN:
          if (value.getBooleanValue() == null) {
            errors.add("BOOLEAN value type must have booleanValue filled");
          }
          break;
        case INTEGER:
          if (value.getIntegerValue() == null) {
            errors.add("INTEGER value type must have integerValue filled");
          }
          break;
        case OFFSET_DATE_TIME:
          if (value.getOffsetDateTimeValue() == null) {
            errors.add("DATE value type must have dateValue filled");
          }
          break;
        case ENUM:
          if (value.getEnumValue() == null) {
            errors.add("ENUM value type must have enumValue filled");
          }
          break;
      }
    }

    return errors;
  }

  /** Валидация PredicatePathExpression */
  public ValidationResult validatePathExpression(PredicatePathExpression pathExpression) {
    List<String> errors = new ArrayList<>();

    if (pathExpression == null) {
      return ValidationResult.error("PredicatePathExpression cannot be null");
    }

    // rootAttribute обязателен
    if (pathExpression.getRootAttribute() == null) {
      errors.add("Path expression must have rootAttribute defined");
    } else {
      // rootAttribute должен быть ENTITY
      if (pathExpression.getRootAttribute().getAttributeCategory() != AttributeCategory.ENTITY) {
        errors.add("Root attribute must be ENTITY category");
      }
    }

    // Валидация цепочки атрибутов
    if (pathExpression.getPathAttributes() != null) {
      for (int i = 0; i < pathExpression.getPathAttributes().size(); i++) {
        MetaAttribute attribute = pathExpression.getPathAttributes().get(i);

        if (attribute == null) {
          errors.add("Path attribute at index " + i + " cannot be null");
          continue;
        }

        // Все атрибуты кроме последнего должны быть ENTITY
        if (i < pathExpression.getPathAttributes().size() - 1) {
          if (attribute.getAttributeCategory() != AttributeCategory.ENTITY) {
            errors.add(
                "Intermediate path attribute '"
                    + attribute.getName()
                    + "' must be ENTITY category");
          }
        } else {
          // Последний атрибут должен быть BASIC
          if (attribute.getAttributeCategory() != AttributeCategory.BASIC) {
            errors.add("Last path attribute '" + attribute.getName() + "' must be BASIC category");
          }
        }
      }
    }

    // Валидация targetAttribute
    if (pathExpression.getTargetAttribute() != null) {
      if (pathExpression.getTargetAttribute().getAttributeCategory() != AttributeCategory.BASIC) {
        errors.add("Target attribute must be BASIC category");
      }

      // targetAttribute должен соответствовать последнему атрибуту в цепочке
      if (pathExpression.getPathAttributes() != null
          && !pathExpression.getPathAttributes().isEmpty()) {

        MetaAttribute lastAttribute =
            pathExpression.getPathAttributes().get(pathExpression.getPathAttributes().size() - 1);

        if (!pathExpression.getTargetAttribute().getId().equals(lastAttribute.getId())) {
          errors.add("Target attribute must match the last attribute in path chain");
        }
      }
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
  }

  /** Валидация MetaEnum */
  public ValidationResult validateMetaEnum(MetaEnum metaEnum) {
    List<String> errors = new ArrayList<>();

    if (metaEnum == null) {
      return ValidationResult.error("MetaEnum cannot be null");
    }

    if (metaEnum.getName() == null || metaEnum.getName().trim().isEmpty()) {
      errors.add("MetaEnum name cannot be null or empty");
    }

    if (metaEnum.getClassName() == null || metaEnum.getClassName().trim().isEmpty()) {
      errors.add("MetaEnum class name cannot be null or empty");
    }

    // Валидация значений enum
    if (metaEnum.getValues() != null) {
      for (MetaEnumValue enumValue : metaEnum.getValues()) {
        ValidationResult valueResult = validateMetaEnumValue(enumValue);
        if (!valueResult.isValid()) {
          errors.add(
              "Enum value '"
                  + enumValue.getName()
                  + "': "
                  + String.join(", ", valueResult.getErrors()));
        }
      }
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
  }

  /** Валидация MetaEnumValue */
  public ValidationResult validateMetaEnumValue(MetaEnumValue enumValue) {
    List<String> errors = new ArrayList<>();

    if (enumValue == null) {
      return ValidationResult.error("MetaEnumValue cannot be null");
    }

    if (enumValue.getName() == null || enumValue.getName().trim().isEmpty()) {
      errors.add("Enum value name cannot be null or empty");
    }

    if (enumValue.getStorageValue() == null || enumValue.getStorageValue().trim().isEmpty()) {
      errors.add("Enum storage value cannot be null or empty");
    }

    if (enumValue.getMetaEnum() == null) {
      errors.add("Enum value must belong to a MetaEnum");
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
  }

  // Контекст для отслеживания состояния валидации (например, циклических ссылок)
  private static class ValidationContext {
    private final Set<UUID> visitedNodes = new java.util.HashSet<>();

    public Set<UUID> getVisitedNodes() {
      return visitedNodes;
    }
  }
}
