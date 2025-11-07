package example.service;

import example.models.meta.AttributeCategory;
import example.models.meta.BasicType;
import example.models.meta.MetaAttribute;
import example.models.meta.MetaEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@Transactional(readOnly = true)
public class MetaEntityValidationService implements Validate<MetaEntity> {

    /**
     * Валидация всей целостности мета-сущности
     */
    public ValidationResult validate(MetaEntity metaEntity) {
        List<String> errors = new ArrayList<>();

        if (metaEntity == null) {
            return ValidationResult.error("MetaEntity cannot be null");
        }

        // Валидация базовых полей
        if (metaEntity.getName() == null || metaEntity.getName().trim().isEmpty()) {
            errors.add("MetaEntity name cannot be null or empty");
        }

        // Валидация атрибутов сущности
        if (metaEntity.getAttributes() != null) {
            for (MetaAttribute attribute : metaEntity.getAttributes()) {
                ValidationResult attributeResult = validateMetaAttribute(attribute);
                if (!attributeResult.isValid()) {
                    errors.add("Attribute '" + attribute.getName() + "': " +
                            String.join(", ", attributeResult.getErrors()));
                }
            }
        }

        return errors.isEmpty() ?
                ValidationResult.success() :
                ValidationResult.error(errors);
    }

    /**
     * Валидация MetaAttribute с учетом всех правил из комментариев
     */
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

        return errors.isEmpty() ?
                ValidationResult.success() :
                ValidationResult.error(errors);
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

                if (relatedAttribute.getRelatedAttribute() == null ||
                        !relatedAttribute.getRelatedAttribute().getId().equals(attribute.getId())) {
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


}