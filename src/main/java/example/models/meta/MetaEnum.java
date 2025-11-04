package example.models.meta;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Мета-описание перечисления (enum)
 */
@Entity
@Table(name = "meta_enum", schema = "meta")
@Getter
@Setter
public class MetaEnum {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /**
     * Имя enum'а в системе
     */
    @Column(name = "name", nullable = false, unique = true)
    private String name;

    /**
     * Описание enum'а
     */
    @Column(name = "description")
    private String description;

    /**
     * Полное имя класса enum'а (например, "com.example.OrderStatus")
     */
    @Column(name = "class_name")
    private String className;

    /**
     * Все возможные значения этого enum'а
     */
    @OneToMany(mappedBy = "metaEnum", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<MetaEnumValue> values = new ArrayList<>();
}