package example.models.meta;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

/**
 * Значение перечисления
 */
@Entity
@Table(name = "meta_enum_value", schema = "meta")
@Getter
@Setter
public class MetaEnumValue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Имя значения (например, "ACTIVE")
     */
    @Column(name = "name", nullable = false)
    private String name;

    /**
     * Значение для хранения в БД (например, "active", "ACT", 1)
     */
    @Column(name = "storage_value", nullable = false)
    private String storageValue;

    /**
     * Человеко-читаемое описание
     */
    @Column(name = "description")
    private String description;


    /**
     * Ссылка на enum
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "meta_enum_id", nullable = false)
    private MetaEnum metaEnum;
}