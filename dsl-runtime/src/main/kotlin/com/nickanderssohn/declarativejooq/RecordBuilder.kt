package com.nickanderssohn.declarativejooq

import org.jooq.Table
import org.jooq.TableField
import org.jooq.UpdatableRecord

/**
 * Captures a placeholder FK assignment made during DSL evaluation, before the target node
 * exists in the graph. Converted to a [PlaceholderRef] when [RecordBuilder.build] is called.
 */
data class PendingPlaceholderRef(
    val fkField: TableField<*, *>,
    val targetRecord: UpdatableRecord<*>
)

/**
 * Base class for all generated per-table builders (e.g., `OrganizationBuilder`). Manages
 * record construction, parent-child linking, and placeholder FK collection. Generated
 * builders extend this and override [buildRecord] to set column values from DSL properties.
 */
@DeclarativeJooqDsl
abstract class RecordBuilder<R : UpdatableRecord<R>>(
    val table: Table<R>,
    var parentNode: RecordNode?,
    val parentFkField: TableField<*, *>?,
    val recordGraph: RecordGraph,
    val isSelfReferential: Boolean = false
) {
    abstract fun buildRecord(): R

    private var _cachedRecord: R? = null

    fun getOrBuildRecord(): R {
        if (_cachedRecord == null) {
            _cachedRecord = buildRecord()
        }
        return _cachedRecord!!
    }

    val pendingPlaceholderRefs: MutableList<PendingPlaceholderRef> = mutableListOf()

    fun build(): RecordNode {
        val record = getOrBuildRecord()
        val node = RecordNode(
            table = table,
            record = record,
            parentNode = parentNode,
            parentFkField = parentFkField,
            declarationIndex = recordGraph.nextDeclarationIndex(),
            isSelfReferential = isSelfReferential
        )
        recordGraph.registerNode(node)
        for (pending in pendingPlaceholderRefs) {
            val targetNode = recordGraph.nodeForRecord(pending.targetRecord)
                ?: throw IllegalStateException(
                    "Placeholder target record not found in graph — was the placeholder created in the same execute block?"
                )
            recordGraph.addPlaceholderRef(node, PlaceholderRef(targetNode, pending.fkField))
        }
        parentNode?.children?.add(node)
        return node
    }
}
