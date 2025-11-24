/*
 * Copyright 2019, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package example.models.vet;

import static com.yahoo.elide.annotation.LifeCycleHookBinding.Operation.UPDATE;
import static com.yahoo.elide.annotation.LifeCycleHookBinding.TransactionPhase.POSTCOMMIT;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.yahoo.elide.annotation.CreatePermission;
import com.yahoo.elide.annotation.Include;
import com.yahoo.elide.annotation.LifeCycleHookBinding;
import com.yahoo.elide.annotation.UpdatePermission;
import com.yahoo.elide.graphql.subscriptions.annotations.Subscription;
import example.lifecycle_hooks.TestHook;
import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.annotations.GenericGenerator;
import org.locationtech.jts.geom.Point;

@Include
@Table(schema = "vet")
@Entity
@Subscription
@Data
@LifeCycleHookBinding(operation = UPDATE, phase = POSTCOMMIT, hook = TestHook.class)
@UpdatePermission(expression = "FGAS.UPDATE")
@CreatePermission(expression = "FGAS.CREATE")
@Getter
@Setter
public class Clinic {

  @Id
  @GeneratedValue(generator = "UUID")
  @GenericGenerator(name = "UUID", strategy = "org.hibernate.id.UUIDGenerator")
  private String id = "";

  //  @SubscriptionField
  @Column(name = "clinic_name")
  private String clinicName = "";

  //  @SubscriptionField
  @Column(name = "is_open")
  private Boolean isOpen = false;

  //  @SubscriptionField
  @OneToMany(mappedBy = "clinic")
  @JsonIgnore
  private List<Pet> pets = new ArrayList<>();

  //        @JPQLFilterFragment(
  //                operator = Operator.NOTEMPTY ,  // Repurpose this operator
  //                generator = WithinRadiusGenerator.class
  //        )
  //

  private Point geometry;
}
