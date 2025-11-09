package example.repo;

import com.cosium.spring.data.jpa.entity.graph.repository.EntityGraphJpaSpecificationExecutor;
import example.models.predicate.PredicateNodeValue;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PredicateNodeValueRepository
    extends JpaRepository<PredicateNodeValue, UUID>,
        JpaSpecificationExecutor<PredicateNodeValue>,
        EntityGraphJpaSpecificationExecutor<PredicateNodeValue> {}
