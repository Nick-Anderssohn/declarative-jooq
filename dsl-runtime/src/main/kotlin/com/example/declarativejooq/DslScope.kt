package com.example.declarativejooq

import org.jooq.DSLContext

@DeclarativeJooqDsl
class DslScope(internal val dslContext: DSLContext) {
    internal val recordGraph = RecordGraph()
}
