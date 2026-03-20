package com.nickanderssohn.declarativejooq

import org.jooq.DSLContext

/**
 * Receiver scope for the top-level [DecDsl.execute] block. Generated root-table extension
 * functions (e.g., `organization { }`) are defined on this class, and each call registers
 * a root node in the underlying [RecordGraph].
 */
@DeclarativeJooqDsl
class DslScope(internal val dslContext: DSLContext) {
    // Must be public so generated extension functions (compiled in a separate module) can access it.
    val recordGraph = RecordGraph()
}
