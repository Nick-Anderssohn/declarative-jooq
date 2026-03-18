package com.nickanderssohn.declarativejooq;

import org.jooq.impl.UpdatableRecordImpl;

public class JavaOrganizationRecord extends UpdatableRecordImpl<JavaOrganizationRecord> {

    public JavaOrganizationRecord() {
        super(JavaOrganizationTable.JAVA_ORGANIZATION);
    }

    public Long getId() {
        return get(JavaOrganizationTable.JAVA_ORGANIZATION.ID);
    }

    public void setId(Long value) {
        set(JavaOrganizationTable.JAVA_ORGANIZATION.ID, value);
    }

    public String getName() {
        return get(JavaOrganizationTable.JAVA_ORGANIZATION.NAME);
    }

    public void setName(String value) {
        set(JavaOrganizationTable.JAVA_ORGANIZATION.NAME, value);
    }
}
