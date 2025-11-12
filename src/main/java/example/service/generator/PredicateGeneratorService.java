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
import example.models.predicate.NodeType;
import example.models.predicate.OperatorType;
import example.models.predicate.PredicateDefinition;
import example.models.predicate.PredicateNode;
import example.models.predicate.PredicateNodeValue;
import example.models.predicate.PredicatePathExpression;
import example.service.SpatialTemplateHelper;
import example.service.validation.PredicateValidationService;
import example.service.validation.ValidationResult;
import java.lang.reflect.Method;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.locationtech.jts.geom.Point;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;
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

// ExpressionResolver.java
@Component
class ExpressionResolver {

  private final List<NodeHandler> handlers;

  public ExpressionResolver(@Lazy List<NodeHandler> handlers) {
    this.handlers = handlers;
  }

  public BooleanExpression buildExpression(PredicateNode node, PathBuilder<?> entityPath) {
    if (node == null) return null;

    return handlers.stream()
        .filter(handler -> handler.supports(node))
        .findFirst()
        .map(handler -> (BooleanExpression) handler.handle(node, entityPath))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "No handler found for node type: " + node.getNodeType()));
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

// NodeHandler.java
interface NodeHandler {
  boolean supports(PredicateNode node);

  Expression<?> handle(PredicateNode node, PathBuilder<?> entityPath);
}

// LogicalOperatorHandler.java
@Component
@RequiredArgsConstructor
class LogicalOperatorHandler implements NodeHandler {

  @Lazy private final ExpressionResolver expressionResolver;

  @Override
  public boolean supports(PredicateNode node) {
    return node.getNodeType() == NodeType.LOGICAL_OPERATOR;
  }

  @Override
  public BooleanExpression handle(PredicateNode node, PathBuilder<?> entityPath) {
    BooleanExpression left = expressionResolver.buildExpression(node.getLeftOperand(), entityPath);

    if (node.getOperatorType() == OperatorType.NOT) {
      return left != null ? left.not() : null;
    }

    BooleanExpression right =
        expressionResolver.buildExpression(node.getRightOperand(), entityPath);

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
}

// ComparisonOperatorHandler.java
@Component
@RequiredArgsConstructor
class ComparisonOperatorHandler implements NodeHandler {

  private final ExpressionBuilder expressionBuilder;
  private final OperatorProcessor operatorProcessor;

  @Override
  public boolean supports(PredicateNode node) {
    return node.getNodeType() == NodeType.COMPARISON_OPERATOR;
  }

  @Override
  public Expression<?> handle(PredicateNode node, PathBuilder<?> entityPath) {
    return operatorProcessor.process(node.getOperatorType(), node, entityPath);
  }
}

// ExpressionBuilder.java
@Component
@RequiredArgsConstructor
class ExpressionBuilder {

  private final EntityClassResolver entityClassResolver;
  private final ConstantBuilder constantBuilder;
  private final SpatialTemplateHelper spatialTemplateHelper;
  @Lazy private final ExpressionResolver expressionResolver;

  public Expression<?> buildLeftOperand(PredicateNode node, PathBuilder<?> entityPath) {
    // ВСЕГДА используем pathExpression - убрана проверка на metaAttribute
    if (node.getPathExpression() != null) {
      return buildPathExpression(node.getPathExpression(), entityPath);
    }
    if (node.getLeftOperand() != null) {
      return buildOperandExpression(node.getLeftOperand(), entityPath);
    }
    throw new IllegalArgumentException("No left operand found for comparison operator");
  }

  public Expression<?> buildOperandExpression(PredicateNode node, PathBuilder<?> entityPath) {
    if (node == null) return null;

    return switch (node.getNodeType()) {
      case VALUE_CONSTANT -> constantBuilder.buildConstant(node.getValue());
      case PATH_EXPRESSION -> buildPathExpression(node.getPathExpression(), entityPath);
      case COMPARISON_OPERATOR -> {
        if (node.getOperatorType() == OperatorType.DISTANCE_SPHERE) {
          yield buildDistanceSphereExpression(node, entityPath);
        } else {
          yield buildLeftOperand(node, entityPath);
        }
      }
      case LOGICAL_OPERATOR -> expressionResolver.buildExpression(node, entityPath);
      default ->
          throw new IllegalArgumentException(
              "Unsupported operand node type: " + node.getNodeType());
    };
  }

  private Expression<?> buildPathExpression(
      PredicatePathExpression pathExpression, PathBuilder<?> entityPath) {
    if (pathExpression == null) return null;

    Expression<?> currentExpression = entityPath;

    // Обрабатываем корневой атрибут
    MetaAttribute rootAttribute = pathExpression.getRootAttribute();
    currentExpression = buildPathStep(currentExpression, rootAttribute);

    // Обрабатываем цепочку атрибутов
    for (MetaAttribute pathAttribute : pathExpression.getPathAttributes()) {
      currentExpression = buildPathStep(currentExpression, pathAttribute);
    }

    return currentExpression;
  }

