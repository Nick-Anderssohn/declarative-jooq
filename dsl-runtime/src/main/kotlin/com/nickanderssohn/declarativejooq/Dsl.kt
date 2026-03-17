package com.nickanderssohn.declarativejooq

import org.jooq.DSLContext

fun execute(dslContext: DSLContext, block: DslScope.() -> Unit): DslResult {
    val scope = DslScope(dslContext)
    scope.block()
    return TopologicalInserter(dslContext).insertAll(scope.recordGraph)
}
