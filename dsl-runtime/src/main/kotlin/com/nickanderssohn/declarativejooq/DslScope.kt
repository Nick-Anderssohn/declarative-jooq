package com.example.declarativejooq

import org.jooq.DSLContext

@DeclarativeJooqDsl
class DslScope(internal val dslContext: DSLContext) {
    // Must be public so generated extension functions (compiled in a separate module) can access it.
    val recordGraph = RecordGraph()
}
