package com.example.declarativejooq

import org.jooq.Table
import org.jooq.TableField
import org.jooq.UpdatableRecord

class RecordNode(
    val table: Table<*>,
    val record: UpdatableRecord<*>,
    val parentNode: RecordNode?,
    val parentFkField: TableField<*, *>?,
    val declarationIndex: Int
) {
    val children: MutableList<RecordNode> = mutableListOf()
}
