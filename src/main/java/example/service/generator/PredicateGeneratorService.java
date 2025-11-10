package example.service.generator;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Ops;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
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
import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PredicateGeneratorService {
    
    private final List<NodeHandler> handlers;
    private final EntityClassResolver entityClassResolver;
    
    public BooleanExpression generatePredicate(PredicateDefinition predicateDefinition) {
        Class<?> entityClass = entityClassResolver.resolve(predicateDefinition.getMetaEntity());
        PathBuilder<?> entityPath = new PathBuilder<>(entityClass, "entity");
        return buildExpression(predicateDefinition.getRootNode(), entityPath);
    }
    
    public BooleanExpression buildExpression(PredicateNode node, PathBuilder<?> entityPath) {
        if (node == null) return null;
        
        return handlers.stream()
            .filter(handler -> handler.supports(node))
            .findFirst()
            .map(handler -> (BooleanExpression) handler.handle(node, entityPath))
            .orElseThrow(() -> new IllegalArgumentException("No handler found for node type: " + node.getNodeType()));
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

    private final ExpressionBuilder expressionBuilder;
    private final OperatorProcessor operatorProcessor;

    @Override
    public boolean supports(PredicateNode node) {
        return node.getNodeType() == NodeType.LOGICAL_OPERATOR;
    }

    @Override
    public BooleanExpression handle(PredicateNode node, PathBuilder<?> entityPath) {
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
            default -> throw new IllegalArgumentException("Unsupported logical operator: " + node.getOperatorType());
        };
    }

    private BooleanExpression buildExpression(PredicateNode node, PathBuilder<?> entityPath) {
        if (node == null) return null;

        // Простая реализация без рекурсивной зависимости
        return switch (node.getNodeType()) {
            case LOGICAL_OPERATOR -> handle(node, entityPath);
            case COMPARISON_OPERATOR -> (BooleanExpression) operatorProcessor.process(
                    node.getOperatorType(),
                    expressionBuilder.buildLeftOperand(node, entityPath),
                    node
            );
            case PATH_EXPRESSION -> {
                Expression<?> pathExpr = expressionBuilder.buildOperandExpression(node, entityPath);
                yield pathExpr != null ? Expressions.predicate(Ops.IS_NOT_NULL, pathExpr) : null;
            }
            case VALUE_CONSTANT ->
                    throw new IllegalArgumentException("VALUE_CONSTANT cannot be used as standalone predicate");
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
        Expression<?> left = expressionBuilder.buildLeftOperand(node, entityPath);
        return operatorProcessor.process(node.getOperatorType(), left, node);
    }
}

// ExpressionBuilder.java
@Component
class ExpressionBuilder {
    
    private final EntityClassResolver entityClassResolver;
    private final ConstantBuilder constantBuilder;
    
    @Autowired
    public ExpressionBuilder(EntityClassResolver entityClassResolver, ConstantBuilder constantBuilder) {
        this.entityClassResolver = entityClassResolver;
        this.constantBuilder = constantBuilder;
    }
    
    public Expression<?> buildLeftOperand(PredicateNode node, PathBuilder<?> entityPath) {
        if (node.getMetaAttribute() != null) {
            return createTypedExpression(entityPath, node.getMetaAttribute());
        }
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
            case COMPARISON_OPERATOR -> buildLeftOperand(node, entityPath);
            default -> throw new IllegalArgumentException("Unsupported operand node type: " + node.getNodeType());
        };
    }
    
    private Expression<?> buildPathExpression(PredicatePathExpression pathExpression, PathBuilder<?> entityPath) {
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
    
    private Expression<?> createTypedExpressionFromParent(Expression<?> parentExpression, MetaAttribute attribute) {
        String fullPath = parentExpression.toString() + "." + attribute.getName();

        return switch (attribute.getBasicType()) {
            case STRING -> Expressions.stringPath(fullPath);
            case BOOLEAN -> Expressions.booleanPath(fullPath);
            case INTEGER -> Expressions.numberPath(Integer.class, fullPath);
            case DOUBLE -> Expressions.numberPath(Double.class, fullPath);
            case OFFSET_DATE_TIME -> Expressions.datePath(LocalDate.class, fullPath);
            case ENUM -> Expressions.stringPath(fullPath);
        };
    }
    
    private Expression<?> createTypedExpression(PathBuilder<?> pathBuilder, MetaAttribute metaAttribute) {
        String attributeName = metaAttribute.getName();

        if (metaAttribute.getAttributeCategory() == AttributeCategory.BASIC) {
            return switch (metaAttribute.getBasicType()) {
                case STRING -> pathBuilder.getString(attributeName);
                case BOOLEAN -> pathBuilder.getBoolean(attributeName);
                case INTEGER -> pathBuilder.getNumber(attributeName, Integer.class);
                case DOUBLE -> pathBuilder.getNumber(attributeName, Double.class);
                case OFFSET_DATE_TIME -> pathBuilder.getDate(attributeName, LocalDate.class);
                case ENUM -> pathBuilder.getSimple(attributeName, String.class);
            };
        } else {
            return pathBuilder.get(attributeName);
        }
    }
}

// OperatorProcessor.java
@Component
@RequiredArgsConstructor
class OperatorProcessor {
    
    private final ExpressionBuilder expressionBuilder;
    private final ConstantBuilder constantBuilder;
    
    public BooleanExpression process(OperatorType operatorType, Expression<?> left, PredicateNode node) {
        return switch (operatorType) {
            case IS_NULL -> Expressions.predicate(Ops.IS_NULL, left);
            case IS_NOT_NULL -> Expressions.predicate(Ops.IS_NOT_NULL, left);
            case IN, NOT_IN -> buildInExpression(left, node.getInValues(), operatorType == OperatorType.NOT_IN);
            case BETWEEN -> buildBetweenExpression(left, node);
            default -> buildBinaryExpression(operatorType, left, node);
        };
    }
    
    private BooleanExpression buildBinaryExpression(OperatorType operatorType, Expression<?> left, PredicateNode node) {
        Expression<?> right = expressionBuilder.buildOperandExpression(node.getRightOperand(), null);
        if (right == null) {
            throw new IllegalArgumentException("Right operand required for operator: " + operatorType);
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
    
    private BooleanExpression buildInExpression(Expression<?> left, List<PredicateNodeValue> inValues, boolean negate) {
        if (inValues == null || inValues.isEmpty()) {
            throw new IllegalArgumentException("IN operator requires at least one value");
        }
        
        List<Expression<?>> expressions = inValues.stream()
            .map(constantBuilder::buildConstant)
            .collect(Collectors.toList());
            
        BooleanExpression inExpr = Expressions.predicate(Ops.IN, left, Expressions.list(expressions.toArray(new Expression[0])));
        return negate ? inExpr.not() : inExpr;
    }
    
    private BooleanExpression buildBetweenExpression(Expression<?> left, PredicateNode node) {
        Expression<?> lowerBound = expressionBuilder.buildOperandExpression(node.getLeftOperand(), null);
        Expression<?> upperBound = expressionBuilder.buildOperandExpression(node.getRightOperand(), null);
        
        if (lowerBound == null || upperBound == null) {
            throw new IllegalArgumentException("BETWEEN operator requires both lower and upper bounds");
        }
        
        return Expressions.predicate(Ops.BETWEEN, left, lowerBound, upperBound);
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
            case STRING, BOOLEAN, INTEGER, OFFSET_DATE_TIME, DOUBLE -> Expressions.constant(valueObj);
            case ENUM -> buildEnumConstant(value);
        };
    }
    
    @SneakyThrows
    private Expression<?> buildEnumConstant(PredicateNodeValue value) {
        MetaEnumValue enumValue = (MetaEnumValue) value.getValue();
        Class<?> enumClass = Class.forName(enumValue.getMetaEnum().getClassName());
        Method valueOfMethod = Enum.class.getMethod("valueOf", Class.class, String.class);
        Object enumConstant = valueOfMethod.invoke(null, enumClass, enumValue.getName());
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