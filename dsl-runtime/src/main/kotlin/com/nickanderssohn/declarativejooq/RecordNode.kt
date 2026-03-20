package com.nickanderssohn.declarativejooq

import org.jooq.Table
import org.jooq.TableField
import org.jooq.UpdatableRecord

data class PlaceholderRef(
    val targetNode: RecordNode,   // the node being referenced (the placeholder)
    /** Child-side FK columns in key order; values are taken from the target's primary key in order. */
    val fkFields: List<TableField<*, *>>
)

class RecordNode(
    val table: Table<*>,
    val record: UpdatableRecord<*>,
    val parentNode: RecordNode?,
    /** Child-side FK columns linking this node to parentNode; empty if not nested under a parent. */
    val parentFkFields: List<TableField<*, *>>,
    val declarationIndex: Int,
    val isSelfReferential: Boolean = false
) {
    val children: MutableList<RecordNode> = mutableListOf()
    val placeholderRefs: MutableList<PlaceholderRef> = mutableListOf()
}
