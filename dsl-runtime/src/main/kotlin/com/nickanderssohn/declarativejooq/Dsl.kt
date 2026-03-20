package com.nickanderssohn.declarativejooq

import org.jooq.DSLContext

/**
 * Entry point for the declarative-jooq DSL. Call [execute] with a DSLContext and a builder
 * block to declare a graph of related records, which are then inserted in FK-dependency order.
 */
object DecDsl {
    fun execute(dslContext: DSLContext, block: DslScope.() -> Unit): DslResult {
        val scope = DslScope(dslContext)
        scope.block()
        return TopologicalInserter(dslContext).insertAll(scope.recordGraph)
    }
}
