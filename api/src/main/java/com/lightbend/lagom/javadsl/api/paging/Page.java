/*
 * Copyright (C) 2016 Lightbend Inc. <http://www.lightbend.com>
 */
package com.lightbend.lagom.javadsl.api.paging;

import java.util.Optional;

/**
 * A page object, use to capture paging information.
 */
public class Page {
    private final Optional<Integer> pageNo;
    private final Optional<Integer> pageSize;

    public Page(Optional<Integer> pageNo, Optional<Integer> pageSize) {
        this.pageNo = pageNo;
        this.pageSize = pageSize;
    }

    public Optional<Integer> pageNo() {
        return pageNo;
    }

    public Optional<Integer> pageSize() {
        return pageSize;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Page page = (Page) o;

        if (!pageNo.equals(page.pageNo)) return false;
        return pageSize.equals(page.pageSize);

    }

    @Override
    public int hashCode() {
        int result = pageNo.hashCode();
        result = 31 * result + pageSize.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "Page{" +
                "pageNo=" + pageNo +
                ", pageSize=" + pageSize +
                '}';
    }
}
