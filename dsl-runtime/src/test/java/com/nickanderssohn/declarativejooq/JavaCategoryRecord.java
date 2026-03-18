package com.nickanderssohn.declarativejooq;

import org.jooq.impl.UpdatableRecordImpl;

public class JavaCategoryRecord extends UpdatableRecordImpl<JavaCategoryRecord> {

    public JavaCategoryRecord() {
        super(JavaCategoryTable.JAVA_CATEGORY);
    }

    public Long getId() {
        return get(JavaCategoryTable.JAVA_CATEGORY.ID);
    }

    public void setId(Long value) {
        set(JavaCategoryTable.JAVA_CATEGORY.ID, value);
    }

    public String getName() {
        return get(JavaCategoryTable.JAVA_CATEGORY.NAME);
    }

    public void setName(String value) {
        set(JavaCategoryTable.JAVA_CATEGORY.NAME, value);
    }

    public Long getParentId() {
        return get(JavaCategoryTable.JAVA_CATEGORY.PARENT_ID);
    }

    public void setParentId(Long value) {
        set(JavaCategoryTable.JAVA_CATEGORY.PARENT_ID, value);
    }
}
