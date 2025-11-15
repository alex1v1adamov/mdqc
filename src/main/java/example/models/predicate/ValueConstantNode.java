package example.models.predicate;

import example.models.meta.BasicType;
import jakarta.persistence.*;
import java.util.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("VALUE_CONSTANT")
@Getter
@Setter
public class ValueConstantNode extends PredicateNode {

  @OneToOne(cascade = CascadeType.ALL, orphanRemoval = true)
  @JoinColumn(name = "value_id")
  private PredicateNodeValue value;

  @OneToMany(cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
  @JoinTable(
      name = "predicate_node_in_values",
      schema = "predicate",
      joinColumns = @JoinColumn(name = "predicate_node_id"),
      inverseJoinColumns = @JoinColumn(name = "node_value_id"))
  @OrderColumn(name = "value_order")
  private Set<PredicateNodeValue> values = new HashSet<>();

  @Override
  public BasicType getNodeReturnType() {
    return value != null
        ? value.getValueType()
        : values.stream().map(PredicateNodeValue::getValueType).findAny().orElse(null);
  }
}
