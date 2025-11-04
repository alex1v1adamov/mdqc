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
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@UpdatePermission(expression = "RSMD")
@Include(rootLevel = true, name = "baseStation")
@Table(schema = "ri", name = "base_station")
@Entity
@Getter
@Setter
public class BaseStation {
    @Id
    private String id = "";

    @Column(name = "name_in_nms")
    private String nameInNms = "";

    @ManyToOne
    private Site site = null;


}