  private Expression<?> buildPathStep(Expression<?> currentExpression, MetaAttribute attribute) {
    if (attribute.getAttributeCategory() == AttributeCategory.ENTITY) {
      Class<?> targetClass = entityClassResolver.resolve(attribute.getAttributeEntityType());

      if (currentExpression instanceof PathBuilder<?> pathBuilder) {
        return new PathBuilder<>(targetClass, String.valueOf(pathBuilder.get(attribute.getName())));
      } else {
        String path = currentExpression.toString() + "." + attribute.getName();
        return new PathBuilder<>(targetClass, path);
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

  NumberTemplate<Double> buildDistanceSphereExpression(
      PredicateNode node, PathBuilder<?> entityPath) {
    // DISTANCE_SPHERE - бинарный оператор с двумя POINT операндами
    Expression<?> leftGeometry = buildLeftOperand(node, entityPath);
    Expression<?> rightPoint = buildOperandExpression(node.getRightOperand(), entityPath);

    if (!(leftGeometry instanceof ComparablePath)) {
      throw new IllegalArgumentException(
          "DISTANCE_SPHERE requires geometry attribute as left operand");
    }

    Point targetPoint = extractPointValue(rightPoint);
    if (targetPoint == null) {
      throw new IllegalArgumentException("DISTANCE_SPHERE requires POINT as right operand");
    }

    ComparablePath<Point> geometryPath = (ComparablePath<Point>) leftGeometry;

    // Создаем выражение расстояния (возвращает NumberTemplate<Double>)
    return spatialTemplateHelper.distanceSphere(geometryPath, targetPoint);
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

// OperatorProcessor.java
@Component
@RequiredArgsConstructor
class OperatorProcessor {

  private final ExpressionBuilder expressionBuilder;
  private final ConstantBuilder constantBuilder;

  public BooleanExpression process(
      OperatorType operatorType, PredicateNode node, PathBuilder<?> entityPath) {
    // Специальная обработка DISTANCE_SPHERE - он возвращает числовое выражение
    if (operatorType == OperatorType.DISTANCE_SPHERE) {
      NumberTemplate<Double> distanceExpr =
          expressionBuilder.buildDistanceSphereExpression(node, entityPath);
      // DISTANCE_SPHERE сам по себе не создает BooleanExpression, возвращаем базовую проверку
      return distanceExpr.gt(0.0);
    }

    // Для остальных операторов строим левый операнд
    Expression<?> left = expressionBuilder.buildLeftOperand(node, entityPath);

    return switch (operatorType) {
      case IS_NULL -> Expressions.predicate(Ops.IS_NULL, left);
      case IS_NOT_NULL -> Expressions.predicate(Ops.IS_NOT_NULL, left);
      case IN, NOT_IN ->
          buildInExpression(left, node.getInValues(), operatorType == OperatorType.NOT_IN);
      case BETWEEN -> buildBetweenExpression(left, node);
      default -> buildBinaryExpression(operatorType, left, node, entityPath);
    };
  }

  private BooleanExpression buildBinaryExpression(
      OperatorType operatorType,
      Expression<?> left,
      PredicateNode node,
      PathBuilder<?> entityPath) {
    Expression<?> right =
        expressionBuilder.buildOperandExpression(node.getRightOperand(), entityPath);
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
      default -> throw new IllegalArgumentException("Unsupported operator: " + operatorType);
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

  private BooleanExpression buildInExpression(
      Expression<?> left, List<PredicateNodeValue> inValues, boolean negate) {
    if (inValues == null || inValues.isEmpty()) {
      throw new IllegalArgumentException("IN operator requires at least one value");
    }

    List<Expression<?>> expressions =
        inValues.stream().map(constantBuilder::buildConstant).collect(Collectors.toList());

    BooleanExpression inExpr =
        Expressions.predicate(
            Ops.IN, left, Expressions.list(expressions.toArray(new Expression[0])));
    return negate ? inExpr.not() : inExpr;
  }

  private BooleanExpression buildBetweenExpression(Expression<?> left, PredicateNode node) {
    if (node.getInValues() == null || node.getInValues().size() != 2) {
      throw new IllegalArgumentException(
          "BETWEEN operator requires exactly two values in inValues list");
    }

    Expression<?> fromValue = constantBuilder.buildConstant(node.getInValues().get(0));
    Expression<?> toValue = constantBuilder.buildConstant(node.getInValues().get(1));

    if (fromValue == null || toValue == null) {
      throw new IllegalArgumentException("BETWEEN operator requires both from and to values");
    }

    return Expressions.predicate(Ops.BETWEEN, left, fromValue, toValue);
  }
}

// ConstantBuilder.java
@Component
class ConstantBuilder {

  @SneakyThrows
  public Expression<?> buildConstant(PredicateNodeValue value) {
    if (value == null) return null;

    Object valueObj = value.getValue();
    if (valueObj == null) return null;

    return switch (value.getValueType()) {
      case STRING, BOOLEAN, INTEGER, DOUBLE -> Expressions.constant(valueObj);
      case OFFSET_DATE_TIME -> Expressions.constant(valueObj);
      case POINT -> Expressions.constant(valueObj);
      case ENUM -> buildEnumConstant(value);
      default ->
          throw new IllegalArgumentException("Unsupported value type: " + value.getValueType());
    };
  }

  @SneakyThrows
  private Expression<?> buildEnumConstant(PredicateNodeValue value) {
    MetaEnumValue enumValue = (MetaEnumValue) value.getValue();
    Class<?> enumClass = Class.forName(enumValue.getMetaEnum().getClassName());
    Method valueOfMethod = enumClass.getMethod("valueOf", String.class);
    Object enumConstant = valueOfMethod.invoke(null, enumValue.getName());
    return Expressions.constant(enumConstant);
  }
}

// PathExpressionHandler.java
@Component
@RequiredArgsConstructor
class PathExpressionHandler implements NodeHandler {

  private final ExpressionBuilder expressionBuilder;

  @Override
  public boolean supports(PredicateNode node) {
    return node.getNodeType() == NodeType.PATH_EXPRESSION;
  }

  @Override
  public Expression<?> handle(PredicateNode node, PathBuilder<?> entityPath) {
    Expression<?> pathExpression = expressionBuilder.buildOperandExpression(node, entityPath);
    return pathExpression != null ? Expressions.predicate(Ops.IS_NOT_NULL, pathExpression) : null;
  }
}

// ValueConstantHandler.java
@Component
class ValueConstantHandler implements NodeHandler {

  @Override
  public boolean supports(PredicateNode node) {
    return node.getNodeType() == NodeType.VALUE_CONSTANT;
  }

  @Override
  public Expression<?> handle(PredicateNode node, PathBuilder<?> entityPath) {
    throw new IllegalArgumentException("VALUE_CONSTANT cannot be used as standalone predicate");
  }
}

// DistanceSphereExpressionHandler.java
@Component
@RequiredArgsConstructor
class DistanceSphereExpressionHandler implements NodeHandler {

  private final ExpressionBuilder expressionBuilder;

  @Override
  public boolean supports(PredicateNode node) {
    // Обрабатываем COMPARISON_OPERATOR, где левый операнд - DISTANCE_SPHERE
    return node.getNodeType() == NodeType.COMPARISON_OPERATOR
        && node.getLeftOperand() != null
        && node.getLeftOperand().getNodeType() == NodeType.COMPARISON_OPERATOR
        && node.getLeftOperand().getOperatorType() == OperatorType.DISTANCE_SPHERE;
  }

  @Override
  public BooleanExpression handle(PredicateNode node, PathBuilder<?> entityPath) {
    // Строим выражение DISTANCE_SPHERE
    NumberTemplate<Double> distanceExpr =
        expressionBuilder.buildDistanceSphereExpression(node.getLeftOperand(), entityPath);

    // Получаем правый операнд для сравнения
    Expression<?> right =
        expressionBuilder.buildOperandExpression(node.getRightOperand(), entityPath);
    if (right == null) {
      throw new IllegalArgumentException("Right operand required for distance comparison");
    }

    // Извлекаем числовое значение
    Double rightValue = extractDoubleValue(right);
    if (rightValue == null) {
      throw new IllegalArgumentException("Distance comparison requires numeric right operand");
    }

    // Строим сравнение расстояния
    return switch (node.getOperatorType()) {
      case GT -> distanceExpr.gt(rightValue);
      case LT -> distanceExpr.lt(rightValue);
      case GOE -> distanceExpr.goe(rightValue);
      case LOE -> distanceExpr.loe(rightValue);
      case EQ -> distanceExpr.eq(rightValue);
      case NE -> distanceExpr.ne(rightValue);
      default ->
          throw new IllegalArgumentException(
              "Unsupported operator for distance comparison: " + node.getOperatorType());
    };
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

// HandlerConfiguration.java
@Component
@RequiredArgsConstructor
class HandlerConfiguration {

  private final LogicalOperatorHandler logicalOperatorHandler;
  private final ComparisonOperatorHandler comparisonOperatorHandler;
  private final PathExpressionHandler pathExpressionHandler;
  private final ValueConstantHandler valueConstantHandler;
  private final DistanceSphereExpressionHandler distanceSphereExpressionHandler;

  @Bean
  public List<NodeHandler> nodeHandlers() {
    return List.of(
        distanceSphereExpressionHandler, // Должен быть первым для приоритетной обработки
        logicalOperatorHandler,
        comparisonOperatorHandler,
        pathExpressionHandler,
        valueConstantHandler);
  }
}
