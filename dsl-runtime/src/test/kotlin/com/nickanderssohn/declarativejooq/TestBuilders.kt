package com.nickanderssohn.declarativejooq

/**
 * Hand-written DSL builders that simulate what codegen produces.
 *
 * OrganizationBuilder — root-level builder for the organization table.
 * UserBuilder — child builder for "user", automatically wires organization_id FK.
 *
 * Extension function DslScope.organization() is the entry point users (and generated code)
 * will call inside the execute { } block.
 */
@DeclarativeJooqDsl
class OrganizationBuilder(
    recordGraph: RecordGraph,
    parentNode: RecordNode? = null
) {
    var name: String? = null

    /** Deferred child builder blocks — executed after this builder's node is created. */
    private val childBlocks = mutableListOf<(RecordNode) -> Unit>()

    internal val recordBuilder = RecordBuilder<OrganizationRecord>(
        table = OrganizationTable.ORGANIZATION,
        parentNode = parentNode,
        recordGraph = recordGraph,
        buildRecord = {
            val record = OrganizationRecord()
            record.set(OrganizationTable.ORGANIZATION.NAME, name)
            record
        }
    )

    /**
     * Declare a child user record.  The FK (organization_id) will be resolved
     * automatically from this organization's generated PK after it is inserted.
     */
    fun user(block: UserBuilder.() -> Unit) {
        childBlocks.add { parentNode ->
            val builder = UserBuilder(
                recordGraph = recordBuilder.recordGraph,
                parentNode = parentNode,
                parentFkFields = listOf(UserTable.USER.ORGANIZATION_ID),
                parentRefFields = listOf(OrganizationTable.ORGANIZATION.ID)
            )
            builder.block()
            builder.recordBuilder.build()
        }
    }

    /**
     * Build this builder's node and then evaluate all deferred child blocks.
     *
     * Called by the DslScope.organization() extension function so that child builders
     * can reference this node as their parent.
     */
    fun buildWithChildren(): RecordNode {
        val node = recordBuilder.build()
        childBlocks.forEach { it(node) }
        return node
    }
}

@DeclarativeJooqDsl
class UserBuilder(
    recordGraph: RecordGraph,
    parentNode: RecordNode?,
    parentFkFields: List<org.jooq.TableField<*, *>> = emptyList(),
    parentRefFields: List<org.jooq.TableField<*, *>> = emptyList()
) {
    var name: String? = null
    var email: String? = null

    internal val recordBuilder = RecordBuilder<UserRecord>(
        table = UserTable.USER,
        parentNode = parentNode,
        parentFkFields = parentFkFields,
        parentRefFields = parentRefFields,
        recordGraph = recordGraph,
        buildRecord = {
            val record = UserRecord()
            record.set(UserTable.USER.NAME, name)
            record.set(UserTable.USER.EMAIL, email)
            record
        }
    )
}

/**
 * Root-level extension function on DslScope.
 *
 * This is the entry point for declaring organization records inside an execute { } block.
 * The generated code will produce equivalent functions for each root table.
 */
fun DslScope.organization(block: OrganizationBuilder.() -> Unit) {
    val builder = OrganizationBuilder(recordGraph = recordGraph, parentNode = null)
    builder.block()
    val node = builder.buildWithChildren()
    recordGraph.addRootNode(node)
}
