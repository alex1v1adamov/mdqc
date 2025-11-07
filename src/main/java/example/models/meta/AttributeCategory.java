package example.models.meta;

/**
 * Категория типа данных атрибута
 */
public enum AttributeCategory {
    /**
     * Простой тип: String, Integer, Long, Boolean, OffsetDateTime, etc.
     * Соответствует @Basic в JPA
     */
    BASIC,

    /**
     * Ссылка на другую сущность (@ManyToOne, @OneToOne)
     * Для SINGULAR типа - прямая ссылка
     * Для PLURAL типа - тип элементов коллекции
     */
    ENTITY

}
