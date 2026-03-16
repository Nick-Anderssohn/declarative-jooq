package com.example.declarativejooq

import org.jooq.Table
import org.jooq.TableField
import org.jooq.UpdatableRecord

@DeclarativeJooqDsl
abstract class RecordBuilder<R : UpdatableRecord<R>>(
    val table: Table<R>,
    val parentNode: RecordNode?,
    val parentFkField: TableField<*, *>?,
    val recordGraph: RecordGraph
) {
    abstract fun buildRecord(): R

    fun build(): RecordNode {
        val record = buildRecord()
        val node = RecordNode(
            table = table,
            record = record,
            parentNode = parentNode,
            parentFkField = parentFkField,
            declarationIndex = recordGraph.nextDeclarationIndex()
        )
        parentNode?.children?.add(node)
        return node
    }
}
