package example.models.predicate;

import example.models.meta.BasicType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@DiscriminatorValue("EVALUATION_OPERATION")
@Getter
@Setter
public class EvaluationOperationNode extends PredicateNode {

  @Enumerated(EnumType.STRING)
  @Column(name = "operator_type", nullable = false)
  private OperatorType operatorType;

  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "left_operand_id")
  private PredicateNode leftOperand;

  @OneToOne(cascade = CascadeType.ALL)
  @JoinColumn(name = "right_operand_id")
  private PredicateNode rightOperand;

  @Override
  public BasicType getNodeReturnType() {
    return operatorType.getResultType();
  }
}
