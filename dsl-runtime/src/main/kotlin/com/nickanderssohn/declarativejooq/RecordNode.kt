package com.nickanderssohn.declarativejooq

import org.jooq.Table
import org.jooq.TableField
import org.jooq.UpdatableRecord

/**
 * A resolved reference from one [RecordNode] to another via a placeholder property.
 * Used by [TopologicalInserter] to set the FK field values after the target record is inserted.
 * [fkFields] and [refFields] are positionally matched: fkFields[i] receives the value from refFields[i].
 */
data class PlaceholderRef(
    val targetNode: RecordNode,
    val fkFields: List<TableField<*, *>>,
    val refFields: List<TableField<*, *>>
)

/**
 * A single node in the [RecordGraph], representing one jOOQ record to be inserted.
 * Holds the record data, its parent relationship (if nested), and any placeholder
 * references to other nodes for cross-tree FK wiring.
 *
 * [parentFkFields] and [parentRefFields] are positionally matched: parentFkFields[i] on this
 * record receives the value from parentRefFields[i] on the parent record.
 */
class RecordNode(
    val table: Table<*>,
    val record: UpdatableRecord<*>,
    val parentNode: RecordNode?,
    val parentFkFields: List<TableField<*, *>> = emptyList(),
    val parentRefFields: List<TableField<*, *>> = emptyList(),
    val declarationIndex: Int,
    val isSelfReferential: Boolean = false
) {
    val children: MutableList<RecordNode> = mutableListOf()
    val placeholderRefs: MutableList<PlaceholderRef> = mutableListOf()
}
