package example.repo;

import example.models.meta.MetaAttribute;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface MetaAttributeRepository
    extends JpaRepository<MetaAttribute, UUID>, JpaSpecificationExecutor<MetaAttribute> {}
