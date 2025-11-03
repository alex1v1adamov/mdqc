package example.service;

import com.querydsl.core.types.Expression;
import com.querydsl.core.types.Ops;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.Expressions;
import com.querydsl.core.types.dsl.PathBuilder;
import example.models.meta.AttributeCategory;
import example.models.meta.MetaAttribute;
import example.models.meta.MetaEntity;
import example.models.predicate.OperatorType;
import example.models.predicate.PredicateDefinition;
import example.models.predicate.PredicateNode;
import example.models.predicate.PredicateNodeValue;
import example.models.predicate.PredicatePathExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class PredicateGeneratorService {

    public BooleanExpression generatePredicate(PredicateDefinition predicateDefinition) {
        try {
            Class<?> entityClass = Class.forName(predicateDefinition.getMetaEntity().getName());
            PathBuilder<?> entityPath = new PathBuilder<>(entityClass, "entity");
            return buildPredicate(predicateDefinition.getRootNode(), entityPath);
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Entity class not found: " + predicateDefinition.getMetaEntity().getName(), e);
        }
    }

    private BooleanExpression buildPredicate(PredicateNode node, PathBuilder<?> entityPath) {
        if (node == null) return null;

        return switch (node.getNodeType()) {
            case LOGICAL_OPERATOR -> buildLogicalPredicate(node, entityPath);
            case COMPARISON_OPERATOR -> buildComparisonPredicate(node, entityPath);
            case VALUE_CONSTANT ->
                    throw new IllegalArgumentException("VALUE_CONSTANT cannot be used as standalone predicate");
            case PATH_EXPRESSION -> buildPathExpressionPredicate(node, entityPath);
        };
    }

    private BooleanExpression buildLogicalPredicate(PredicateNode node, PathBuilder<?> entityPath) {
        BooleanExpression left = buildPredicate(node.getLeftOperand(), entityPath);
        BooleanExpression right = buildPredicate(node.getRightOperand(), entityPath);

        return switch (node.getOperatorType()) {
            case AND -> andExpressions(left, right);
            case OR -> orExpressions(left, right);
            case NOT -> notExpression(left);
            default -> throw new IllegalArgumentException("Unsupported logical operator: " + node.getOperatorType());
        };
    }

    private BooleanExpression andExpressions(BooleanExpression left, BooleanExpression right) {
        if (left != null && right != null) return left.and(right);
        if (left != null) return left;
        return right;
    }

    private BooleanExpression orExpressions(BooleanExpression left, BooleanExpression right) {
        if (left != null && right != null) return left.or(right);
        if (left != null) return left;
        return right;
    }

    private BooleanExpression notExpression(BooleanExpression expression) {
        return expression != null ? expression.not() : null;
    }

    private BooleanExpression buildComparisonPredicate(PredicateNode node, PathBuilder<?> entityPath) {
        // Для операторов IS_NULL и IS_NOT_NULL используем только meta_attribute
        if (node.getOperatorType() == OperatorType.IS_NULL || node.getOperatorType() == OperatorType.IS_NOT_NULL) {
            Expression<?> attributeExpression = buildAttributeExpression(node.getMetaAttribute(), entityPath);
            if (attributeExpression == null) return null;

            return node.getOperatorType() == OperatorType.IS_NULL
                    ? Expressions.predicate(Ops.IS_NULL, attributeExpression)
                    : Expressions.predicate(Ops.IS_NOT_NULL, attributeExpression);
        }

        // Для остальных операторов сравнения используем левый и правый операнды
        Expression<?> left = buildOperandExpression(node.getLeftOperand(), entityPath);
        if (left == null) return null;

        return switch (node.getOperatorType()) {
            case EQ, NE -> buildEqualityPredicate(node, left, entityPath);
            default -> throw new IllegalArgumentException("Unsupported comparison operator: " + node.getOperatorType());
        };
    }

    private BooleanExpression buildEqualityPredicate(PredicateNode node, Expression<?> left, PathBuilder<?> entityPath) {
        Expression<?> right = buildOperandExpression(node.getRightOperand(), entityPath);
        if (right == null) return null;

        Ops operator = node.getOperatorType() == OperatorType.EQ ? Ops.EQ : Ops.NE;
        return Expressions.booleanOperation(operator, left, right);
    }

    // Новый метод для построения выражений операндов (без рекурсии для COMPARISON_OPERATOR)
    private Expression<?> buildOperandExpression(PredicateNode node, PathBuilder<?> entityPath) {
        if (node == null) return null;

        return switch (node.getNodeType()) {
            case VALUE_CONSTANT -> buildConstantExpression(node);
            case PATH_EXPRESSION -> buildPathExpression(node, entityPath);
            case COMPARISON_OPERATOR -> {
                // Для COMPARISON_OPERATOR в операндах - это атрибут, а не предикат
                // Создаем выражение атрибута из meta_attribute
                yield buildAttributeExpression(node.getMetaAttribute(), entityPath);
            }
            default -> throw new IllegalArgumentException("Unsupported operand node type: " + node.getNodeType());
        };
    }

    // Старый метод buildExpression оставляем только для внутреннего использования
    private Expression<?> buildExpression(PredicateNode node, PathBuilder<?> entityPath) {
        if (node == null) return null;

        return switch (node.getNodeType()) {
            case VALUE_CONSTANT -> buildConstantExpression(node);
            case PATH_EXPRESSION -> buildPathExpression(node, entityPath);
            case COMPARISON_OPERATOR -> {
                // Для выражений, которые используют COMPARISON_OPERATOR как часть большего выражения
                // Создаем BooleanExpression и преобразуем его в Expression
                BooleanExpression booleanExpr = buildComparisonPredicate(node, entityPath);
                yield booleanExpr != null ? Expressions.asBoolean(booleanExpr) : null;
            }
            default -> throw new IllegalArgumentException("Unsupported expression node type: " + node.getNodeType());
        };
    }

    // Вынесен в отдельный метод для использования в COMPARISON_OPERATOR
    private Expression<?> buildAttributeExpression(MetaAttribute metaAttribute, PathBuilder<?> entityPath) {
        if (metaAttribute == null) return null;
        return createTypedExpression(entityPath, metaAttribute);
    }

    private Expression<?> buildPathExpression(PredicateNode node, PathBuilder<?> entityPath) {
        PredicatePathExpression pathExpression = node.getPathExpression();
        if (pathExpression == null) return null;

        // Начинаем с корневой сущности
        Expression<?> currentExpression = entityPath;

        // Обрабатываем корневой атрибут (ENTITY)
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
            // Навигация по связи - создаем PathBuilder для следующей сущности
            Class<?> targetClass = getEntityClass(attribute.getAttributeEntityType());

            if (currentExpression instanceof PathBuilder<?> pathBuilder) {
                // Если текущее выражение - PathBuilder, создаем новый для навигации
                return new PathBuilder<>(targetClass, String.valueOf(pathBuilder.get(attribute.getName())));
            } else {
                // Если текущее выражение - уже путь, создаем новый PathBuilder на его основе
                String path = currentExpression.toString() + "." + attribute.getName();
                return new PathBuilder<>(targetClass, path);
            }
        } else {
            // BASIC атрибут - конечная точка пути, создаем типизированное выражение
            return createTypedExpressionFromParent(currentExpression, attribute);
        }
    }

    private Expression<?> createTypedExpressionFromParent(Expression<?> parentExpression, MetaAttribute attribute) {
        String fullPath = parentExpression.toString() + "." + attribute.getName();

        return switch (attribute.getBasicType()) {
            case STRING -> Expressions.stringPath(fullPath);
            case BOOLEAN -> Expressions.booleanPath(fullPath);
            case INTEGER -> Expressions.numberPath(Integer.class, fullPath);
            case DATE -> Expressions.datePath(LocalDate.class, fullPath);
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
                case DATE -> pathBuilder.getDate(attributeName, LocalDate.class);
                case ENUM -> pathBuilder.getSimple(attributeName, String.class);
            };
        } else {
            // Для ENTITY атрибутов возвращаем простой Path
            return pathBuilder.get(attributeName);
        }
    }

    private Class<?> getEntityClass(MetaEntity metaEntity) {
        if (metaEntity == null) {
            throw new IllegalArgumentException("MetaEntity cannot be null for ENTITY attribute");
        }
        try {
            return Class.forName(metaEntity.getName());
        } catch (ClassNotFoundException e) {
            throw new RuntimeException("Entity class not found: " + metaEntity.getName(), e);
        }
    }

    private Expression<?> buildConstantExpression(PredicateNode node) {
        PredicateNodeValue value = node.getValue();
        if (value == null) return null;

        Object valueObj = value.getValue();
        if (valueObj == null) return null;

        // Создаем типизированные константы
        return switch (value.getValueType()) {
            case STRING -> Expressions.constant(valueObj);
            case BOOLEAN -> Expressions.constant(valueObj);
            case INTEGER -> Expressions.constant(valueObj);
            case DATE -> Expressions.constant(valueObj);
            case ENUM -> Expressions.constant(valueObj.toString());
        };
    }

    private BooleanExpression buildPathExpressionPredicate(PredicateNode node, PathBuilder<?> entityPath) {
        Expression<?> pathExpression = buildPathExpression(node, entityPath);
        return pathExpression != null ?
                Expressions.predicate(Ops.IS_NOT_NULL, pathExpression) : null;
    }
}