package example.service.generator;

import com.querydsl.core.types.ConstantImpl;
import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Ops;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.ComparablePath;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.NumberTemplate;
import com.querydsl.core.types.dsl.PathBuilder;
import example.models.meta.AttributeCategory;
import example.models.meta.MetaAttribute;
import example.models.meta.MetaEntity;
import example.models.meta.MetaEnumValue;
import example.models.predicate.*;
import example.service.SpatialTemplateHelper;
import example.service.validation.PredicateValidationService;
import example.service.validation.ValidationResult;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.hibernate.Hibernate;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PredicateGeneratorService {

  private final ExpressionResolver expressionResolver;
  private final EntityClassResolver entityClassResolver;
  private final PredicateValidationService validationService;

  public BooleanExpression generatePredicate(PredicateDefinition predicateDefinition) {
    // Предварительная валидация предиката
    ValidationResult validationResult = validationService.validate(predicateDefinition);
    if (!validationResult.isValid()) {
      throw new IllegalArgumentException(
          "Predicate validation failed: " + String.join(", ", validationResult.getErrors()));
    }

    Class<?> entityClass = entityClassResolver.resolve(predicateDefinition.getMetaEntity());
    PathBuilder<?> entityPath = new PathBuilder<>(entityClass, "entity");
    return expressionResolver.buildExpression(predicateDefinition.getRootNode(), entityPath);
  }
}

@Component
class ExpressionResolver {

  private final ConstantBuilder constantBuilder;
  private final ExpressionBuilder expressionBuilder;

  public ExpressionResolver(ConstantBuilder constantBuilder, ExpressionBuilder expressionBuilder) {
    this.constantBuilder = constantBuilder;
    this.expressionBuilder = expressionBuilder;
  }

  public BooleanExpression buildExpression(PredicateNode node, PathBuilder<?> entityPath) {
    if (node == null) return null;

    // Распаковываем Hibernate proxy
    PredicateNode unproxiedNode = (PredicateNode) Hibernate.unproxy(node);

    return switch (unproxiedNode) {
      case EvaluationOperationNode evalNode -> buildEvaluationExpression(evalNode, entityPath);
      case PathExpressionNode pathNode -> buildPathExpression(pathNode, entityPath);
      case ValueConstantNode valueNode ->
          throw new IllegalArgumentException(
              "VALUE_CONSTANT cannot be used as standalone predicate");
      default ->
          throw new IllegalArgumentException(
              "Unsupported node type: " + unproxiedNode.getClass().getSimpleName());
    };
  }

  // Новый метод для построения выражений любого типа (не только Boolean)
  public Expression<?> buildAnyExpression(PredicateNode node, PathBuilder<?> entityPath) {
    if (node == null) return null;

    return switch (node) {
      case ValueConstantNode valueNode -> constantBuilder.buildConstant(valueNode);
      case PathExpressionNode pathNode ->
          expressionBuilder.buildPathExpression(pathNode.getPathExpression(), entityPath);
      case EvaluationOperationNode evalNode ->
          buildEvaluationOperationExpression(evalNode, entityPath);
      default ->
          throw new IllegalArgumentException(
              "Unsupported node type: " + node.getClass().getSimpleName());
    };
  }

  private BooleanExpression buildEvaluationExpression(
      EvaluationOperationNode node, PathBuilder<?> entityPath) {
    OperatorType operatorType = node.getOperatorType();

    // Для DISTANCE_SPHERE возвращаем BooleanExpression через сравнение
    if (operatorType == OperatorType.DISTANCE_SPHERE) {
      Expression<?> distanceExpr = buildEvaluationOperationExpression(node, entityPath);
      // DISTANCE_SPHERE обычно используется в сравнениях, поэтому здесь возвращаем null
      // и ожидаем, что он будет обработан в родительском узле сравнения
      return null;
    }

    // Обработка логических операторов
    if (isLogicalOperator(operatorType)) {
      return buildLogicalExpression(node, entityPath);
    }

    // Обработка операторов сравнения
    return buildComparisonExpression(node, entityPath);
  }

  private Expression<?> buildEvaluationOperationExpression(
      EvaluationOperationNode node, PathBuilder<?> entityPath) {
    OperatorType operatorType = node.getOperatorType();

    // Для DISTANCE_SPHERE возвращаем NumberTemplate<Double>
    if (operatorType == OperatorType.DISTANCE_SPHERE) {
      return expressionBuilder.buildDistanceSphereExpression(node, entityPath);
    }

    // Для остальных операторов возвращаем BooleanExpression
    return buildExpression(node, entityPath);
  }

