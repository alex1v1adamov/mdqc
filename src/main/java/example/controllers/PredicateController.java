package example.controllers;

import example.service.PredicateStringConverter;
import example.models.predicate.PredicateDefinition;
import example.repo.PredicateDefinitionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PredicateController {
    
    private final PredicateStringConverter predicateConverter;
    private final PredicateDefinitionRepository predicateRepository;
    
    @GetMapping("/predicate/{id}/string")
    public String getPredicateAsString(@PathVariable UUID id) {
        PredicateDefinition predicate = predicateRepository.findById(id)
            .orElseThrow(() -> new RuntimeException("Predicate not found"));
            
        return predicateConverter.convertToString(predicate);
    }
}