package example.controllers;

import example.models.predicate.PredicateDefinition;

import example.models.predicate.QPredicateDefinition;
import example.repo.PredicateDefinitionRepository;
import example.service.ConsolePredicateVisualizer;
import lombok.RequiredArgsConstructor;
import org.springframework.shell.standard.ShellComponent;
import org.springframework.shell.standard.ShellMethod;
import org.springframework.shell.standard.ShellOption;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@ShellComponent
@RequiredArgsConstructor
public class PredicateVisualizationController {

    private final PredicateDefinitionRepository predicateDefinitionRepository;
    private final ConsolePredicateVisualizer visualizer;

    @ShellMethod(key = "show-predicate", value = "Show predicate structure diagram")
    public String showPredicate(@ShellOption UUID id) {

        
        return visualizer.visualize(id);
    }

    @ShellMethod(key = "list-predicates", value = "List all available predicates")
    public String listPredicates() {
        List<PredicateDefinition> predicates = predicateDefinitionRepository.findAll();
        
        if (predicates.isEmpty()) {
            return "No predicates found";
        }
        
        StringBuilder sb = new StringBuilder("Available predicates:\n");
        for (PredicateDefinition predicate : predicates) {
            sb.append("- ").append(predicate.getName())
              .append(" (ID: ").append(predicate.getId())
              .append(", Entity: ").append(predicate.getMetaEntity().getName())
              .append(")\n");
        }
        
        return sb.toString();
    }

    @ShellMethod(key = "show-all-predicates", value = "Show diagrams for all predicates")
    public String showAllPredicates() {
        List<PredicateDefinition> predicates = predicateDefinitionRepository.findAll();
        
        if (predicates.isEmpty()) {
            return "No predicates found";
        }
        
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < predicates.size(); i++) {
            PredicateDefinition predicate = predicates.get(i);
            sb.append("=== PREDICATE ").append(i + 1).append(" of ").append(predicates.size()).append(" ===\n");
            sb.append(visualizer.visualize(predicate.getId()));
            sb.append("\n".repeat(3));
        }
        
        return sb.toString();
    }
}