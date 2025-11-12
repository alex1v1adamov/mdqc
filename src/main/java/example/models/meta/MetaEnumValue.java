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
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** Значение перечисления */
@Entity
@Table(name = "meta_enum_value", schema = "meta")
@Getter
@Setter
public class MetaEnumValue {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  /** Имя значения (например, "DOG") */
  @Column(name = "name", nullable = false)
  private String name;

  /** Значение для хранения в БД (например, "DOG", "ACT", 1) */
  @Column(name = "storage_value", nullable = false)
  private String storageValue;

  /** Человеко-читаемое описание */
  @Column(name = "description")
  private String description;

  /** Ссылка на enum */
  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "meta_enum_id", nullable = false)
  private MetaEnum metaEnum;
}
