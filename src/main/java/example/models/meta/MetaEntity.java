package example.models.meta;


import com.yahoo.elide.annotation.Include;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldNameConstants;
import org.hibernate.annotations.BatchSize;
import org.hibernate.annotations.Fetch;
import org.hibernate.annotations.FetchMode;
import org.hibernate.annotations.Immutable;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Мета-описание бизнес-сущности
 * Соответствует JPA Entity классу и используется для сравнения с Jakarta Metamodel
 */
@Entity
@Include
@Immutable
@Table(name = "meta_entity", schema = "meta")
@Getter
@Setter
@FieldNameConstants
public class MetaEntity {

    /**
     * Уникальный идентификатор сущности
     */
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Имя сущности в метамодели (короткое имя, например "User")
     */
    @Column(nullable = false)
    private String name;

    /**
     * Все атрибуты (поля) данной сущности
     */
    @OneToMany(mappedBy = "entity", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @BatchSize(size = 50)
    private Set<MetaAttribute> attributes = new HashSet<>();

    /**
     * другие поля типа этой сущности
     */
    @OneToMany(mappedBy = "attributeEntityType", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Fetch(FetchMode.SUBSELECT)
    @BatchSize(size = 50)
    private Set<MetaAttribute> entityAttributes = new HashSet<>();


}