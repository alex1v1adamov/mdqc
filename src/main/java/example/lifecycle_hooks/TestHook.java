package example.lifecycle_hooks;

import com.yahoo.elide.annotation.LifeCycleHookBinding;
import com.yahoo.elide.core.lifecycle.LifeCycleHook;
import com.yahoo.elide.core.security.ChangeSpec;
import com.yahoo.elide.core.security.RequestScope;
import example.models.Site;

import java.util.Optional;

/**
 * alexander.adamov created on 30.10.2025
 */
public class TestHook implements LifeCycleHook<Site> {


    @Override
    public void execute(final LifeCycleHookBinding.Operation operation,
                        final LifeCycleHookBinding.TransactionPhase phase,
                        final Site elideEntity, final RequestScope requestScope,
                        final Optional<ChangeSpec> changes) {
        System.out.println("!!!!!!!!!!!!TestHook!!!!!!!!!!!!!!");
    }
}
