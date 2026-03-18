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

public class JavaCategoryTable extends TableImpl<JavaCategoryRecord> {

    public static final JavaCategoryTable JAVA_CATEGORY = new JavaCategoryTable();

    public final TableField<JavaCategoryRecord, Long> ID =
        createField(DSL.name("id"), SQLDataType.BIGINT.identity(true), this);

    public final TableField<JavaCategoryRecord, String> NAME =
        createField(DSL.name("name"), SQLDataType.VARCHAR(255).nullable(false), this);

    public final TableField<JavaCategoryRecord, Long> PARENT_ID =
        createField(DSL.name("parent_id"), SQLDataType.BIGINT.nullable(true), this);

    private JavaCategoryTable() {
        super(DSL.name("category"));
    }

    @Override
    public Class<JavaCategoryRecord> getRecordType() {
        return JavaCategoryRecord.class;
    }

    @Override
    public UniqueKey<JavaCategoryRecord> getPrimaryKey() {
        return Internal.createUniqueKey(this, DSL.name("pk_java_category"), new TableField[]{ID}, true);
    }

    @Override
    public Identity<JavaCategoryRecord, ?> getIdentity() {
        return Internal.createIdentity(this, ID);
    }

    @Override
    public List<ForeignKey<JavaCategoryRecord, ?>> getReferences() {
        return Arrays.asList(
            Internal.createForeignKey(
                this,
                DSL.name("fk_java_category_parent"),
                new TableField[]{PARENT_ID},
                this.getPrimaryKey(),
                new TableField[]{ID},
                false
            )
        );
    }
}
