package example.repo;

import com.cosium.spring.data.jpa.entity.graph.repository.EntityGraphJpaSpecificationExecutor;
import example.models.meta.MetaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface MetaEntityRepository extends
        JpaRepository<MetaEntity, String>,
        JpaSpecificationExecutor<MetaEntity>,
        EntityGraphJpaSpecificationExecutor<MetaEntity> {
}