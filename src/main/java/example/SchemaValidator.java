package example;

import com.cosium.spring.data.jpa.entity.graph.domain2.DynamicEntityGraph;
import example.models.meta.MetaAttribute;
import example.models.meta.MetaEntity;
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
        List<Class<?>> metaDataClasses = List.of(MetaEntity.class, MetaAttribute.class);

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
    }

    private void validateEntities(List<EntityType<?>> metamodelEntities, List<MetaEntity> metaEntities) {
        List<String> discrepancies = new ArrayList<>();

        // Преобразуем в множества имен для удобства сравнения
        Set<String> metamodelEntityNames = metamodelEntities.stream()
                .map(EntityType::getName)
                .collect(Collectors.toSet());

        Set<String> metaEntityNames = metaEntities.stream()
                .map(MetaEntity::getName)
                .collect(Collectors.toSet());

        // Проверка: есть в метамодели, но нет в БД - WARN
        for (String metamodelName : metamodelEntityNames) {
            if (!metaEntityNames.contains(metamodelName)) {
                logger.warn("Entity '{}' found in metamodel but not in database metadata", metamodelName);
            }
        }

        // Проверка: есть в БД, но нет в метамодели - ERROR
        for (String metaName : metaEntityNames) {
            if (!metamodelEntityNames.contains(metaName)) {
                discrepancies.add("Entity '" + metaName + "' found in database metadata but not in metamodel");
            }
        }

        // Проверка соответствия атрибутов для существующих сущностей
        for (EntityType<?> metamodelEntity : metamodelEntities) {
            MetaEntity metaEntity = metaEntities.stream()
                    .filter(me -> me.getName().equals(metamodelEntity.getName()))
                    .findFirst()
                    .orElse(null);

            if (metaEntity != null) {
                validateAttributes(metamodelEntity, metaEntity, discrepancies);
            }
        }

        // Если найдены расхождения - выбрасываем исключение
        if (!discrepancies.isEmpty()) {
            String errorMessage = "Schema validation failed:\n" + String.join("\n", discrepancies);
            logger.error(errorMessage);
            throw new IllegalStateException(errorMessage);
        }

        logger.info("Schema validation completed successfully");
    }

    private void validateAttributes(EntityType<?> metamodelEntity, MetaEntity metaEntity, List<String> discrepancies) {
        String entityName = metamodelEntity.getName();

        // Получаем атрибуты из метамодели
        Set<String> metamodelAttributeNames = metamodelEntity.getAttributes().stream()
                .map(Attribute::getName)
                .collect(Collectors.toSet());

        // Получаем атрибуты из БД
        Set<String> metaAttributeNames = metaEntity.getAttributes().stream()
                .map(MetaAttribute::getName)
                .collect(Collectors.toSet());

        // Проверка: есть в метамодели, но нет в БД - WARN
        for (String metamodelAttrName : metamodelAttributeNames) {
            if (!metaAttributeNames.contains(metamodelAttrName)) {
                logger.warn("Attribute '{}.{}' found in metamodel but not in database metadata",
                        entityName, metamodelAttrName);
            }
        }

        // Проверка: есть в БД, но нет в метамодели - ERROR
        for (String metaAttrName : metaAttributeNames) {
            if (!metamodelAttributeNames.contains(metaAttrName)) {
                discrepancies.add("Attribute '" + entityName + "." + metaAttrName +
                        "' found in database metadata but not in metamodel");
            }
        }

        // Дополнительная проверка типов атрибутов для существующих атрибутов
        for (Attribute<?, ?> metamodelAttr : metamodelEntity.getAttributes()) {
            MetaAttribute metaAttr = metaEntity.getAttributes().stream()
                    .filter(ma -> ma.getName().equals(metamodelAttr.getName()))
                    .findFirst()
                    .orElse(null);

            if (metaAttr != null) {
                validateAttributeType(metamodelAttr, metaAttr, entityName, discrepancies);
            }
        }
    }

    private void validateAttributeType(Attribute<?, ?> metamodelAttr, MetaAttribute metaAttr,
                                       String entityName, List<String> discrepancies) {
        String attrName = metamodelAttr.getName();

        // Проверка типа атрибута (SINGULAR/PLURAL)
        boolean isPluralInMetamodel = metamodelAttr.isCollection();
        boolean isPluralInMeta = "PLURAL".equals(metaAttr.getType().name());

        if (isPluralInMetamodel != isPluralInMeta) {
            discrepancies.add("Attribute type mismatch for '" + entityName + "." + attrName +
                    "': metamodel=" + (isPluralInMetamodel ? "PLURAL" : "SINGULAR") +
                    ", metadata=" + (isPluralInMeta ? "PLURAL" : "SINGULAR"));
        }

        // Проверка категории типа (BASIC/ENTITY)
        if (!isPluralInMetamodel) {
            // Для не-коллекций проверяем Java тип
            Class<?> javaType = metamodelAttr.getJavaType();
            boolean isEntityInMetamodel = !javaType.isPrimitive() &&
                    !javaType.getName().startsWith("java.") &&
                    !javaType.isEnum();

            boolean isEntityInMeta = "ENTITY".equals(metaAttr.getAttributeCategory().name());

            if (isEntityInMetamodel != isEntityInMeta) {
                discrepancies.add("Attribute category mismatch for '" + entityName + "." + attrName +
                        "': metamodel=" + (isEntityInMetamodel ? "ENTITY" : "BASIC") +
                        ", metadata=" + (isEntityInMeta ? "ENTITY" : "BASIC"));
            }
        }
    }
}