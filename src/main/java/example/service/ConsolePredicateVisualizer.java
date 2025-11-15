package example.service;

import example.models.meta.AttributeCategory;
import example.models.meta.MetaAttribute;
import example.models.meta.MetaEnumValue;
import example.models.predicate.*;
import example.repo.PredicateDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.hibernate.Hibernate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

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
        PredicateDefinition predicateDefinition = predicateDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PredicateDefinition not found: " + id));

        StringBuilder sb = new StringBuilder();
        sb.append("🏷️  PredicateDefinition: '")
                .append(predicateDefinition.getName())
                .append("' (ID: ")
                .append(predicateDefinition.getId())
                .append(")\n");

        sb.append("🎯 Target Entity: ")
                .append(predicateDefinition.getMetaEntity().getName())
                .append("\n\n");

        if (predicateDefinition.getRootNode() != null) {
            visualizeNode(sb, predicateDefinition.getRootNode(), "", true, true);
        } else {
            sb.append("📭 (empty predicate)\n");
        }

        return sb.toString();
    }

    @Transactional(readOnly = true)
    public String visualizeCompact(UUID id) {
        PredicateDefinition predicateDefinition = predicateDefinitionRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("PredicateDefinition not found: " + id));

        StringBuilder sb = new StringBuilder();
        sb.append("'").append(predicateDefinition.getName()).append("': ");

        if (predicateDefinition.getRootNode() != null) {
            visualizeNodeCompact(sb, predicateDefinition.getRootNode());
        } else {
            sb.append("(empty)");
        }

        return sb.toString();
    }

    private void visualizeNode(StringBuilder sb, PredicateNode node, String prefix, boolean isLast, boolean isRoot) {
        String currentPrefix = prefix + (isLast ? LAST_BRANCH : BRANCH);
        String childPrefix = prefix + (isLast ? EMPTY_INDENT : CONNECTOR);

        // Распаковываем proxy для определения типа
        PredicateNode unproxiedNode = (PredicateNode) Hibernate.unproxy(node);

        // Основная информация об узле с эмодзи
        String nodeIcon = getNodeIcon(unproxiedNode);
        sb.append(currentPrefix).append(nodeIcon).append(" ");

        // Информация о типе узла и операторе
        if (unproxiedNode instanceof EvaluationOperationNode evalNode) {
            sb.append(getOperatorSymbol(evalNode.getOperatorType()));
            if (isRoot) {
                sb.append(" [ROOT]");
            }
        } else {
            sb.append(unproxiedNode.getClass().getSimpleName());
        }

        // Return type
        sb.append(" → ").append(unproxiedNode.getNodeReturnType()).append("\n");

        // Детальная информация в зависимости от типа узла
        visualizeNodeDetails(sb, unproxiedNode, childPrefix, true);
    }

    private void visualizeNodeDetails(StringBuilder sb, PredicateNode node, String prefix, boolean isLast) {
        switch (node) {
            case PathExpressionNode pathNode ->
                    visualizePathExpression(sb, pathNode.getPathExpression(), prefix, isLast);
            case ValueConstantNode valueNode ->
                    visualizeValueConstant(sb, valueNode, prefix, isLast);
            case EvaluationOperationNode evalNode ->
                    visualizeEvaluationOperation(sb, evalNode, prefix);
            default ->
                    sb.append(prefix).append("❓ Unknown node type: ").append(node.getClass().getSimpleName()).append("\n");
        }
    }

    private void visualizePathExpression(
            StringBuilder sb, PredicatePathExpression pathExpr, String prefix, boolean isLast) {
        if (pathExpr == null) {
            sb.append(prefix).append(LAST_BRANCH).append("📭 null PathExpression\n");
            return;
        }

        // Распаковываем proxy
        PredicatePathExpression unproxiedPathExpr = (PredicatePathExpression) Hibernate.unproxy(pathExpr);

        String currentPrefix = prefix + (isLast ? LAST_BRANCH : BRANCH);

        // Путь в читаемом формате
        sb.append(currentPrefix).append("🛣️  Path: ").append(buildReadablePath(unproxiedPathExpr)).append("\n");

        String childPrefix = prefix + (isLast ? EMPTY_INDENT : CONNECTOR);

        // Детали конечного атрибута
        if (unproxiedPathExpr.getFinalPathAttribute() != null) {
            MetaAttribute finalAttr = (MetaAttribute) Hibernate.unproxy(unproxiedPathExpr.getFinalPathAttribute());
            sb.append(childPrefix).append(BRANCH)
                    .append("🎯 Final: ").append(visualizeMetaAttribute(finalAttr)).append("\n");
        }

        // Атрибуты пути
        List<MetaAttribute> pathAttributes = unproxiedPathExpr.getPathAttributes();
        if (!pathAttributes.isEmpty()) {
            sb.append(childPrefix).append(BRANCH).append("🔗 Navigation:\n");
            for (int i = 0; i < pathAttributes.size(); i++) {
                boolean lastAttr = i == pathAttributes.size() - 1;
                String attrPrefix = childPrefix + CONNECTOR;
                MetaAttribute attr = (MetaAttribute) Hibernate.unproxy(pathAttributes.get(i));
                sb.append(attrPrefix)
                        .append(lastAttr ? LAST_BRANCH : BRANCH)
                        .append(visualizeMetaAttribute(attr))
                        .append("\n");
            }
        }
    }

    private void visualizeValueConstant(StringBuilder sb, ValueConstantNode valueNode, String prefix, boolean isLast) {
        // Распаковываем proxy
        ValueConstantNode unproxiedNode = (ValueConstantNode) Hibernate.unproxy(valueNode);

        String currentPrefix = prefix + (isLast ? LAST_BRANCH : BRANCH);

        // Одиночное значение
        if (unproxiedNode.getValue() != null) {
            PredicateNodeValue value = (PredicateNodeValue) Hibernate.unproxy(unproxiedNode.getValue());
            sb.append(currentPrefix).append("💎 Value: ").append(visualizeNodeValue(value)).append("\n");
        }

        // Множественные значения (IN)
        if (unproxiedNode.getValues() != null && !unproxiedNode.getValues().isEmpty()) {
            sb.append(currentPrefix).append("📦 IN Values (").append(unproxiedNode.getValues().size()).append("):\n");
            String valuesPrefix = prefix + (isLast ? EMPTY_INDENT : CONNECTOR);

            List<PredicateNodeValue> values = unproxiedNode.getValues().stream()
                    .map(value -> (PredicateNodeValue) Hibernate.unproxy(value))
                    .toList();

            for (int i = 0; i < values.size(); i++) {
                boolean lastValue = i == values.size() - 1;
                String valuePrefix = valuesPrefix + CONNECTOR;
                sb.append(valuePrefix)
                        .append(lastValue ? LAST_BRANCH : BRANCH)
                        .append("[").append(i).append("] ")
                        .append(visualizeNodeValue(values.get(i)))
                        .append("\n");
            }
        }
    }

    private void visualizeEvaluationOperation(StringBuilder sb, EvaluationOperationNode node, String prefix) {
        // Распаковываем proxy
        EvaluationOperationNode unproxiedNode = (EvaluationOperationNode) Hibernate.unproxy(node);
        OperatorType operator = unproxiedNode.getOperatorType();

        // УБИРАЕМ лишние строки с описаниями операций - они создают висячие линии

        // Сразу переходим к визуализации операндов
        switch (operator) {
            case AND, OR -> visualizeLogicalOperation(sb, unproxiedNode, prefix);
            case NOT -> visualizeNotOperation(sb, unproxiedNode, prefix);
            case IS_NULL, IS_NOT_NULL -> visualizeNullCheck(sb, unproxiedNode, prefix);
            case IN, NOT_IN -> visualizeInOperation(sb, unproxiedNode, prefix);
            case DISTANCE_SPHERE -> visualizeSpatialOperation(sb, unproxiedNode, prefix);
            default -> visualizeComparisonOperation(sb, unproxiedNode, prefix);
        }
    }

    private void visualizeLogicalOperation(StringBuilder sb, EvaluationOperationNode node, String prefix) {
        // УБИРАЕМ строку "🔗 Logical Operation" - она создает висячую линию

        if (node.getLeftOperand() != null) {
            PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(node.getLeftOperand());
            visualizeNode(sb, leftOperand, prefix, false, false);
        }

        if (node.getRightOperand() != null) {
            PredicateNode rightOperand = (PredicateNode) Hibernate.unproxy(node.getRightOperand());
            visualizeNode(sb, rightOperand, prefix, true, false);
        }
    }

    private void visualizeNotOperation(StringBuilder sb, EvaluationOperationNode node, String prefix) {
        // УБИРАЕМ строку "🚫 Negation" - она создает висячую линию

        if (node.getLeftOperand() != null) {
            PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(node.getLeftOperand());
            visualizeNode(sb, leftOperand, prefix, true, false);
        }
    }

    private void visualizeNullCheck(StringBuilder sb, EvaluationOperationNode node, String prefix) {
        // УБИРАЕМ строку "❓ Null Check" - она создает висячую линию

        if (node.getLeftOperand() != null) {
            PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(node.getLeftOperand());
            visualizeNode(sb, leftOperand, prefix, true, false);
        }
    }

    private void visualizeInOperation(StringBuilder sb, EvaluationOperationNode node, String prefix) {
        // УБИРАЕМ строку "📋 IN Operation" - она создает висячую линию

        // Левый операнд (поле)
        if (node.getLeftOperand() != null) {
            PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(node.getLeftOperand());
            visualizeNode(sb, leftOperand, prefix, false, false);
        }

        // Правый операнд (значения)
        if (node.getRightOperand() != null) {
            PredicateNode rightOperand = (PredicateNode) Hibernate.unproxy(node.getRightOperand());
            visualizeNode(sb, rightOperand, prefix, true, false);
        }
    }

    private void visualizeSpatialOperation(StringBuilder sb, EvaluationOperationNode node, String prefix) {
        // УБИРАЕМ строку "📍 Spatial Operation" - она создает висячую линию

        if (node.getLeftOperand() != null) {
            PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(node.getLeftOperand());
            visualizeNode(sb, leftOperand, prefix, false, false);
        }

        if (node.getRightOperand() != null) {
            PredicateNode rightOperand = (PredicateNode) Hibernate.unproxy(node.getRightOperand());
            visualizeNode(sb, rightOperand, prefix, true, false);
        }
    }

    private void visualizeComparisonOperation(StringBuilder sb, EvaluationOperationNode node, String prefix) {
        // УБИРАЕМ строку "⚖️  Comparison" - она создает висячую линию

        // Левый операнд
        if (node.getLeftOperand() != null) {
            PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(node.getLeftOperand());
            visualizeNode(sb, leftOperand, prefix, false, false);
        }

        // Правый операнд
        if (node.getRightOperand() != null) {
            PredicateNode rightOperand = (PredicateNode) Hibernate.unproxy(node.getRightOperand());
            visualizeNode(sb, rightOperand, prefix, true, false);
        }
    }

    // Компактная визуализация для краткого представления
    private void visualizeNodeCompact(StringBuilder sb, PredicateNode node) {
        // Распаковываем proxy
        PredicateNode unproxiedNode = (PredicateNode) Hibernate.unproxy(node);

        switch (unproxiedNode) {
            case EvaluationOperationNode evalNode -> visualizeEvaluationOperationCompact(sb, evalNode);
            case PathExpressionNode pathNode -> {
                PredicatePathExpression unproxiedPath = (PredicatePathExpression) Hibernate.unproxy(pathNode.getPathExpression());
                sb.append(buildReadablePath(unproxiedPath));
            }
            case ValueConstantNode valueNode -> {
                ValueConstantNode unproxiedValueNode = (ValueConstantNode) Hibernate.unproxy(valueNode);
                if (unproxiedValueNode.getValue() != null) {
                    PredicateNodeValue value = (PredicateNodeValue) Hibernate.unproxy(unproxiedValueNode.getValue());
                    sb.append(visualizeNodeValueCompact(value));
                } else if (unproxiedValueNode.getValues() != null && !unproxiedValueNode.getValues().isEmpty()) {
                    sb.append("[");
                    unproxiedValueNode.getValues().forEach(value -> {
                        PredicateNodeValue unproxiedValue = (PredicateNodeValue) Hibernate.unproxy(value);
                        sb.append(visualizeNodeValueCompact(unproxiedValue)).append(", ");
                    });
                    if (sb.charAt(sb.length() - 2) == ',') {
                        sb.setLength(sb.length() - 2); // Remove last comma and space
                    }
                    sb.append("]");
                }
            }
            default -> sb.append("?");
        }
    }

    private void visualizeEvaluationOperationCompact(StringBuilder sb, EvaluationOperationNode node) {
        // Распаковываем proxy
        EvaluationOperationNode unproxiedNode = (EvaluationOperationNode) Hibernate.unproxy(node);
        OperatorType operator = unproxiedNode.getOperatorType();

        switch (operator) {
            case AND, OR -> {
                sb.append("(");
                if (unproxiedNode.getLeftOperand() != null) {
                    PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(unproxiedNode.getLeftOperand());
                    visualizeNodeCompact(sb, leftOperand);
                }
                sb.append(" ").append(getOperatorSymbol(operator)).append(" ");
                if (unproxiedNode.getRightOperand() != null) {
                    PredicateNode rightOperand = (PredicateNode) Hibernate.unproxy(unproxiedNode.getRightOperand());
                    visualizeNodeCompact(sb, rightOperand);
                }
                sb.append(")");
            }
            case NOT -> {
                sb.append("NOT(");
                if (unproxiedNode.getLeftOperand() != null) {
                    PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(unproxiedNode.getLeftOperand());
                    visualizeNodeCompact(sb, leftOperand);
                }
                sb.append(")");
            }
            case IS_NULL -> {
                if (unproxiedNode.getLeftOperand() != null) {
                    PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(unproxiedNode.getLeftOperand());
                    visualizeNodeCompact(sb, leftOperand);
                    sb.append(" IS NULL");
                }
            }
            case IS_NOT_NULL -> {
                if (unproxiedNode.getLeftOperand() != null) {
                    PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(unproxiedNode.getLeftOperand());
                    visualizeNodeCompact(sb, leftOperand);
                    sb.append(" IS NOT NULL");
                }
            }
            case IN, NOT_IN -> {
                if (unproxiedNode.getLeftOperand() != null) {
                    PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(unproxiedNode.getLeftOperand());
                    visualizeNodeCompact(sb, leftOperand);
                }
                sb.append(operator == OperatorType.IN ? " IN " : " NOT IN ");
                if (unproxiedNode.getRightOperand() != null) {
                    PredicateNode rightOperand = (PredicateNode) Hibernate.unproxy(unproxiedNode.getRightOperand());
                    visualizeNodeCompact(sb, rightOperand);
                }
            }
            case DISTANCE_SPHERE -> {
                sb.append("DISTANCE_SPHERE(");
                if (unproxiedNode.getLeftOperand() != null) {
                    PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(unproxiedNode.getLeftOperand());
                    visualizeNodeCompact(sb, leftOperand);
                }
                sb.append(", ");
                if (unproxiedNode.getRightOperand() != null) {
                    PredicateNode rightOperand = (PredicateNode) Hibernate.unproxy(unproxiedNode.getRightOperand());
                    visualizeNodeCompact(sb, rightOperand);
                }
                sb.append(")");
            }
            default -> {
                if (unproxiedNode.getLeftOperand() != null) {
                    PredicateNode leftOperand = (PredicateNode) Hibernate.unproxy(unproxiedNode.getLeftOperand());
                    visualizeNodeCompact(sb, leftOperand);
                }
                sb.append(" ").append(getOperatorSymbol(operator)).append(" ");
                if (unproxiedNode.getRightOperand() != null) {
                    PredicateNode rightOperand = (PredicateNode) Hibernate.unproxy(unproxiedNode.getRightOperand());
                    visualizeNodeCompact(sb, rightOperand);
                }
            }
        }
    }

    // Вспомогательные методы
    private String buildReadablePath(PredicatePathExpression pathExpr) {
        if (pathExpr == null) return "null";

        StringBuilder path = new StringBuilder();

        List<MetaAttribute> pathAttributes = pathExpr.getPathAttributes();

        // Добавляем атрибуты пути в правильном порядке
        for (MetaAttribute attr : pathAttributes) {
            MetaAttribute unproxiedAttr = (MetaAttribute) Hibernate.unproxy(attr);
            path.append(unproxiedAttr.getName()).append(".");
        }

        // Конечный атрибут
        if (pathExpr.getFinalPathAttribute() != null) {
            MetaAttribute finalAttr = (MetaAttribute) Hibernate.unproxy(pathExpr.getFinalPathAttribute());
            path.append(finalAttr.getName());
        }

        return path.toString();
    }

    private String visualizeMetaAttribute(MetaAttribute attribute) {
        if (attribute == null) return "null";

        MetaAttribute unproxiedAttr = (MetaAttribute) Hibernate.unproxy(attribute);

        return String.format("%s [%s:%s]",
                unproxiedAttr.getName(),
                unproxiedAttr.getAttributeCategory(),
                unproxiedAttr.getAttributeCategory() == AttributeCategory.BASIC ?
                        unproxiedAttr.getBasicType() :
                        "ENTITY->" + unproxiedAttr.getAttributeEntityType().getName());
    }

    private String visualizeNodeValue(PredicateNodeValue value) {
        if (value == null) return "null";

        PredicateNodeValue unproxiedValue = (PredicateNodeValue) Hibernate.unproxy(value);
        StringBuilder sb = new StringBuilder();
        sb.append(unproxiedValue.getValueType()).append(": ");

        Object actualValue = unproxiedValue.getValue();
        if (actualValue instanceof MetaEnumValue enumValue) {
            MetaEnumValue unproxiedEnum = (MetaEnumValue) Hibernate.unproxy(enumValue);
            sb.append(unproxiedEnum.getName())
                    .append(" (storage: ").append(unproxiedEnum.getStorageValue()).append(")");
        } else {
            sb.append(actualValue);
        }

        return sb.toString();
    }

    private String visualizeNodeValueCompact(PredicateNodeValue value) {
        if (value == null) return "null";

        PredicateNodeValue unproxiedValue = (PredicateNodeValue) Hibernate.unproxy(value);
        Object actualValue = unproxiedValue.getValue();
        if (actualValue instanceof MetaEnumValue enumValue) {
            MetaEnumValue unproxiedEnum = (MetaEnumValue) Hibernate.unproxy(enumValue);
            return unproxiedEnum.getName();
        } else {
            return String.valueOf(actualValue);
        }
    }

    private String getNodeIcon(PredicateNode node) {
        return switch (node) {
            case EvaluationOperationNode evalNode -> getOperatorIcon(evalNode.getOperatorType());
            case PathExpressionNode x -> "🛣️";
            case ValueConstantNode y -> "💎";
            default -> "❓";
        };
    }

    private String getOperatorIcon(OperatorType operator) {
        return switch (operator) {
            case AND, OR -> "🔗";
            case NOT -> "🚫";
            case EQ -> "🟰";
            case NE -> "≠";
            case GT -> "⬆️";
            case LT -> "⬇️";
            case GOE -> "≥";
            case LOE -> "≤";
            case LIKE, STARTS_WITH, ENDS_WITH, CONTAINS -> "🔍";
            case IS_NULL, IS_NOT_NULL -> "❓";
            case IN, NOT_IN -> "📋";
            case DISTANCE_SPHERE -> "📍";
            default -> "⚙️";
        };
    }

    private String getOperatorSymbol(OperatorType operator) {
        return switch (operator) {
            case AND -> "AND";
            case OR -> "OR";
            case NOT -> "NOT";
            case EQ -> "=";
            case NE -> "≠";
            case GT -> ">";
            case LT -> "<";
            case GOE -> ">=";
            case LOE -> "<=";
            case LIKE -> "LIKE";
            case STARTS_WITH -> "STARTS_WITH";
            case ENDS_WITH -> "ENDS_WITH";
            case CONTAINS -> "CONTAINS";
            case IS_NULL -> "IS NULL";
            case IS_NOT_NULL -> "IS NOT NULL";
            case IN -> "IN";
            case NOT_IN -> "NOT IN";
            case DISTANCE_SPHERE -> "DISTANCE_SPHERE";
            default -> operator.name();
        };
    }
}