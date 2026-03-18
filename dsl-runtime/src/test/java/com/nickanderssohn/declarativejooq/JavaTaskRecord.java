package com.nickanderssohn.declarativejooq;

import org.jooq.impl.UpdatableRecordImpl;

public class JavaTaskRecord extends UpdatableRecordImpl<JavaTaskRecord> {

    public JavaTaskRecord() {
        super(JavaTaskTable.JAVA_TASK);
    }

    public Long getId() {
        return get(JavaTaskTable.JAVA_TASK.ID);
    }

    public void setId(Long value) {
        set(JavaTaskTable.JAVA_TASK.ID, value);
    }

    public String getTitle() {
        return get(JavaTaskTable.JAVA_TASK.TITLE);
    }

    public void setTitle(String value) {
        set(JavaTaskTable.JAVA_TASK.TITLE, value);
    }

    public Long getCreatedBy() {
        return get(JavaTaskTable.JAVA_TASK.CREATED_BY);
    }

    public void setCreatedBy(Long value) {
        set(JavaTaskTable.JAVA_TASK.CREATED_BY, value);
    }

    public Long getUpdatedBy() {
        return get(JavaTaskTable.JAVA_TASK.UPDATED_BY);
    }

    public void setUpdatedBy(Long value) {
        set(JavaTaskTable.JAVA_TASK.UPDATED_BY, value);
    }
}
