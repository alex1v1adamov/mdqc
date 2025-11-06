/*
 * Copyright 2019, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package example.models;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yahoo.elide.annotation.Include;
import com.yahoo.elide.annotation.LifeCycleHookBinding;
import com.yahoo.elide.annotation.UpdatePermission;
import com.yahoo.elide.graphql.subscriptions.annotations.Subscription;
import com.yahoo.elide.graphql.subscriptions.annotations.SubscriptionField;
import example.lifecycle_hooks.TestHook;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

import static com.yahoo.elide.annotation.LifeCycleHookBinding.Operation.UPDATE;
import static com.yahoo.elide.annotation.LifeCycleHookBinding.TransactionPhase.PRECOMMIT;

@Include
@Table(schema = "ri")
@Entity
@Subscription
@Data
@LifeCycleHookBinding(operation = UPDATE, phase = PRECOMMIT, hook = TestHook.class)
@UpdatePermission(expression = "RSMD")
@Getter
@Setter
public class Site {
    @Id
    private String id = "";

    @SubscriptionField
    @Column(name = "site_name")
    private String siteName = "";

    @SubscriptionField
    @Column(name = "is_research")
    private Boolean isResearch;

    @SubscriptionField
    @OneToMany(mappedBy = "site")
    @JsonIgnore
    private List<BaseStation> baseStations = new ArrayList<>();
}
