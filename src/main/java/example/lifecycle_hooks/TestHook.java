package example.lifecycle_hooks;

import com.yahoo.elide.annotation.LifeCycleHookBinding;
import com.yahoo.elide.core.lifecycle.LifeCycleHook;
import com.yahoo.elide.core.security.ChangeSpec;
import com.yahoo.elide.core.security.RequestScope;
import example.models.vet.Clinic;
import java.util.Optional;


public class TestHook implements LifeCycleHook<Clinic> {

  @Override
  public void execute(
      final LifeCycleHookBinding.Operation operation,
      final LifeCycleHookBinding.TransactionPhase phase,
      final Clinic elideEntity,
      final RequestScope requestScope,
      final Optional<ChangeSpec> changes) {
    System.out.println("!!!!!!!!!!!!TestHook!!!!!!!!!!!!!!");
  }
}
