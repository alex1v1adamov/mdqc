package example.service.generator;

import com.querydsl.core.types.*;
import com.querydsl.core.types.dsl.BooleanExpression;
import example.models.meta.*;
import example.models.predicate.*;
import example.repo.*;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.locationtech.jts.geom.Point;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PredicateReverseGeneratorService {

  private final MetaEntityResolver metaEntityResolver;
  private final MetaAttributeResolver metaAttributeResolver;
  private final MetaEnumResolver metaEnumResolver;

  public PredicateDefinition generateFromQueryDsl(Predicate predicate, MetaEntity rootEntity) {
    Objects.requireNonNull(predicate, "Predicate cannot be null");
    Objects.requireNonNull(rootEntity, "Root entity cannot be null");

    PredicateDefinition definition = new PredicateDefinition();
    definition.setId(UUID.randomUUID());
    definition.setName("GeneratedFromQueryDsl_" + System.currentTimeMillis());
    definition.setMetaEntity(rootEntity);

    PredicateNode rootNode = convertExpression(predicate, rootEntity);
    definition.setRootNode(rootNode);

    return definition;
  }

  public PredicateDefinition generateFromQueryDsl(Predicate predicate, Class<?> entityClass) {
    MetaEntity metaEntity = metaEntityResolver.resolveByClass(entityClass);
    return generateFromQueryDsl(predicate, metaEntity);
  }

  private PredicateNode convertExpression(Expression<?> expression, MetaEntity currentEntity) {
    Objects.requireNonNull(expression, "Expression cannot be null");

    ExpressionVisitor visitor = new ExpressionVisitor(currentEntity);
    return expression.accept(visitor, null);
  }

  private PredicateNode convertBooleanExpression(
      BooleanExpression expression, MetaEntity currentEntity) {
    ExpressionVisitor visitor = new ExpressionVisitor(currentEntity);
    return expression.accept(visitor, null);
  }

  @RequiredArgsConstructor
  private class ExpressionVisitor implements Visitor<PredicateNode, Void> {

    private final MetaEntity currentEntity;

      @Override
      public PredicateNode visit(Operation<?> operation, Void context) {
          Operator operator = operation.getOperator();
          List<? extends Expression<?>> args = operation.getArgs();

          // Проверяем, является ли это сравнением с DISTANCE_SPHERE
          if (isSpatialComparison(operation)) {
              return createSpatialComparison(operation);
          }

          return switch (operator) {
              case Ops.AND, Ops.OR -> createLogicalOperation(operator, args);
              case Ops.NOT -> createNotOperation(args);
              case Ops.EQ, Ops.NE, Ops.GT, Ops.LT, Ops.GOE, Ops.LOE -> createComparisonOperation(operator, args);
              case Ops.IS_NULL, Ops.IS_NOT_NULL -> createNullCheckOperation(operator, args);
              case Ops.LIKE, Ops.STARTS_WITH, Ops.ENDS_WITH, Ops.STRING_CONTAINS -> createStringOperation(operator, args);
              case Ops.IN, Ops.NOT_IN -> createInOperation(operator, args);
              default -> throw new UnsupportedOperationException("Operator not supported: " + operator);
          };
      }

      private boolean isSpatialComparison(Operation<?> operation) {
          List<? extends Expression<?>> args = operation.getArgs();
          if (args.size() != 2) return false;

          // Проверяем, содержит ли один из аргументов пространственную функцию
          return args.stream().anyMatch(this::isSpatialExpression);
      }

      private boolean isSpatialExpression(Expression<?> expr) {
          if (expr instanceof TemplateExpression) {
              String template = ((TemplateExpression<?>) expr).getTemplate().toString();
              return template.contains("ST_DistanceSphere") ||
                      template.contains("ST_Within") ||
                      template.contains("ST_Intersects") ||
                      template.contains("ST_DWithin");
          }
          return false;
      }

      private PredicateNode createSpatialComparison(Operation<?> operation) {
          List<?> rawArgs = operation.getArgs();
          Operator operator = operation.getOperator();

          // Преобразуем к List<Expression<?>>
          List<Expression<?>> args = rawArgs.stream()
                  .filter(arg -> arg instanceof Expression)
                  .map(arg -> (Expression<?>) arg)
                  .collect(Collectors.toList());

          if (args.size() != 2) {
              throw new IllegalArgumentException("Spatial comparison requires exactly 2 arguments");
          }

          // Находим пространственное выражение и значение для сравнения
          Expression<?> spatialExpr = args.stream()
                  .filter(this::isSpatialExpression)
                  .findFirst()
                  .orElseThrow(() -> new IllegalArgumentException("No spatial expression found"));

          Expression<?> comparisonValue = args.stream()
                  .filter(arg -> !isSpatialExpression(arg))
                  .findFirst()
                  .orElseThrow(() -> new IllegalArgumentException("No comparison value found for spatial operation"));

          // Конвертируем пространственное выражение
          PredicateNode spatialNode = convertExpression(spatialExpr, currentEntity);

          // Если spatialNode - это DISTANCE_SPHERE, создаем сравнение
          if (spatialNode instanceof EvaluationOperationNode evalNode &&
                  evalNode.getOperatorType() == OperatorType.DISTANCE_SPHERE) {

              // Создаем сравнение (например, DISTANCE_SPHERE < 1000)
              EvaluationOperationNode comparisonNode = createEvaluationOperationNode(convertComparisonOperator(operator));
              comparisonNode.setLeftOperand(spatialNode);
              comparisonNode.setRightOperand(convertExpression(comparisonValue, currentEntity));

              return comparisonNode;
          }

          throw new UnsupportedOperationException("Complex spatial comparison not supported: " + operation);
      }

    @Override
    public PredicateNode visit(Path<?> path, Void context) {
      return createPathExpressionNode(path);
    }

    @Override
    public PredicateNode visit(Constant<?> constant, Void context) {
      return createValueConstantNode(constant);
    }

    @Override
    public PredicateNode visit(FactoryExpression<?> expr, Void context) {
      throw new UnsupportedOperationException("FactoryExpression not supported: " + expr);
    }

    @Override
    public PredicateNode visit(ParamExpression<?> expr, Void context) {
      throw new UnsupportedOperationException("ParamExpression not supported: " + expr);
    }

    @Override
    public PredicateNode visit(SubQueryExpression<?> expr, Void context) {
      throw new UnsupportedOperationException("SubQueryExpression not supported: " + expr);
    }

      @Override
      public PredicateNode visit(TemplateExpression<?> expr, Void context) {
          String template = expr.getTemplate().toString();

          // Обработка DISTANCE_SPHERE из SpatialTemplateHelper
          if (template.contains("ST_DistanceSphere")) {
              return createDistanceSphereOperation(expr);
          }

          // Обработка других пространственных функций
          if (template.contains("ST_Within")) {
              return createSpatialOperation(expr, "WITHIN");
          }

          if (template.contains("ST_Intersects")) {
              return createSpatialOperation(expr, "INTERSECTS");
          }

          if (template.contains("ST_DWithin")) {
              return createSpatialOperation(expr, "DWITHIN");
          }

          throw new UnsupportedOperationException("TemplateExpression not supported: " + expr);
      }

      private PredicateNode createDistanceSphereOperation(TemplateExpression<?> expr) {
          // Явное приведение типа для args
          @SuppressWarnings("unchecked")
          List<Expression<?>> args = (List<Expression<?>>) (List<?>) expr.getArgs();

          if (args.size() != 2) {
              throw new IllegalArgumentException("DISTANCE_SPHERE requires exactly 2 arguments");
          }

          // Создаем узел для DISTANCE_SPHERE операции
          EvaluationOperationNode operationNode = createEvaluationOperationNode(OperatorType.DISTANCE_SPHERE);
          operationNode.setLeftOperand(convertExpression(args.get(0), currentEntity));
          operationNode.setRightOperand(convertExpression(args.get(1), currentEntity));

          return operationNode;
      }

      private PredicateNode createSpatialOperation(TemplateExpression<?> expr, String operationType) {
          // Для простоты пока выбросим исключение, но можно добавить поддержку
          throw new UnsupportedOperationException("Spatial operation " + operationType + " not yet supported: " + expr);
      }

    private PredicateNode createLogicalOperation(
        Operator operator, List<? extends Expression<?>> args) {
      OperatorType operatorType =
          switch (operator) {
            case Ops.AND -> OperatorType.AND;
            case Ops.OR -> OperatorType.OR;
            default ->
                throw new IllegalArgumentException("Unsupported logical operator: " + operator);
          };

      EvaluationOperationNode operationNode = createEvaluationOperationNode(operatorType);
      operationNode.setLeftOperand(convertExpression(args.get(0), currentEntity));
      operationNode.setRightOperand(convertExpression(args.get(1), currentEntity));

      return operationNode;
    }

    private PredicateNode createNotOperation(List<? extends Expression<?>> args) {
      EvaluationOperationNode operationNode = createEvaluationOperationNode(OperatorType.NOT);
      operationNode.setLeftOperand(convertExpression(args.get(0), currentEntity));
      return operationNode;
    }

    private PredicateNode createComparisonOperation(
        Operator operator, List<? extends Expression<?>> args) {
      OperatorType operatorType = convertComparisonOperator(operator);
      EvaluationOperationNode operationNode = createEvaluationOperationNode(operatorType);

      operationNode.setLeftOperand(convertExpression(args.get(0), currentEntity));
      if (args.size() > 1) {
        operationNode.setRightOperand(convertExpression(args.get(1), currentEntity));
      }

      return operationNode;
    }

    private PredicateNode createNullCheckOperation(
        Operator operator, List<? extends Expression<?>> args) {
      OperatorType operatorType =
          operator == Ops.IS_NULL ? OperatorType.IS_NULL : OperatorType.IS_NOT_NULL;
      EvaluationOperationNode operationNode = createEvaluationOperationNode(operatorType);
      operationNode.setLeftOperand(convertExpression(args.get(0), currentEntity));
      return operationNode;
    }

    private PredicateNode createStringOperation(
        Operator operator, List<? extends Expression<?>> args) {
      OperatorType operatorType = convertStringOperator(operator);
      EvaluationOperationNode operationNode = createEvaluationOperationNode(operatorType);
      operationNode.setLeftOperand(convertExpression(args.get(0), currentEntity));
      operationNode.setRightOperand(convertExpression(args.get(1), currentEntity));
      return operationNode;
    }

    private PredicateNode createInOperation(Operator operator, List<? extends Expression<?>> args) {
      OperatorType operatorType = operator == Ops.IN ? OperatorType.IN : OperatorType.NOT_IN;
      EvaluationOperationNode operationNode = createEvaluationOperationNode(operatorType);
      operationNode.setLeftOperand(convertExpression(args.get(0), currentEntity));

      ValueConstantNode valueNode = createInValuesNode(args.get(1));
      operationNode.setRightOperand(valueNode);

      return operationNode;
    }

    private EvaluationOperationNode createEvaluationOperationNode(OperatorType operatorType) {
      EvaluationOperationNode node = new EvaluationOperationNode();
      node.setId(UUID.randomUUID());
      node.setOperatorType(operatorType);
      return node;
    }

    private PathExpressionNode createPathExpressionNode(Path<?> path) {
      String pathString = path.toString();
      String[] pathSegments = pathString.split(Pattern.quote("."));

      PathExpressionNode pathNode = new PathExpressionNode();
      pathNode.setId(UUID.randomUUID());

      PredicatePathExpression pathExpression = new PredicatePathExpression();
      pathExpression.setId(UUID.randomUUID());

      // Определяем конечный атрибут с учетом навигации
      MetaAttribute finalAttribute = resolveFinalAttribute(pathSegments);
      pathExpression.setFinalPathAttribute(finalAttribute);

      // Строим цепочку атрибутов для навигации
      List<MetaAttribute> pathAttributes = resolvePathAttributes(pathSegments);
      pathExpression.setPathAttributes(pathAttributes);

      pathNode.setPathExpression(pathExpression);
      return pathNode;
    }

    private ValueConstantNode createValueConstantNode(Constant<?> constant) {
      ValueConstantNode valueNode = new ValueConstantNode();
      valueNode.setId(UUID.randomUUID());

      PredicateNodeValue nodeValue = createPredicateNodeValue(constant.getConstant());
      valueNode.setValue(nodeValue);

      return valueNode;
    }

    private ValueConstantNode createInValuesNode(Expression<?> expression) {
      ValueConstantNode valueNode = new ValueConstantNode();
      valueNode.setId(UUID.randomUUID());

      Set<PredicateNodeValue> values = extractValuesFromExpression(expression);
      valueNode.setValues(values);

      return valueNode;
    }

    private Set<PredicateNodeValue> extractValuesFromExpression(Expression<?> expression) {
      return switch (expression) {
        case Constant<?> constant -> Set.of(createPredicateNodeValue(constant.getConstant()));
        case Operation<?> op when op.getOperator() == Ops.LIST ->
            op.getArgs().stream()
                .filter(Constant.class::isInstance)
                .map(Constant.class::cast)
                .map(constant -> createPredicateNodeValue(constant.getConstant()))
                .collect(Collectors.toCollection(LinkedHashSet::new));
        default ->
            throw new IllegalArgumentException(
                "Unsupported expression for IN values: " + expression.getClass().getSimpleName());
      };
    }

    private PredicateNodeValue createPredicateNodeValue(Object value) {
      PredicateNodeValue nodeValue = new PredicateNodeValue();
      nodeValue.setId(UUID.randomUUID());

      BasicType basicType = BasicTypeResolver.resolveFromValue(value);
      nodeValue.setValueType(basicType);
      BasicTypeResolver.setValueByType(nodeValue, value, basicType, metaEnumResolver);

      return nodeValue;
    }

    private MetaAttribute resolveFinalAttribute(String[] pathSegments) {
      // Начинаем с текущей сущности
      MetaEntity currentEntity = this.currentEntity;

      // Проходим по всем сегментам пути, кроме последнего
      for (int i = 1; i < pathSegments.length - 1; i++) {
        String segment = pathSegments[i];
        MetaAttribute attribute = metaAttributeResolver.resolveAttribute(currentEntity, segment);

        if (attribute.getAttributeCategory() == AttributeCategory.ENTITY) {
          // Переходим к связанной сущности
          currentEntity = attribute.getAttributeEntityType();
        } else {
          throw new IllegalArgumentException(
              "Path segment '"
                  + segment
                  + "' is not an entity reference in entity: "
                  + currentEntity.getName());
        }
      }

      // Последний сегмент - конечный атрибут
      String finalAttributeName = pathSegments[pathSegments.length - 1];
      return metaAttributeResolver.resolveAttribute(currentEntity, finalAttributeName);
    }

    private List<MetaAttribute> resolvePathAttributes(String[] pathSegments) {
      List<MetaAttribute> attributes = new ArrayList<>();
      MetaEntity currentEntity = this.currentEntity;

      // Проходим по всем сегментам пути, кроме последнего (конечный атрибут) и первого (алиас)
      for (int i = 1; i < pathSegments.length - 1; i++) {
        String segment = pathSegments[i];
        MetaAttribute attribute = metaAttributeResolver.resolveAttribute(currentEntity, segment);

        if (attribute.getAttributeCategory() == AttributeCategory.ENTITY) {
          attributes.add(attribute);
          currentEntity = attribute.getAttributeEntityType();
        } else {
          throw new IllegalArgumentException(
              "Path segment '"
                  + segment
                  + "' is not an entity reference in entity: "
                  + currentEntity.getName());
        }
      }

      return attributes;
    }

    private OperatorType convertComparisonOperator(Operator operator) {
      return switch (operator) {
        case Ops.EQ -> OperatorType.EQ;
        case Ops.NE -> OperatorType.NE;
        case Ops.GT -> OperatorType.GT;
        case Ops.LT -> OperatorType.LT;
        case Ops.GOE -> OperatorType.GOE;
        case Ops.LOE -> OperatorType.LOE;
        default ->
            throw new IllegalArgumentException("Unsupported comparison operator: " + operator);
      };
    }

    private OperatorType convertStringOperator(Operator operator) {
      return switch (operator) {
        case Ops.LIKE -> OperatorType.LIKE;
        case Ops.STARTS_WITH -> OperatorType.STARTS_WITH;
        case Ops.ENDS_WITH -> OperatorType.ENDS_WITH;
        case Ops.STRING_CONTAINS -> OperatorType.CONTAINS;
        default -> throw new IllegalArgumentException("Unsupported string operator: " + operator);
      };
    }
  }

  /** Вспомогательный класс для разрешения BasicType из значений */
  private static class BasicTypeResolver {

      static BasicType resolveFromValue(Object value) {
          if (value == null) return BasicType.STRING;

          return switch (value.getClass().getSimpleName()) {
              case "String" -> BasicType.STRING;
              case "Boolean", "boolean" -> BasicType.BOOLEAN;
              case "Integer", "int", "Long", "long" -> BasicType.INTEGER;
              case "Double", "double", "Float", "float" -> BasicType.DOUBLE;
              case "OffsetDateTime" -> BasicType.OFFSET_DATE_TIME;
              case "Point" -> BasicType.POINT;
              case "NumberTemplate" -> BasicType.DOUBLE; // Для результатов DISTANCE_SPHERE
              default -> value instanceof Enum ? BasicType.ENUM : BasicType.STRING;
          };
      }

    static void setValueByType(
        PredicateNodeValue nodeValue,
        Object value,
        BasicType basicType,
        MetaEnumResolver enumResolver) {
      if (value == null) return;

      switch (basicType) {
        case STRING -> nodeValue.setStringValue((String) value);
        case BOOLEAN -> nodeValue.setBooleanValue((Boolean) value);
        case INTEGER -> setIntegerValue(nodeValue, value);
        case DOUBLE -> nodeValue.setDoubleValue(((Number) value).doubleValue());
        case OFFSET_DATE_TIME -> nodeValue.setOffsetDateTimeValue((OffsetDateTime) value);
        case POINT -> nodeValue.setPointValue((Point) value);
        case ENUM -> {
          MetaEnumValue enumValue = enumResolver.resolveEnumValue((Enum<?>) value);
          nodeValue.setEnumValue(enumValue);
        }
      }
    }

    private static void setIntegerValue(PredicateNodeValue nodeValue, Object value) {
      if (value instanceof Integer) {
        nodeValue.setIntegerValue((Integer) value);
      } else if (value instanceof Long) {
        nodeValue.setLongValue((Long) value);
      } else if (value instanceof Number) {
        nodeValue.setIntegerValue(((Number) value).intValue());
      }
    }
  }
}

