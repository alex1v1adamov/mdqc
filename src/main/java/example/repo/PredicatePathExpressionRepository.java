package example.repo;

import com.cosium.spring.data.jpa.entity.graph.repository.EntityGraphJpaSpecificationExecutor;
import example.models.predicate.PredicateNodeValue;
import example.models.predicate.PredicatePathExpression;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PredicatePathExpressionRepository extends
        JpaRepository<PredicatePathExpression, UUID>,
        JpaSpecificationExecutor<PredicatePathExpression>,
        EntityGraphJpaSpecificationExecutor<PredicatePathExpression> {
}