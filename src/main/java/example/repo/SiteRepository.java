package example.repo;

import example.models.Site;
import example.models.meta.MetaAttribute;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface SiteRepository
    extends JpaRepository<Site, UUID>,
        JpaSpecificationExecutor<Site>,
        QuerydslPredicateExecutor<Site> {}
