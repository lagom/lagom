/*
 * Copyright (C) 2016-2017 Lightbend Inc. <https://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.persistence.jpa;

import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;

@Entity
public class TestJpaEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Integer id;
    private String parentId;
    private String element;

    public TestJpaEntity(String parentId, String element) {
        this.parentId = parentId;
        this.element = element;
    }

    public TestJpaEntity() {
        this(null, null);
    }

    public Integer getId() {
        return id;
    }

    public void setId(Integer id) {
        this.id = id;
    }

    public String getParentId() {
        return parentId;
    }

    public void setParentId(String parentId) {
        this.parentId = parentId;
    }

    public String getElement() {
        return element;
    }

    public void setElement(String element) {
        this.element = element;
    }
}
