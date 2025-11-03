/*
 * Copyright 2019, Yahoo Inc.
 * Licensed under the Apache License, Version 2.0
 * See LICENSE file in project root for terms.
 */
package example.models;

import com.yahoo.elide.annotation.Include;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Include(rootLevel = false, name = "baseStation")
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
