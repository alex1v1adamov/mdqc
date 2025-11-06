package example.repo;

import com.cosium.spring.data.jpa.entity.graph.repository.EntityGraphJpaSpecificationExecutor;
import example.models.policy.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface PermissionRepository extends
        JpaRepository<Permission, UUID>,
        JpaSpecificationExecutor<Permission>,
        EntityGraphJpaSpecificationExecutor<Permission>,
        QuerydslPredicateExecutor<Permission> {
}