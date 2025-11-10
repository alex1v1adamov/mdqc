package example.service;

import example.models.meta.*;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.metamodel.Attribute;
import jakarta.persistence.metamodel.EntityType;
import jakarta.persistence.metamodel.Metamodel;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MetaEntityValidationService implements Validate<MetaEntity> {

  private Metamodel metamodel;
  private EntityManagerFactory entityManagerFactory;

  public MetaEntityValidationService(final EntityManagerFactory entityManagerFactory) {
    this.entityManagerFactory = entityManagerFactory;
    this.metamodel = entityManagerFactory.getMetamodel();
  }

  /** Валидация всей целостности мета-сущности с учетом JPA метамодели */
  public ValidationResult validate(MetaEntity metaEntity) {
    List<String> errors = new ArrayList<>();

    if (metaEntity == null) {
      return ValidationResult.error("MetaEntity cannot be null");
    }

    // Валидация базовых полей
    if (metaEntity.getName() == null || metaEntity.getName().trim().isEmpty()) {
      errors.add("MetaEntity name cannot be null or empty");
    }

    // Проверка соответствия JPA сущности (если доступна метамодель)
    if (metamodel != null) {
      errors.addAll(validateJpaCompatibility(metaEntity));
    }

    // Валидация атрибутов сущности
    if (metaEntity.getAttributes() != null) {
      for (MetaAttribute attribute : metaEntity.getAttributes()) {
        ValidationResult attributeResult = validateMetaAttribute(attribute);
        if (!attributeResult.isValid()) {
          errors.add(
              "Attribute '"
                  + attribute.getName()
                  + "': "
                  + String.join(", ", attributeResult.getErrors()));
        }
      }

      // Дополнительные проверки на уровне всей сущности
      errors.addAll(validateEntityLevelConstraints(metaEntity));
    }

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
  }

  /** Проверка соответствия JPA метамодели */
  private List<String> validateJpaCompatibility(MetaEntity metaEntity) {
    List<String> errors = new ArrayList<>();

    try {
      Class<?> entityClass = Class.forName(metaEntity.getName());
      EntityType<?> jpaEntity = metamodel.entity(entityClass);

      if (jpaEntity == null) {
        errors.add("JPA entity not found for class: " + metaEntity.getName());
        return errors;
      }

      // Проверка атрибутов на соответствие JPA
      errors.addAll(validateJpaAttributes(jpaEntity, metaEntity));

    } catch (ClassNotFoundException e) {
      errors.add("Entity class not found: " + metaEntity.getName());
    } catch (IllegalArgumentException e) {
      errors.add("Entity not found in JPA metamodel: " + metaEntity.getName());
    }

    return errors;
  }

  /**
   * Проверка соответствия атрибутов JPA метамодели ПРАВИЛА: - JPA атрибуты МОГУТ иметь
   * MetaAttribute (не обязательно) - MetaAttribute ДОЛЖНЫ иметь JPA аналоги (обязательно)
   */
  private List<String> validateJpaAttributes(EntityType<?> jpaEntity, MetaEntity metaEntity) {
    List<String> errors = new ArrayList<>();
    Map<String, MetaAttribute> metaAttributesMap =
        metaEntity.getAttributes().stream()
            .collect(Collectors.toMap(MetaAttribute::getName, attr -> attr));

    // Создаем мапу JPA атрибутов для быстрого поиска
    Map<String, Attribute<?, ?>> jpaAttributesMap =
        jpaEntity.getAttributes().stream()
            .collect(Collectors.toMap(Attribute::getName, attr -> attr));

    // ПРАВИЛО 1: Все MetaAttribute ДОЛЖНЫ иметь JPA аналоги
    for (MetaAttribute metaAttribute : metaEntity.getAttributes()) {
      String attributeName = metaAttribute.getName();
      Attribute<?, ?> jpaAttribute = jpaAttributesMap.get(attributeName);

      if (jpaAttribute == null) {
        errors.add(
            String.format(
                "MetaAttribute '%s' in entity '%s' does not have corresponding JPA attribute",
                attributeName, metaEntity.getName()));
        continue;
      }

      // Проверка типа атрибута
      errors.addAll(
          validateAttributeTypeCompatibility(jpaAttribute, metaAttribute, metaEntity.getName()));

      // Проверка enum значений для enum атрибутов
      if (jpaAttribute.getJavaType().isEnum()
          && metaAttribute.getAttributeCategory() == AttributeCategory.BASIC
          && metaAttribute.getBasicType() == BasicType.ENUM) {
        errors.addAll(validateEnumCompatibility(jpaAttribute, metaAttribute, metaEntity.getName()));
      }
    }

    // ПРАВИЛО 2: JPA атрибуты МОГУТ иметь MetaAttribute (не проверяем - это нормально)
    // Не требуем, чтобы все JPA атрибуты имели MetaAttribute

    return errors;
  }

  /** Проверка совместимости типов атрибутов между JPA и MetaEntity */
  private List<String> validateAttributeTypeCompatibility(
      Attribute<?, ?> jpaAttribute, MetaAttribute metaAttribute, String entityName) {
    List<String> errors = new ArrayList<>();

    // Проверка типа (SINGULAR/PLURAL)
    AttributeType expectedType =
        jpaAttribute.isCollection() ? AttributeType.PLURAL : AttributeType.SINGULAR;

    if (metaAttribute.getType() != expectedType) {
      errors.add(
          String.format(
              "Type mismatch for attribute %s in entity %s: expected %s, but found %s",
              metaAttribute.getName(), entityName, expectedType, metaAttribute.getType()));
    }

    // Для SINGULAR атрибутов проверяем категорию
    if (metaAttribute.getType() == AttributeType.SINGULAR) {
      Class<?> jpaAttributeType = jpaAttribute.getJavaType();
      AttributeCategory expectedCategory = determineAttributeCategory(jpaAttributeType);

      if (metaAttribute.getAttributeCategory() != expectedCategory) {
        errors.add(
            String.format(
                "Category mismatch for attribute %s in entity %s: expected %s, but found %s",
                metaAttribute.getName(),
                entityName,
                expectedCategory,
                metaAttribute.getAttributeCategory()));
      }

      // Если категория BASIC, проверяем конкретный BasicType
      if (metaAttribute.getAttributeCategory() == AttributeCategory.BASIC
          && metaAttribute.getBasicType() != null) {
        BasicType expectedBasicType = determineBasicType(jpaAttributeType);
        if (expectedBasicType != null && metaAttribute.getBasicType() != expectedBasicType) {
          errors.add(
              String.format(
                  "BasicType mismatch for attribute %s in entity %s: expected %s, but found %s",
                  metaAttribute.getName(),
                  entityName,
                  expectedBasicType,
                  metaAttribute.getBasicType()));
        }
      }
    }

    return errors;
  }

  /** Проверка совместимости enum значений */
  private List<String> validateEnumCompatibility(
      Attribute<?, ?> jpaAttribute, MetaAttribute metaAttribute, String entityName) {
    List<String> errors = new ArrayList<>();

    if (metaAttribute.getMetaEnum() == null) {
      errors.add(
          String.format(
              "Enum attribute %s in entity %s does not have associated MetaEnum",
              metaAttribute.getName(), entityName));
      return errors;
    }

    // Получаем значения из JPA enum
    Class<?> jpaEnumType = jpaAttribute.getJavaType();
    Object[] jpaEnumValues = jpaEnumType.getEnumConstants();
    List<String> jpaEnumValueNames = new ArrayList<>();
    for (Object enumValue : jpaEnumValues) {
      jpaEnumValueNames.add(((Enum<?>) enumValue).name());
    }

    // Получаем значения из MetaEnum
    List<String> metaEnumValueNames =
        metaAttribute.getMetaEnum().getValues().stream()
            .map(MetaEnumValue::getName)
            .collect(Collectors.toList());

    // Проверяем соответствие значений
    if (!jpaEnumValueNames.containsAll(metaEnumValueNames)) {
      errors.add(
          String.format(
              "Enum value mismatch for attribute %s in entity %s: "
                  + "JPA values: %s, MetaEnum values: %s",
              metaAttribute.getName(), entityName, jpaEnumValueNames, metaEnumValueNames));
    }

    return errors;
  }

  /** Проверки на уровне всей сущности */
  private List<String> validateEntityLevelConstraints(MetaEntity metaEntity) {
    List<String> errors = new ArrayList<>();

    // Проверка уникальности имен атрибутов
    Set<String> attributeNames =
        metaEntity.getAttributes().stream().map(MetaAttribute::getName).collect(Collectors.toSet());

    if (attributeNames.size() != metaEntity.getAttributes().size()) {
      errors.add("Duplicate attribute names found in entity: " + metaEntity.getName());
    }

    // Проверка bidirectional связей на целостность
    errors.addAll(validateBidirectionalRelationships(metaEntity));

    return errors;
  }

  /** Проверка целостности bidirectional связей */
  private List<String> validateBidirectionalRelationships(MetaEntity metaEntity) {
    List<String> errors = new ArrayList<>();

    for (MetaAttribute attribute : metaEntity.getAttributes()) {
      if (Boolean.TRUE.equals(attribute.getIsBidirectional())
          && attribute.getRelatedAttribute() != null) {

        MetaAttribute related = attribute.getRelatedAttribute();

        // Проверка, что связанный атрибут существует и правильно ссылается обратно
        if (!Boolean.TRUE.equals(related.getIsBidirectional())) {
          errors.add(
              String.format(
                  "Bidirectional attribute %s.%s has non-bidirectional related attribute",
                  metaEntity.getName(), attribute.getName()));
        }

        if (related.getRelatedAttribute() == null
            || !related.getRelatedAttribute().getId().equals(attribute.getId())) {
          errors.add(
              String.format(
                  "Bidirectional attribute %s.%s has incorrect back-reference",
                  metaEntity.getName(), attribute.getName()));
        }

        // Проверка совместимости типов связанных сущностей
        if (attribute.getAttributeEntityType() != null
            && related.getEntity() != null
            && !attribute.getAttributeEntityType().getId().equals(related.getEntity().getId())) {
          errors.add(
              String.format(
                  "Type mismatch in bidirectional relationship: %s.%s -> %s.%s",
                  metaEntity.getName(),
                  attribute.getName(),
                  related.getEntity().getName(),
                  related.getName()));
        }
      }
    }

    return errors;
  }

  /** Валидация MetaAttribute с учетом всех правил из комментариев */
  public ValidationResult validateMetaAttribute(MetaAttribute attribute) {
    List<String> errors = new ArrayList<>();

    if (attribute == null) {
      return ValidationResult.error("MetaAttribute cannot be null");
    }

    // Базовые проверки
    if (attribute.getName() == null || attribute.getName().trim().isEmpty()) {
      errors.add("Attribute name cannot be null or empty");
    }

    if (attribute.getType() == null) {
      errors.add("Attribute type cannot be null");
    }

    if (attribute.getAttributeCategory() == null) {
      errors.add("Attribute category cannot be null");
    }

    if (attribute.getEntity() == null) {
      errors.add("Attribute must belong to an entity");
    }

    // Проверки в зависимости от категории атрибута
    if (attribute.getAttributeCategory() == AttributeCategory.BASIC) {
      errors.addAll(validateBasicAttribute(attribute));
    } else if (attribute.getAttributeCategory() == AttributeCategory.ENTITY) {
      errors.addAll(validateEntityAttribute(attribute));
    }

    // Валидация bidirectional связей
    errors.addAll(validateBidirectionalRelationship(attribute));

    return errors.isEmpty() ? ValidationResult.success() : ValidationResult.error(errors);
  }

  private List<String> validateBasicAttribute(MetaAttribute attribute) {
    List<String> errors = new ArrayList<>();

    // Для BASIC атрибутов basicType обязателен
    if (attribute.getBasicType() == null) {
      errors.add("BASIC attributes must have basicType defined");
    }

    // Для ENUM типа должна быть ссылка на MetaEnum
    if (attribute.getBasicType() == BasicType.ENUM && attribute.getMetaEnum() == null) {
      errors.add("ENUM attributes must reference a MetaEnum");
    }

    // Для не-ENUM типов metaEnum должен быть null
    if (attribute.getBasicType() != BasicType.ENUM && attribute.getMetaEnum() != null) {
      errors.add("Only ENUM attributes can reference MetaEnum");
    }

    // Для BASIC атрибутов attributeEntityType должен быть null
    if (attribute.getAttributeEntityType() != null) {
      errors.add("BASIC attributes cannot have attributeEntityType");
    }

    return errors;
  }

  private List<String> validateEntityAttribute(MetaAttribute attribute) {
    List<String> errors = new ArrayList<>();

    // Для ENTITY атрибутов attributeEntityType обязателен
    if (attribute.getAttributeEntityType() == null) {
      errors.add("ENTITY attributes must have attributeEntityType defined");
    }

    // Для ENTITY атрибутов basicType должен быть null
    if (attribute.getBasicType() != null) {
      errors.add("ENTITY attributes cannot have basicType");
    }

    // Для ENTITY атрибутов metaEnum должен быть null
    if (attribute.getMetaEnum() != null) {
      errors.add("ENTITY attributes cannot reference MetaEnum");
    }

    return errors;
  }

  private List<String> validateBidirectionalRelationship(MetaAttribute attribute) {
    List<String> errors = new ArrayList<>();

    Boolean isBidirectional = attribute.getIsBidirectional();
    MetaAttribute relatedAttribute = attribute.getRelatedAttribute();

    // Правила для bidirectional связей
    if (Boolean.TRUE.equals(isBidirectional)) {
      // Только для ENTITY атрибутов
      if (attribute.getAttributeCategory() != AttributeCategory.ENTITY) {
        errors.add("Bidirectional relationships are only allowed for ENTITY attributes");
      }

      // relatedAttribute обязателен
      if (relatedAttribute == null) {
        errors.add("Bidirectional attributes must have relatedAttribute defined");
      } else {
        // Проверка симметричности
        if (!Boolean.TRUE.equals(relatedAttribute.getIsBidirectional())) {
          errors.add("Related attribute must also be bidirectional");
        }

        if (relatedAttribute.getRelatedAttribute() == null
            || !relatedAttribute.getRelatedAttribute().getId().equals(attribute.getId())) {
          errors.add("Related attribute must reference back to this attribute");
        }

        // Проверка циклических ссылок
        if (attribute.getId() != null && attribute.getId().equals(relatedAttribute.getId())) {
          errors.add("Attribute cannot reference itself as relatedAttribute");
        }
      }
    } else {
      // Если не bidirectional, relatedAttribute должен быть null
      if (relatedAttribute != null) {
        errors.add("Non-bidirectional attributes cannot have relatedAttribute");
      }
    }

    return errors;
  }

  // Вспомогательные методы
  private AttributeCategory determineAttributeCategory(Class<?> javaType) {
    if (isCurrentlySupportedBasicType(javaType)) {
      return AttributeCategory.BASIC;
    }
    return AttributeCategory.ENTITY;
  }

  private boolean isCurrentlySupportedBasicType(Class<?> javaType) {
    return javaType == String.class
        || javaType == Boolean.class
        || javaType == boolean.class
        || javaType == Integer.class
        || javaType == int.class
        || javaType.isEnum()
        || javaType == OffsetDateTime.class;
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

  private static final org.slf4j.Logger logger =
      org.slf4j.LoggerFactory.getLogger(MetaEntityValidationService.class);
}
