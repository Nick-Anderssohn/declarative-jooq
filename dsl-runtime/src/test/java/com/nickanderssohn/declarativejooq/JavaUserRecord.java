package com.nickanderssohn.declarativejooq;

import org.jooq.impl.UpdatableRecordImpl;

public class JavaUserRecord extends UpdatableRecordImpl<JavaUserRecord> {

    public JavaUserRecord() {
        super(JavaUserTable.JAVA_USER);
    }

    public Long getId() {
        return get(JavaUserTable.JAVA_USER.ID);
    }

    public void setId(Long value) {
        set(JavaUserTable.JAVA_USER.ID, value);
    }

    public String getName() {
        return get(JavaUserTable.JAVA_USER.NAME);
    }

    public void setName(String value) {
        set(JavaUserTable.JAVA_USER.NAME, value);
    }

    public String getEmail() {
        return get(JavaUserTable.JAVA_USER.EMAIL);
    }

    public void setEmail(String value) {
        set(JavaUserTable.JAVA_USER.EMAIL, value);
    }

    public Long getOrganizationId() {
        return get(JavaUserTable.JAVA_USER.ORGANIZATION_ID);
    }

    public void setOrganizationId(Long value) {
        set(JavaUserTable.JAVA_USER.ORGANIZATION_ID, value);
    }
}
