package example.service;

/** alexander.adamov created on 07.11.2025 */
public interface Validate<T> {

  ValidationResult validate(T object);
}
