package com.nickanderssohn.declarativejooq.codegen.emitter

import com.nickanderssohn.declarativejooq.codegen.ir.ForeignKeyIR
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class FkWhenDiscriminatorTest {

    private fun fk(
        name: String,
        vararg exprs: String,
    ) = ForeignKeyIR(
        fkName = name,
        childTableName = "child",
        childFieldExpressions = exprs.toList(),
        parentTableName = "parent",
        parentBuilderClassName = "ParentBuilder",
        parentResultClassName = "ParentResult",
        builderFunctionName = "child",
        placeholderPropertyName = "parent",
        childResultClassName = "ChildResult",
        childRecordClassName = "ChildRecord",
        childSourcePackage = "p",
        isSelfReferential = false,
        isMultiFk = true
    )

    @Test
    fun sharedFirstColumnUsesLaterColumnForSecondFk() {
        // Same jOOQ expression for the leading column (one physical FK column, two constraints)
        val sharedOrg = "ChildTable.CHILD.ORG_ID"
        val a = fk("fk_a", sharedOrg, "ChildTable.CHILD.DOC_REF")
        val b = fk("fk_b", sharedOrg, "ChildTable.CHILD.ARCHIVE_REF")
        val assigned = assignFkWhenDiscriminators(listOf(a, b)).toMap()
        assertEquals(sharedOrg, assigned[a])
        assertEquals("ChildTable.CHILD.ARCHIVE_REF", assigned[b])
    }

    @Test
    fun stableOrderByFkName() {
        val shared = "ChildTable.CHILD.C1"
        val z = fk("fk_z", shared, "ChildTable.CHILD.C2")
        val a = fk("fk_a", shared, "ChildTable.CHILD.C3")
        val pairs = assignFkWhenDiscriminators(listOf(z, a))
        assertEquals("fk_a", pairs[0].first.fkName)
        assertEquals("fk_z", pairs[1].first.fkName)
        assertEquals(shared, pairs[0].second)
        assertEquals("ChildTable.CHILD.C2", pairs[1].second)
    }

    @Test
    fun duplicateChildColumnSetsFail() {
        val dup1 = fk("fk_one", "T.C.A", "T.C.B")
        val dup2 = fk("fk_two", "T.C.A", "T.C.B")
        assertThrows<IllegalStateException> {
            assignFkWhenDiscriminators(listOf(dup1, dup2))
        }
    }
}
