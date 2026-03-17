package com.nickanderssohn.declarativejooq

/**
 * Hand-written DSL builders that simulate what Phase 2 codegen will produce.
 *
 * OrganizationBuilder — root-level builder for the organization table.
 * AppUserBuilder — child builder for app_user, automatically wires organization_id FK.
 *
 * Extension function DslScope.organization() is the entry point users (and generated code)
 * will call inside the execute { } block.
 */
class OrganizationBuilder(
    private val graph: RecordGraph
) : RecordBuilder<OrganizationRecord>(
    table = OrganizationTable.ORGANIZATION,
    parentNode = null,
    parentFkField = null,
    recordGraph = graph
) {
    var name: String? = null

    /** Deferred child builder blocks — executed after this builder's node is created. */
    private val childBlocks = mutableListOf<(RecordNode) -> Unit>()

    override fun buildRecord(): OrganizationRecord {
        val record = OrganizationRecord()
        record.set(OrganizationTable.ORGANIZATION.NAME, name)
        return record
    }

    /**
     * Declare a child app_user record.  The FK (organization_id) will be resolved
     * automatically from this organization's generated PK after it is inserted.
     */
    fun user(block: AppUserBuilder.() -> Unit) {
        childBlocks.add { parentNode ->
            val builder = AppUserBuilder(
                recordGraph = graph,
                parentNode = parentNode,
                parentFkField = AppUserTable.APP_USER.ORGANIZATION_ID
            )
            builder.block()
            builder.build()
            // build() appends the child node to parentNode.children via RecordBuilder.build()
        }
    }

    /**
     * Build this builder's node and then evaluate all deferred child blocks.
     *
     * Called by the DslScope.organization() extension function so that child builders
     * can reference this node as their parent.
     */
    fun buildWithChildren(): RecordNode {
        val node = build()
        childBlocks.forEach { it(node) }
        return node
    }
}

class AppUserBuilder(
    recordGraph: RecordGraph,
    parentNode: RecordNode,
    parentFkField: org.jooq.TableField<*, *>
) : RecordBuilder<AppUserRecord>(
    table = AppUserTable.APP_USER,
    parentNode = parentNode,
    parentFkField = parentFkField,
    recordGraph = recordGraph
) {
    var name: String? = null
    var email: String? = null

    override fun buildRecord(): AppUserRecord {
        val record = AppUserRecord()
        record.set(AppUserTable.APP_USER.NAME, name)
        record.set(AppUserTable.APP_USER.EMAIL, email)
        return record
    }
}

/**
 * Root-level extension function on DslScope.
 *
 * This is the entry point for declaring organization records inside an execute { } block.
 * The generated Phase 2 code will produce equivalent functions for each root table.
 */
fun DslScope.organization(block: OrganizationBuilder.() -> Unit) {
    val builder = OrganizationBuilder(recordGraph)
    builder.block()
    val node = builder.buildWithChildren()
    recordGraph.addRootNode(node)
}
