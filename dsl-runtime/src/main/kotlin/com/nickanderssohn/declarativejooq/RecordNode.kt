package com.nickanderssohn.declarativejooq

import org.jooq.Table
import org.jooq.TableField
import org.jooq.UpdatableRecord

data class PlaceholderRef(
    val targetNode: RecordNode,   // the node being referenced (the placeholder)
    val fkField: TableField<*, *> // the FK field on THIS node's record that should receive the target's PK
)

class RecordNode(
    val table: Table<*>,
    val record: UpdatableRecord<*>,
    val parentNode: RecordNode?,
    val parentFkField: TableField<*, *>?,
    val declarationIndex: Int,
    val isSelfReferential: Boolean = false
) {
    val children: MutableList<RecordNode> = mutableListOf()
    val placeholderRefs: MutableList<PlaceholderRef> = mutableListOf()
}
