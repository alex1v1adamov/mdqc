package example.repo;

import com.cosium.spring.data.jpa.entity.graph.repository.EntityGraphJpaSpecificationExecutor;
import example.models.meta.MetaAttribute;
import example.models.meta.MetaEntity;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface MetaEntityRepository
        extends JpaRepository<MetaEntity, UUID>,
        EntityGraphJpaSpecificationExecutor<MetaEntity>,
        JpaSpecificationExecutor<MetaEntity>,
        QuerydslPredicateExecutor<MetaEntity> {}