  private BooleanExpression buildPathExpression(
      PathExpressionNode node, PathBuilder<?> entityPath) {
    Expression<?> pathExpression =
        expressionBuilder.buildPathExpression(node.getPathExpression(), entityPath);
    return pathExpression != null ? Expressions.predicate(Ops.IS_NOT_NULL, pathExpression) : null;
  }

  private boolean isLogicalOperator(OperatorType operatorType) {
    return operatorType == OperatorType.AND
        || operatorType == OperatorType.OR
        || operatorType == OperatorType.NOT;
  }

  private BooleanExpression buildLogicalExpression(
      EvaluationOperationNode node, PathBuilder<?> entityPath) {
    BooleanExpression left = buildExpression(node.getLeftOperand(), entityPath);

    if (node.getOperatorType() == OperatorType.NOT) {
      return left != null ? left.not() : null;
    }

    BooleanExpression right = buildExpression(node.getRightOperand(), entityPath);

    if (left == null && right == null) return null;
    if (left == null) return right;
    if (right == null) return left;

    return switch (node.getOperatorType()) {
      case AND -> left.and(right);
      case OR -> left.or(right);
      default ->
          throw new IllegalArgumentException(
              "Unsupported logical operator: " + node.getOperatorType());
    };
  }

  private BooleanExpression buildComparisonExpression(
      EvaluationOperationNode node, PathBuilder<?> entityPath) {
    OperatorType operatorType = node.getOperatorType();

    // Строим левый операнд
    Expression<?> left = buildLeftOperand(node, entityPath);

    // Обработка унарных операторов
    if (operatorType == OperatorType.IS_NULL) {
      return Expressions.predicate(Ops.IS_NULL, left);
    }
    if (operatorType == OperatorType.IS_NOT_NULL) {
      return Expressions.predicate(Ops.IS_NOT_NULL, left);
    }

    // Обработка IN/NOT_IN
    if (operatorType == OperatorType.IN || operatorType == OperatorType.NOT_IN) {
      if (!(node.getRightOperand() instanceof ValueConstantNode valueNode)) {
        throw new IllegalArgumentException("IN operator requires VALUE_CONSTANT as right operand");
      }
      return buildInExpression(left, valueNode.getValues(), operatorType == OperatorType.NOT_IN);
    }

    // Обработка бинарных операторов сравнения
    return buildBinaryComparison(operatorType, left, node, entityPath);
  }

  private Expression<?> buildLeftOperand(EvaluationOperationNode node, PathBuilder<?> entityPath) {
    // Приоритет: pathExpression (если есть в старом дизайне), затем leftOperand
    // В новом дизайне leftOperand всегда должен быть заполнен
    if (node.getLeftOperand() != null) {
      return buildAnyExpression(node.getLeftOperand(), entityPath);
    }
    throw new IllegalArgumentException("No left operand found for comparison operator");
  }

  private BooleanExpression buildBinaryComparison(
      OperatorType operatorType,
      Expression<?> left,
      EvaluationOperationNode node,
      PathBuilder<?> entityPath) {
    Expression<?> right = buildAnyExpression(node.getRightOperand(), entityPath);
    if (right == null) {
      throw new IllegalArgumentException("Right operand required for operator: " + operatorType);
    }

    // Если левый операнд - результат DISTANCE_SPHERE (NumberTemplate), обрабатываем как числовое
    // сравнение
    if (left instanceof NumberTemplate) {
      return buildNumericComparison(operatorType, (NumberTemplate<Double>) left, right);
    }

    return switch (operatorType) {
      case EQ -> Expressions.predicate(Ops.EQ, left, right);
      case NE -> Expressions.predicate(Ops.NE, left, right);
      case GT -> Expressions.predicate(Ops.GT, left, right);
      case LT -> Expressions.predicate(Ops.LT, left, right);
      case GOE -> Expressions.predicate(Ops.GOE, left, right);
      case LOE -> Expressions.predicate(Ops.LOE, left, right);
      case LIKE -> Expressions.predicate(Ops.LIKE, left, right);
      case STARTS_WITH -> Expressions.predicate(Ops.STARTS_WITH, left, right);
      case ENDS_WITH -> Expressions.predicate(Ops.ENDS_WITH, left, right);
      case CONTAINS -> Expressions.predicate(Ops.STRING_CONTAINS, left, right);
      default ->
          throw new IllegalArgumentException("Unsupported comparison operator: " + operatorType);
    };
  }

