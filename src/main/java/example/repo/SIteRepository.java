package example.repo;


import example.models.Site;
import example.models.meta.MetaAttribute;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SIteRepository
        extends JpaRepository<MetaAttribute, UUID>, JpaSpecificationExecutor<Site>,
        QuerydslPredicateExecutor<Site> {
    

}