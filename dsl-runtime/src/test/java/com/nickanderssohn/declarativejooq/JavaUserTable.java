package com.nickanderssohn.declarativejooq;

import org.jooq.ForeignKey;
import org.jooq.Identity;
import org.jooq.TableField;
import org.jooq.UniqueKey;
import org.jooq.impl.DSL;
import org.jooq.impl.Internal;
import org.jooq.impl.SQLDataType;
import org.jooq.impl.TableImpl;

import java.util.Arrays;
import java.util.List;

public class JavaUserTable extends TableImpl<JavaUserRecord> {

    public static final JavaUserTable JAVA_USER = new JavaUserTable();

    public final TableField<JavaUserRecord, Long> ID =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this);

    public final TableField<JavaUserRecord, String> NAME =
        createField(DSL.name("name"), SQLDataType.VARCHAR(255).nullable(false), this);

    public final TableField<JavaUserRecord, String> EMAIL =
        createField(DSL.name("email"), SQLDataType.VARCHAR(255).nullable(false), this);

    public final TableField<JavaUserRecord, Long> ORGANIZATION_ID =
        createField(DSL.name("organization_id"), SQLDataType.BIGINT.nullable(false), this);

    private JavaUserTable() {
        super(DSL.name("user"));
    }

    @Override
    public Class<JavaUserRecord> getRecordType() {
        return JavaUserRecord.class;
    }

    @Override
    public UniqueKey<JavaUserRecord> getPrimaryKey() {
        return Internal.createUniqueKey(this, DSL.name("pk_java_user"), new TableField[]{ID}, true);
    }

    @Override
    public Identity<JavaUserRecord, ?> getIdentity() {
        return Internal.createIdentity(this, ID);
    }

    @Override
    public List<ForeignKey<JavaUserRecord, ?>> getReferences() {
        return Arrays.asList(
            Internal.createForeignKey(
                this,
                DSL.name("fk_java_user_organization"),
                new TableField[]{ORGANIZATION_ID},
                JavaOrganizationTable.JAVA_ORGANIZATION.getPrimaryKey(),
                new TableField[]{JavaOrganizationTable.JAVA_ORGANIZATION.ID},
                false
            )
        );
    }
}
