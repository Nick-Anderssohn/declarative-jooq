package com.nickanderssohn.declarativejooq

import org.jooq.UpdatableRecord

/**
 * Return value of [DecDsl.execute]. Provides access to all inserted records, grouped by
 * table name and ordered by declaration order within each table.
 */
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
