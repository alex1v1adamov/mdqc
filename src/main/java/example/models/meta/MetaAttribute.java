package example.models.meta;


import com.yahoo.elide.annotation.Include;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.Immutable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Мета-описание атрибута бизнес-сущности
 * Используется для сравнения с Jakarta Metamodel
 */
@Entity
@Include
@Table(name = "meta_attribute", schema = "meta")
@Immutable
@Getter
@Setter
public class MetaAttribute {

    /**
     * Уникальный идентификатор атрибута
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Имя атрибута в метамодели (например, "nameInNms" для коллекции)
     */
    @Column(nullable = false)
    private String name;


    /**
     * Сущность, к которой принадлежит данный атрибут
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "entity_id", nullable = false)
    private MetaEntity entity;



    /**
     * Тип атрибута: SINGULAR - одиночное значение, PLURAL - коллекция
     */
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttributeType type;

    /**
     * Категория типа данных атрибута. для AttributeType.SINGULAR - тип аттрибута, для AttributeType.PLURAL -  тип элемента
     * - BASIC: простой тип (String, Integer, LocalDate, etc.)
     * - ENTITY: ссылка на другую сущность
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "attribute_category", nullable = false)
    private AttributeCategory attributeCategory;

    /**
     * тип для AttributeCategory.BASIC
     */
    @Column(name = "basic_type")
    private String attributeBasicType;

    /**
     * тип для AttributeCategory.ENTITY
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attribute_entity_type_id")
    private MetaEntity attributeEntityType;


    /**
     * Флаг bidirectional связи
     * true - атрибут является частью двусторонней связи
     */
    @Column(name = "is_bidirectional", nullable = false)
    private Boolean isBidirectional = false;

    /**
     * Связанный атрибут в bidirectional связи
     * Указывает на атрибут в связанной сущности, который ссылается обратно на этот атрибут
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "related_attribute_id")
    private MetaAttribute relatedAttribute;



    /**
     * Обратные ссылки для bidirectional связей
     */
    @OneToMany(mappedBy = "relatedAttribute")
    private Set<MetaAttribute> inverseRelatedAttributes = new HashSet<>();


}

