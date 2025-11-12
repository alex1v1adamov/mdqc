package example.repo;

import example.models.vet.Clinic;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.querydsl.QuerydslPredicateExecutor;
import org.springframework.stereotype.Repository;

@Repository
public interface ClinicRepository
    extends JpaRepository<Clinic, UUID>,
        JpaSpecificationExecutor<Clinic>,
        QuerydslPredicateExecutor<Clinic> {}
