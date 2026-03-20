package com.nickanderssohn.declarativejooq

import org.jooq.Table
import org.jooq.TableField
import org.jooq.UpdatableRecord

/**
 * A resolved reference from one [RecordNode] to another via a placeholder property.
 * Used by [TopologicalInserter] to set the FK field value after the target record is inserted.
 */
data class PlaceholderRef(
    val targetNode: RecordNode,   // the node being referenced (the placeholder)
    val fkField: TableField<*, *> // the FK field on THIS node's record that should receive the target's PK
)

/**
 * A single node in the [RecordGraph], representing one jOOQ record to be inserted.
 * Holds the record data, its parent relationship (if nested), and any placeholder
 * references to other nodes for cross-tree FK wiring.
 */
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
