package example.models.predicate;

import example.models.meta.BasicType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/* Универсальное хранилище значений для предикатов
 */
@Entity
@Table(name = "predicate_node_value", schema = "predicate")
@Getter
@Setter
public class PredicateNodeValue {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    // === ТИПОБЕЗОПАСНЫЕ ПОЛЯ ===

    @Column(name = "string_value")
    private String stringValue;

    @Column(name = "boolean_value")
    private Boolean booleanValue;

    @Column(name = "integer_value")
    private Integer integerValue;

    @Column(name = "long_value")
    private Long longValue;

    @Column(name = "date_value")
    private LocalDate dateValue;

    @Column(name = "timestamp_value")
    private LocalDateTime timestampValue;

    @Column(name = "enum_value")
    private String enumValue; // храним как строку

    /* Тип значения - определяет, какое поле использовать
     */
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false)
    private BasicType valueType;

    // === МЕТОДЫ ДЛЯ РАБОТЫ СО ЗНАЧЕНИЯМИ ===

    public void setValue(Object value, BasicType type) {
        this.valueType = type;

        // Очищаем все поля перед установкой нового значения
        this.stringValue = null;
        this.booleanValue = null;
        this.integerValue = null;
        this.longValue = null;
        this.dateValue = null;
        this.timestampValue = null;
        this.enumValue = null;

        if (value == null) return;

        switch (type) {
            case STRING -> this.stringValue = (String) value;
            case BOOLEAN -> this.booleanValue = (Boolean) value;
            case INTEGER -> this.integerValue = (Integer) value;
            case DATE -> this.dateValue = (LocalDate) value;
            case ENUM -> this.enumValue = value.toString();
        }
    }

    public Object getValue() {
        if (valueType == null) return null;

        return switch (valueType) {
            case STRING -> stringValue;
            case BOOLEAN -> booleanValue;
            case INTEGER -> integerValue;
            case DATE -> dateValue;
            case ENUM -> enumValue;
        };
    }
}