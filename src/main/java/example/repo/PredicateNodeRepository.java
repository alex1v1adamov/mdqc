package example.repo;

import com.cosium.spring.data.jpa.entity.graph.repository.EntityGraphJpaSpecificationExecutor;
import example.models.predicate.PredicateNode;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PredicateNodeRepository
    extends JpaRepository<PredicateNode, UUID>,
        JpaSpecificationExecutor<PredicateNode>,
        EntityGraphJpaSpecificationExecutor<PredicateNode> {}
