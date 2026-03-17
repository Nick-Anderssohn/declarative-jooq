package com.nickanderssohn.declarativejooq

import org.jooq.UpdatableRecord

class DslResult(
    private val recordsByTable: LinkedHashMap<String, MutableList<UpdatableRecord<*>>>
) {
    fun <R : UpdatableRecord<R>> records(tableName: String): List<UpdatableRecord<*>> {
        return recordsByTable[tableName] ?: emptyList()
    }

    fun allRecords(): Map<String, List<UpdatableRecord<*>>> {
        return recordsByTable
    }
}