  private BooleanExpression buildNumericComparison(
      OperatorType operatorType, NumberTemplate<Double> left, Expression<?> right) {
    Double rightValue = extractDoubleValue(right);
    if (rightValue == null) {
      throw new IllegalArgumentException("Numeric operator requires numeric right operand");
    }

    return switch (operatorType) {
      case EQ -> left.eq(rightValue);
      case NE -> left.ne(rightValue);
      case GT -> left.gt(rightValue);
      case LT -> left.lt(rightValue);
      case GOE -> left.goe(rightValue);
      case LOE -> left.loe(rightValue);
      default ->
          throw new IllegalArgumentException("Unsupported numeric operator: " + operatorType);
    };
  }

  private BooleanExpression buildInExpression(
      Expression<?> left, Set<PredicateNodeValue> values, boolean negate) {
    if (values == null || values.isEmpty()) {
      throw new IllegalArgumentException("IN operator requires at least one value");
    }

    List<Expression<?>> expressions =
        values.stream()
            .map(value -> constantBuilder.buildConstant(value))
            .collect(Collectors.toList());

    BooleanExpression inExpr =
        Expressions.predicate(
            Ops.IN, left, Expressions.list(expressions.toArray(new Expression[0])));
    return negate ? inExpr.not() : inExpr;
  }

  private Double extractDoubleValue(Expression<?> doubleExpression) {
    if (doubleExpression instanceof ConstantImpl) {
      Object value = ((ConstantImpl) doubleExpression).getConstant();
      if (value instanceof Double) {
        return (Double) value;
      } else if (value instanceof Number) {
        return ((Number) value).doubleValue();
      }
    }
    return null;
  }
}

@Component
class ConstantBuilder {

  @SneakyThrows
  public Expression<?> buildConstant(ValueConstantNode valueNode) {
    if (valueNode == null) return null;

    // Для узлов с множественными значениями (IN)
    if (valueNode.getValues() != null && !valueNode.getValues().isEmpty()) {
      throw new IllegalArgumentException("Multiple values should be handled in IN expression");
    }

    return buildConstant(valueNode.getValue());
  }

  @SneakyThrows
  public Expression<?> buildConstant(PredicateNodeValue value) {
    if (value == null) return null;

    return switch (value.getValueType()) {
      case STRING -> Expressions.constant(value.getStringValue());
      case BOOLEAN -> Expressions.constant(value.getBooleanValue());
      case INTEGER -> Expressions.constant(value.getIntegerValue());
      case DOUBLE -> Expressions.constant(value.getDoubleValue());
      case OFFSET_DATE_TIME -> Expressions.constant(value.getOffsetDateTimeValue());
      case POINT -> Expressions.constant(value.getPointValue());
      case ENUM -> buildEnumConstant(value);
      default ->
          throw new IllegalArgumentException("Unsupported value type: " + value.getValueType());
    };
  }

  @SneakyThrows
  private Expression<?> buildEnumConstant(PredicateNodeValue value) {
    MetaEnumValue enumValue = value.getEnumValue();
    if (enumValue == null) {
      throw new IllegalArgumentException("Enum value cannot be null");
    }
    Class<?> enumClass = Class.forName(enumValue.getMetaEnum().getClassName());
    Method valueOfMethod = enumClass.getMethod("valueOf", String.class);
    Object enumConstant = valueOfMethod.invoke(null, enumValue.getName());
    return Expressions.constant(enumConstant);
  }
}

@Component
@RequiredArgsConstructor
class ExpressionBuilder {

  private final EntityClassResolver entityClassResolver;
  private final SpatialTemplateHelper spatialTemplateHelper;
  private final ConstantBuilder constantBuilder;

  // Существующие методы остаются в основном без изменений...
  public Expression<?> buildPathExpression(
      PredicatePathExpression pathExpression, PathBuilder<?> entityPath) {
    if (pathExpression == null) return null;

    Expression<?> currentExpression = entityPath;

    // Обрабатываем цепочку атрибутов
    for (MetaAttribute pathAttribute : pathExpression.getPathAttributes()) {
      currentExpression = buildPathStep(currentExpression, pathAttribute);
    }
    // Обрабатываем корневой атрибут
    MetaAttribute rootAttribute = pathExpression.getFinalPathAttribute();
    currentExpression = buildPathStep(currentExpression, rootAttribute);

    return currentExpression;
  }

