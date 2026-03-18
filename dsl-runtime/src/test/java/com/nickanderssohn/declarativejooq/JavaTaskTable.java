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

public class JavaTaskTable extends TableImpl<JavaTaskRecord> {

    public static final JavaTaskTable JAVA_TASK = new JavaTaskTable();

    public final TableField<JavaTaskRecord, Long> ID =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this);

    public final TableField<JavaTaskRecord, String> TITLE =
        createField(DSL.name("title"), SQLDataType.VARCHAR(255).nullable(false), this);

    public final TableField<JavaTaskRecord, Long> CREATED_BY =
        createField(DSL.name("created_by"), SQLDataType.BIGINT.nullable(false), this);

    public final TableField<JavaTaskRecord, Long> UPDATED_BY =
        createField(DSL.name("updated_by"), SQLDataType.BIGINT.nullable(true), this);

    private JavaTaskTable() {
        super(DSL.name("task"));
    }

    @Override
    public Class<JavaTaskRecord> getRecordType() {
        return JavaTaskRecord.class;
    }

    @Override
    public UniqueKey<JavaTaskRecord> getPrimaryKey() {
        return Internal.createUniqueKey(this, DSL.name("pk_java_task"), new TableField[]{ID}, true);
    }

    @Override
    public Identity<JavaTaskRecord, ?> getIdentity() {
        return Internal.createIdentity(this, ID);
    }

    @Override
    public List<ForeignKey<JavaTaskRecord, ?>> getReferences() {
        return Arrays.asList(
            Internal.createForeignKey(
                this,
                DSL.name("fk_java_task_created_by"),
                new TableField[]{CREATED_BY},
                JavaUserTable.JAVA_USER.getPrimaryKey(),
                new TableField[]{JavaUserTable.JAVA_USER.ID},
                false
            ),
            Internal.createForeignKey(
                this,
                DSL.name("fk_java_task_updated_by"),
                new TableField[]{UPDATED_BY},
                JavaUserTable.JAVA_USER.getPrimaryKey(),
                new TableField[]{JavaUserTable.JAVA_USER.ID},
                false
            )
        );
    }
}
