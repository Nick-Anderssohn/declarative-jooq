package com.nickanderssohn.declarativejooq

import org.jooq.UpdatableRecord

object ResultAssembler {
    fun assemble(allNodes: List<RecordNode>): DslResult {
        val recordsByTable = LinkedHashMap<String, MutableList<UpdatableRecord<*>>>()
        // Sort by declaration index to preserve DSL declaration order (DSL-08)
        for (node in allNodes.sortedBy { it.declarationIndex }) {
            recordsByTable
                .getOrPut(node.table.name) { mutableListOf() }
                .add(node.record as UpdatableRecord<*>)
        }
        return DslResult(recordsByTable)
    }
}
