package example.models.predicate;

import com.yahoo.elide.annotation.Include;
import example.models.meta.BasicType;
import jakarta.persistence.*;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/*
 * Узел дерева предиката
 */
@Include(rootLevel = false)
@Entity
@Table(name = "predicate_node", schema = "predicate")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "node_type")
@Getter
@Setter
public abstract class PredicateNode {

  @Id
  @GeneratedValue(strategy = GenerationType.UUID)
  @Column(name = "id", updatable = false, nullable = false)
  private UUID id;

  //  @Enumerated(EnumType.STRING)
  //  @Column(name = "node_type", nullable = false, insertable = false, updatable = false)
  //  private NodeType nodeType;

  public abstract BasicType getNodeReturnType();
}
