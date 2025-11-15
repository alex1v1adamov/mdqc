package example.service.validation;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;

@Getter
public class ValidationResult {
  private final boolean valid;
  private final List<String> errors;

  public ValidationResult(boolean valid, List<String> errors) {
    this.valid = valid;
    this.errors = errors != null ? errors : new ArrayList<>();
  }

  public static ValidationResult success() {
    return new ValidationResult(true, new ArrayList<>());
  }

  public static ValidationResult error(String error) {
    return new ValidationResult(false, List.of(error));
  }

  public static ValidationResult error(List<String> errors) {
    return new ValidationResult(false, errors);
  }
}
