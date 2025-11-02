package example.models.predicate;

import example.models.meta.MetaAttribute;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/* Выражение пути для навигации по связям между сущностями
 */
@Entity
@Table(name = "predicate_path_expression", schema = "predicate")
@Getter
@Setter
public class PredicatePathExpression {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    /* Корневой атрибут (начало пути)
     * Например, для site.isResearch - это атрибут "site" в BaseStation
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "root_attribute_id", nullable = false)
    private MetaAttribute rootAttribute;

    /* Цепочка атрибутов для навигации
     * Порядок определяется порядком в списке через @OrderColumn
     * Например, для site.isResearch: [isResearch]
     */
    @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true)
    @JoinTable(
            name = "predicate_path_expression_attributes",
            schema = "predicate",
            joinColumns = @JoinColumn(name = "path_expression_id"),
            inverseJoinColumns = @JoinColumn(name = "meta_attribute_id")
    )
    @OrderColumn(name = "attribute_order")
    private List<MetaAttribute> pathAttributes = new ArrayList<>();

    /* Конечный атрибут (последний в цепочке)
     * Вычисляется автоматически, но хранится для удобства
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_attribute_id")
    private MetaAttribute targetAttribute;
}