@Service
@RequiredArgsConstructor
class MetaEntityResolver {
  private final MetaEntityRepository metaEntityRepository;

  public MetaEntity resolveByClass(Class<?> entityClass) {
    String className = entityClass.getName();
    return metaEntityRepository
        .findOne(QMetaEntity.metaEntity.name.eq(className))
        .orElseThrow(
            () -> new IllegalArgumentException("MetaEntity not found for class: " + className));
  }
}

@Service
@RequiredArgsConstructor
class MetaAttributeResolver {
  private final MetaAttributeRepository metaAttributeRepository;

  public MetaAttribute resolveAttribute(MetaEntity entity, String attributeName) {
    return metaAttributeRepository
        .findOne(
            QMetaAttribute.metaAttribute
                .entity
                .id
                .eq(entity.getId())
                .and(QMetaAttribute.metaAttribute.name.eq(attributeName)))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "MetaAttribute not found: "
                        + attributeName
                        + " in entity: "
                        + entity.getName()));
  }
}

@Service
@RequiredArgsConstructor
class MetaEnumResolver {
  private final MetaEnumValueRepository metaEnumValueRepository;

  public MetaEnumValue resolveEnumValue(Enum<?> enumValue) {
    String enumClassName = enumValue.getDeclaringClass().getName();
    String enumValueName = enumValue.name();

    return metaEnumValueRepository
        .findOne(
            QMetaEnumValue.metaEnumValue
                .metaEnum
                .className
                .eq(enumClassName)
                .and(QMetaEnumValue.metaEnumValue.name.eq(enumValueName)))
        .orElseThrow(
            () ->
                new IllegalArgumentException(
                    "MetaEnumValue not found: " + enumValueName + " in enum: " + enumClassName));
  }
}
