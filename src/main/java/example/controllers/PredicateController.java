package example.controllers;

import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.core.types.dsl.PathBuilder;
import com.querydsl.jpa.impl.JPAQuery;
import example.models.meta.MetaEntity;
import example.models.predicate.PredicateDefinition;
import example.repo.MetaEntityRepository;
import example.repo.PredicateDefinitionRepository;
import example.service.generator.PredicateGeneratorService;
import example.service.generator.PredicateReverseGeneratorService;
import example.service.validation.MetaEntityValidationService;
import example.service.validation.PredicateValidationService;
import example.service.validation.ValidationResult;
import jakarta.persistence.EntityManager;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class PredicateController {

  //  private final PredicateStringConverter predicateConverter;
  private final PredicateDefinitionRepository predicateRepository;
  private final MetaEntityRepository metaEntityRepository;
  private final EntityManager entityManager;
  private final PredicateGeneratorService predicateGeneratorService;
  private final PredicateValidationService metaDataValidationService;
  private final MetaEntityValidationService metaEntityValidationService;
  private final PredicateReverseGeneratorService predicateReverseGeneratorService;



  @SneakyThrows
  @GetMapping("/predicate/{id}/validate")
  public ValidationResult validatePredicate(@PathVariable UUID id) {
    PredicateDefinition predicate =
        predicateRepository
            .findById(id)
            .orElseThrow(() -> new RuntimeException("Predicate not found"));
    return metaDataValidationService.validate(predicate);
  }

  @SneakyThrows
  @GetMapping("/meta-entity/{id}/validate")
  public ValidationResult validateMetaEntity(@PathVariable UUID id) {
    MetaEntity predicate =
        metaEntityRepository
            .findById(id)
            .orElseThrow(() -> new RuntimeException("MetaEntity not found"));
    return metaEntityValidationService.validate(predicate);
  }

  @SneakyThrows
  @GetMapping("/predicate/{id}/matching")
  public List<?> getMatching(@PathVariable UUID id) {
    PredicateDefinition predicate =
        predicateRepository
            .findById(id)
            .orElseThrow(() -> new RuntimeException("Predicate not found"));

    String entityName = predicate.getMetaEntity().getName(); // пример: example.models.vet.Clinic
    Class<?> aClass = Class.forName(entityName);
    // Вместо Q-class создаем PathBuilder
    PathBuilder<?> entity = new PathBuilder<>(aClass, "entity");

    JPAQuery<?> query = new JPAQuery<>(entityManager);
    BooleanExpression booleanExpression = predicateGeneratorService.generatePredicate(predicate);

    List<?> fetch2 = query.select(entity).from(entity).where(booleanExpression).fetch();

    return fetch2;
  }


    @SneakyThrows
    @GetMapping("/predicate/{id}/reverse")
    public List<?> reverse(@PathVariable UUID id) {
        PredicateDefinition predicate =
                predicateRepository
                        .findById(id)
                        .orElseThrow(() -> new RuntimeException("Predicate not found"));

        String entityName = predicate.getMetaEntity().getName(); // пример: example.models.vet.Clinic
        Class<?> aClass = Class.forName(entityName);
        // Вместо Q-class создаем PathBuilder
        PathBuilder<?> entity = new PathBuilder<>(aClass, "entity");

        JPAQuery<?> query = new JPAQuery<>(entityManager);
        BooleanExpression booleanExpression = predicateGeneratorService.generatePredicate(predicate);

        PredicateDefinition predicateDefinition = predicateReverseGeneratorService.generateFromQueryDsl(booleanExpression, aClass);
        BooleanExpression booleanExpression1 = predicateGeneratorService.generatePredicate(predicateDefinition);
        List<?> fetch2 = query.select(entity).from(entity).where(booleanExpression1).fetch();

        return fetch2;
    }
}
