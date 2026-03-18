package com.nickanderssohn.declarativejooq;

import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

import java.util.Collections;
import java.util.List;

public class JavaOrganizationTable extends TableImpl<JavaOrganizationRecord> {

    public static final JavaOrganizationTable JAVA_ORGANIZATION = new JavaOrganizationTable();

    public final TableField<JavaOrganizationRecord, Long> ID =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this);

    public final TableField<JavaOrganizationRecord, String> NAME =
        createField(DSL.name("name"), SQLDataType.VARCHAR(255).nullable(false), this);

    private JavaOrganizationTable() {
        super(DSL.name("organization"));
    }

    @Override
    public Class<JavaOrganizationRecord> getRecordType() {
        return JavaOrganizationRecord.class;
    }

    @Override
    public UniqueKey<JavaOrganizationRecord> getPrimaryKey() {
        return Internal.createUniqueKey(this, DSL.name("pk_java_organization"), new TableField[]{ID}, true);
    }

    @Override
    public Identity<JavaOrganizationRecord, ?> getIdentity() {
        return Internal.createIdentity(this, ID);
    }

    @Override
    public List<ForeignKey<JavaOrganizationRecord, ?>> getReferences() {
        return Collections.emptyList();
    }
}
