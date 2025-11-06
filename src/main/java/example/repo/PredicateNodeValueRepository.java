package example.repo;

import com.cosium.spring.data.jpa.entity.graph.repository.EntityGraphJpaSpecificationExecutor;
import example.models.predicate.PredicateNode;
import example.models.predicate.PredicateNodeValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PredicateNodeValueRepository extends
        JpaRepository<PredicateNodeValue, UUID>,
        JpaSpecificationExecutor<PredicateNodeValue>,
        EntityGraphJpaSpecificationExecutor<PredicateNodeValue> {
}