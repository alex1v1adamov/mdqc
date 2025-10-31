package example.repo;

import com.cosium.spring.data.jpa.entity.graph.repository.EntityGraphJpaSpecificationExecutor;
import example.models.meta.MetaEntity;
import example.models.policy.Permission;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface PermissionRepository extends
        JpaRepository<Permission, String>,
        JpaSpecificationExecutor<Permission>,
        EntityGraphJpaSpecificationExecutor<Permission> {
}