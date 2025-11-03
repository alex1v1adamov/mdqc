package example;

import com.cosium.spring.data.jpa.entity.graph.domain2.DynamicEntityGraph;
import example.models.meta.*;
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

        // Лишние MetaEntity без JPA сущности - это нормально, только логируем INFO
        for (MetaEntity metaEntity : metaEntities) {
            if (!jpaEntitiesMap.containsKey(metaEntity.getName())) {
                logger.info("MetaEntity {} не имеет соответствующей JPA сущности (это нормально)", metaEntity.getName());
            }
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

//            // Пропускаем служебные атрибуты
//            if (isSystemAttribute(attributeName)) {
//                metaAttributesMap.remove(attributeName);
//                continue;
//            }

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
                javaType == Integer.class || javaType == int.class;
    }

    private BasicType determineBasicType(Class<?> javaType) {
        if (javaType == String.class) {
            return BasicType.STRING;
        } else if (javaType == Boolean.class || javaType == boolean.class) {
            return BasicType.BOOLEAN;
        } else if (javaType == Integer.class || javaType == int.class) {
            return BasicType.INTEGER;
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