package example;

import com.cosium.spring.data.jpa.entity.graph.domain2.DynamicEntityGraph;
import example.models.meta.AttributeCategory;
import example.models.meta.AttributeType;
import example.models.meta.BasicType;
import example.models.meta.MetaAttribute;
import example.models.meta.MetaEntity;
import example.models.meta.MetaEnum;
import example.models.meta.MetaEnumValue;
import example.repo.MetaEntityRepository;
import jakarta.annotation.PostConstruct;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class SchemaValidator {

    private static final Logger logger = LoggerFactory.getLogger(SchemaValidator.class);

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private MetaEntityRepository metaEntityRepository;

    @PostConstruct
    @Transactional
    public void validateSchema() {
        List<Class<?>> metaDataClasses = List.of(
                MetaEntity.class, MetaAttribute.class
        );

        Metamodel metamodel = entityManagerFactory.getMetamodel();
        Set<EntityType<?>> entities = metamodel.getEntities();
        List<EntityType<?>> businessEntities = entities.stream()
                .filter(x -> !metaDataClasses.contains(x.getJavaType()))
                .toList();
        List<MetaEntity> businessEntitiesMetaData = metaEntityRepository.findAll(
                null
                , DynamicEntityGraph.loading()
                        .addPath(MetaEntity.Fields.attributes)
                        .addPath(MetaEntity.Fields.attributes + "." + MetaAttribute.Fields.metaEnum + "." + MetaEnum.Fields.values)
                        .build()
        );

        validateEntities(businessEntities, businessEntitiesMetaData);
        logger.info("Schema validation correct");
    }

    private void validateEntities(List<EntityType<?>> jpaEntities, List<MetaEntity> metaEntities) {
        List<String> problems = new ArrayList<>();

        // Создаем мапы для быстрого поиска
        Map<String, EntityType<?>> jpaEntitiesMap = jpaEntities.stream()
                .collect(Collectors.toMap(
                        entity -> entity.getJavaType().getName(),
                        entity -> entity
                ));

        Map<String, MetaEntity> metaEntitiesMap = metaEntities.stream()
                .collect(Collectors.toMap(
                        MetaEntity::getName,
                        entity -> entity
                ));

        // Отсутствие MetaEntity для JPA сущности - это нормально, только логируем INFO
        for (EntityType<?> jpaEntity : jpaEntities) {
            String entityName = jpaEntity.getJavaType().getName();
            if (!metaEntitiesMap.containsKey(entityName)) {
                logger.info("JPA сущность {} не имеет соответствующей MetaEntity (это нормально)", entityName);
            }
        }

        // Лишние MetaEntity без JPA сущности - это НЕ нормально, бросаем исключение
        // Собираем все MetaEntity без соответствующих JPA сущностей
        List<String> missingJpaEntities = io.vavr.collection.Stream.ofAll(metaEntities)
                .filter(metaEntity -> !jpaEntitiesMap.containsKey(metaEntity.getName()))
                .peek(metaEntity -> logger.info("MetaEntity {} не имеет соответствующей JPA сущности", metaEntity.getName()))
                .map(MetaEntity::getName)
                .toJavaList();

        // Если есть несоответствия - бросаем исключение со всей информацией
        if (!missingJpaEntities.isEmpty()) {
            String errorMessage = io.vavr.collection.Stream.of(missingJpaEntities)
                    .transform(stream -> String.format(
                            "Обнаружены MetaEntity без соответствующих JPA сущностей (%d шт.): %s",
                            stream.size(),
                            stream.mkString(", ")
                    ));

            logger.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        // Проверяем атрибуты только для существующих пар сущностей
        for (EntityType<?> jpaEntity : jpaEntities) {
            String entityName = jpaEntity.getJavaType().getName();
            MetaEntity metaEntity = metaEntitiesMap.get(entityName);

            if (metaEntity != null) {
                validateAttributes(jpaEntity, metaEntity, problems);
            }
        }

        // Если есть критические проблемы - бросаем исключение
        if (!problems.isEmpty()) {
            String errorMessage = "Обнаружены несоответствия схемы:\n" +
                    String.join("\n", problems);
            logger.error(errorMessage);
            throw new IllegalStateException("Валидация схемы не пройдена. Проверьте логи для деталей.");
        }

        logger.info("Валидация схемы успешно завершена");
    }

    private void validateAttributes(EntityType<?> jpaEntity, MetaEntity metaEntity, List<String> problems) {
        Map<String, MetaAttribute> metaAttributesMap = metaEntity.getAttributes().stream()
                .collect(Collectors.toMap(
                        MetaAttribute::getName,
                        attr -> attr
                ));

        // Проверяем JPA атрибуты
        for (Attribute<?, ?> jpaAttribute : jpaEntity.getAttributes()) {
            String attributeName = jpaAttribute.getName();
            MetaAttribute metaAttribute = metaAttributesMap.get(attributeName);

            // Отсутствие MetaAttribute - это нормально, только логируем INFO
            if (metaAttribute == null) {
                logger.info("Атрибут {} сущности {} не имеет соответствующего MetaAttribute (это нормально)",
                        attributeName, metaEntity.getName());
                continue;
            }

            // Валидируем соответствие типа атрибута (это уже критично)
            validateAttributeType(jpaAttribute, metaAttribute, metaEntity.getName(), problems);

            // Удаляем проверенный атрибут из мапы
            metaAttributesMap.remove(attributeName);
        }

        // Лишние MetaAttribute - это нормально, только логируем INFO
        for (MetaAttribute extraAttribute : metaAttributesMap.values()) {
            logger.info("MetaAttribute {} сущности {} не имеет соответствующего JPA атрибута (это нормально)",
                    extraAttribute.getName(), metaEntity.getName());
        }
    }

    private void validateAttributeType(Attribute<?, ?> jpaAttribute, MetaAttribute metaAttribute,
                                       String entityName, List<String> problems) {
        // Определяем ожидаемый тип атрибута
        AttributeType expectedType = jpaAttribute.isCollection() ?
                AttributeType.PLURAL : AttributeType.SINGULAR;

        if (metaAttribute.getType() != expectedType) {
            problems.add(String.format(
                    "Несоответствие типа атрибута %s в сущности %s: ожидается %s, но найдено %s",
                    metaAttribute.getName(), entityName, expectedType, metaAttribute.getType()
            ));
        }

        // Для SINGULAR атрибутов проверяем категорию
        if (metaAttribute.getType() == AttributeType.SINGULAR) {
            Class<?> jpaAttributeType = jpaAttribute.getJavaType();
            AttributeCategory expectedCategory = determineAttributeCategory(jpaAttributeType);

            if (metaAttribute.getAttributeCategory() != expectedCategory) {
                problems.add(String.format(
                        "Несоответствие категории атрибута %s в сущности %s: ожидается %s, но найдено %s",
                        metaAttribute.getName(), entityName, expectedCategory, metaAttribute.getAttributeCategory()
                ));
            }

            // Если категория BASIC, проверяем конкретный BasicType
            if (metaAttribute.getAttributeCategory() == AttributeCategory.BASIC &&
                    metaAttribute.getBasicType() != null) {
                BasicType expectedBasicType = determineBasicType(jpaAttributeType);
                if (expectedBasicType != null && metaAttribute.getBasicType() != expectedBasicType) {
                    problems.add(String.format(
                            "Несоответствие BasicType атрибута %s в сущности %s: ожидается %s, но найдено %s",
                            metaAttribute.getName(), entityName, expectedBasicType, metaAttribute.getBasicType()
                    ));
                }
            }

            // Дополнительная проверка для ENUM типов
            if (jpaAttributeType.isEnum() && metaAttribute.getAttributeCategory() == AttributeCategory.BASIC) {
                validateEnumValues(jpaAttributeType, metaAttribute, entityName, problems);
            }
        }
    }

    /**
     * Проверяет соответствие значений enum между JPA-моделью и MetaEnum
     */
    private void validateEnumValues(Class<?> jpaEnumType, MetaAttribute metaAttribute,
                                    String entityName, List<String> problems) {
        if (metaAttribute.getMetaEnum() == null) {
            problems.add(String.format(
                    "Enum атрибут %s в сущности %s не имеет связанного MetaEnum",
                    metaAttribute.getName(), entityName
            ));
            return;
        }

        // Получаем значения из JPA enum
        Object[] jpaEnumValues = jpaEnumType.getEnumConstants();
        List<String> jpaEnumValueNames = new ArrayList<>();
        for (Object enumValue : jpaEnumValues) {
            jpaEnumValueNames.add(((Enum<?>) enumValue).name());
        }

        // Получаем значения из MetaEnum
        List<String> metaEnumValueNames = metaAttribute.getMetaEnum().getValues().stream()
                .map(MetaEnumValue::getName)
                .collect(Collectors.toList());

        // Проверяем соответствие значений
        if (jpaEnumValueNames.size() != metaEnumValueNames.size() ||
                !jpaEnumValueNames.containsAll(metaEnumValueNames)) {
            problems.add(String.format(
                    "Несоответствие значений enum для атрибута %s в сущности %s: " +
                            "JPA значения: %s, MetaEnum значения: %s",
                    metaAttribute.getName(), entityName, jpaEnumValueNames, metaEnumValueNames
            ));
        }
    }

    private AttributeCategory determineAttributeCategory(Class<?> javaType) {
        if (isCurrentlySupportedBasicType(javaType)) {
            return AttributeCategory.BASIC;
        }
        return AttributeCategory.ENTITY;
    }

    private boolean isCurrentlySupportedBasicType(Class<?> javaType) {
        return javaType == String.class ||
                javaType == Boolean.class || javaType == boolean.class ||
                javaType == Integer.class || javaType == int.class ||
                javaType.isEnum();
    }

    private BasicType determineBasicType(Class<?> javaType) {
        if (javaType == String.class) {
            return BasicType.STRING;
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            return BasicType.BOOLEAN;
        } else if (javaType == Integer.class || javaType == int.class) {
            return BasicType.INTEGER;
        } else if (javaType.isEnum()) {
            return BasicType.ENUM;
        }
        return null;
    }

    private boolean isSystemAttribute(String attributeName) {
        return "id".equals(attributeName) ||
                "version".equals(attributeName) ||
                attributeName.contains("$") ||
                attributeName.startsWith("_");
    }
}