  private Expression<?> buildPathStep(Expression<?> currentExpression, MetaAttribute attribute) {
    if (attribute.getAttributeCategory() == AttributeCategory.ENTITY) {
      Class<?> targetClass = entityClassResolver.resolve(attribute.getAttributeEntityType());

      if (currentExpression instanceof PathBuilder<?> pathBuilder) {
        return pathBuilder.get(attribute.getName(), targetClass);
      } else {
        // Для уже построенных путей создаем новый PathBuilder
        String currentPath = currentExpression.toString();
        return new PathBuilder<>(targetClass, currentPath + "." + attribute.getName());
      }
    } else {
      return createTypedExpressionFromParent(currentExpression, attribute);
    }
  }

  private Expression<?> createTypedExpressionFromParent(
      Expression<?> parentExpression, MetaAttribute attribute) {
    String fullPath = parentExpression.toString() + "." + attribute.getName();

    return switch (attribute.getBasicType()) {
      case STRING -> Expressions.stringPath(fullPath);
      case BOOLEAN -> Expressions.booleanPath(fullPath);
      case INTEGER -> Expressions.numberPath(Integer.class, fullPath);
      case DOUBLE -> Expressions.numberPath(Double.class, fullPath);
      case OFFSET_DATE_TIME -> Expressions.dateTimePath(OffsetDateTime.class, fullPath);
      case ENUM -> Expressions.stringPath(fullPath);
      case POINT -> Expressions.comparablePath(Point.class, fullPath);
      default ->
          throw new IllegalArgumentException("Unsupported basic type: " + attribute.getBasicType());
    };
  }

  // Добавляем метод для построения выражения DISTANCE_SPHERE
  public NumberTemplate<Double> buildDistanceSphereExpression(
      EvaluationOperationNode node, PathBuilder<?> entityPath) {
    Expression<?> leftGeometry = buildAnyExpression(node.getLeftOperand(), entityPath);
    Expression<?> rightPoint = buildAnyExpression(node.getRightOperand(), entityPath);

    if (!(leftGeometry instanceof ComparablePath)) {
      throw new IllegalArgumentException(
          "DISTANCE_SPHERE requires geometry attribute as left operand");
    }

    Point targetPoint = extractPointValue(rightPoint);
    if (targetPoint == null) {
      throw new IllegalArgumentException("DISTANCE_SPHERE requires POINT as right operand");
    }

    ComparablePath<Point> geometryPath = (ComparablePath<Point>) leftGeometry;
    return spatialTemplateHelper.distanceSphere(geometryPath, targetPoint);
  }

  private Expression<?> buildAnyExpression(PredicateNode node, PathBuilder<?> entityPath) {
    if (node == null) return null;

    if (node instanceof ValueConstantNode valueNode) {
      return constantBuilder.buildConstant(valueNode);
    } else if (node instanceof PathExpressionNode pathNode) {
      return buildPathExpression(pathNode.getPathExpression(), entityPath);
    } else if (node instanceof EvaluationOperationNode evalNode) {
      if (evalNode.getOperatorType() == OperatorType.DISTANCE_SPHERE) {
        return buildDistanceSphereExpression(evalNode, entityPath);
      } else {
        // Для других операций возвращаем левый операнд
        return buildAnyExpression(evalNode.getLeftOperand(), entityPath);
      }
    } else {
      throw new IllegalArgumentException(
          "Unsupported operand node type: " + node.getClass().getSimpleName());
    }
  }

  private Point extractPointValue(Expression<?> pointExpression) {
    if (pointExpression instanceof ConstantImpl) {
      Object value = ((ConstantImpl) pointExpression).getConstant();
      if (value instanceof Point) {
        return (Point) value;
      }
    }
    return null;
  }
}

// EntityClassResolver.java
@Component
class EntityClassResolver {
  @SneakyThrows
  public Class<?> resolve(MetaEntity metaEntity) {
    if (metaEntity == null) {
      throw new IllegalArgumentException("MetaEntity cannot be null");
    }
    return Class.forName(metaEntity.getName());
  }
}
