package example.models.predicate;

import com.yahoo.elide.annotation.Include;
import example.models.meta.BasicType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Include(rootLevel = false)
@Entity
@DiscriminatorValue("PATH_EXPRESSION")
@Getter
@Setter
public class PathExpressionNode extends PredicateNode {

  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "path_expression_id")
  private PredicatePathExpression pathExpression;

  @Override
  public BasicType getNodeReturnType() {
    return pathExpression.getFinalPathAttribute().getBasicType();
  }
}
