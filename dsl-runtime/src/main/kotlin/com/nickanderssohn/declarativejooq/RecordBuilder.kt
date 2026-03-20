package com.nickanderssohn.declarativejooq

import org.jooq.Table
import org.jooq.TableField
import org.jooq.UpdatableRecord

data class PendingPlaceholderRef(
    val fkFields: List<TableField<*, *>>,
    val targetRecord: UpdatableRecord<*>
)

@DeclarativeJooqDsl
abstract class RecordBuilder<R : UpdatableRecord<R>>(
    val table: Table<R>,
    var parentNode: RecordNode?,
    val parentFkFields: List<TableField<*, *>>,
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
            parentFkFields = parentFkFields,
            declarationIndex = recordGraph.nextDeclarationIndex(),
            isSelfReferential = isSelfReferential
        )
        recordGraph.registerNode(node)
        for (pending in pendingPlaceholderRefs) {
            val targetNode = recordGraph.nodeForRecord(pending.targetRecord)
                ?: throw IllegalStateException(
                    "Placeholder target record not found in graph — was the placeholder created in the same execute block?"
                )
            recordGraph.addPlaceholderRef(node, PlaceholderRef(targetNode, pending.fkFields))
        }
        parentNode?.children?.add(node)
        return node
    }
}
