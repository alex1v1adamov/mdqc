package example.models.meta;

/** Тип атрибута */
public enum AttributeType {
  /** Одиночное значение (поле или ссылка на одну сущность) */
  SINGULAR,

  /** Коллекция значений или сущностей (List, Set, Map, Collection) */
  PLURAL
}
