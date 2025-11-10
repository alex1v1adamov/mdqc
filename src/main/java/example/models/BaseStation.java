/*
 * Copyright 2019, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package example.models;

import com.yahoo.elide.annotation.Include;
import com.yahoo.elide.annotation.UpdatePermission;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@UpdatePermission(expression = "RSMD")
@Include(name = "baseStation")
@Table(schema = "ri", name = "base_station")
@Entity
@Getter
@Setter
public class BaseStation {
  @Id private String id = "";

  @Column(name = "name_in_nms")
  private String nameInNms = "";

  @Enumerated(EnumType.STRING)
  @Column(name = "status")
  BsStatus bsStatus;

  @Column(name = "bs_date_time")
  OffsetDateTime bsDateTime;

    Integer rating;

    @ManyToOne private Site site = null;
}
