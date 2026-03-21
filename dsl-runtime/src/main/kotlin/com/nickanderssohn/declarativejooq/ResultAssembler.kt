package com.nickanderssohn.declarativejooq

import org.jooq.UpdatableRecord

/**
 * Collects all inserted records from the [RecordGraph] into a [DslResult], grouping them
 * by table name and preserving declaration order.
 */
object ResultAssembler {
    fun assemble(allNodes: List<RecordNode>): DslResult {
        val recordsByTable = allNodes
            .sortedBy { it.declarationIndex }
            .groupBy({ it.table.name }, { it.record as UpdatableRecord<*> })
            .mapValuesTo(LinkedHashMap()) { (_, records) -> records.toMutableList() }

        return DslResult(recordsByTable)
    }
}
