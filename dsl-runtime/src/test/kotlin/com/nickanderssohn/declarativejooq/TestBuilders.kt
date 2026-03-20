package com.nickanderssohn.declarativejooq

import org.jooq.TableField

/**
 * Hand-written DSL builders that simulate what Phase 2 codegen will produce.
 *
 * OrganizationBuilder — root-level builder for the organization table.
 * UserBuilder — child builder for "user", automatically wires organization_id FK.
 *
 * Extension function DslScope.organization() is the entry point users (and generated code)
 * will call inside the execute { } block.
 */
class OrganizationBuilder(
    private val graph: RecordGraph
) : RecordBuilder<OrganizationRecord>(
    table = OrganizationTable.ORGANIZATION,
    parentNode = null,
    parentFkFields = emptyList(),
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
     * Declare a child user record.  The FK (organization_id) will be resolved
     * automatically from this organization's generated PK after it is inserted.
     */
    fun user(block: UserBuilder.() -> Unit) {
        childBlocks.add { parentNode ->
            val builder = UserBuilder(
                recordGraph = graph,
                parentNode = parentNode,
                parentFkFields = listOf(UserTable.USER.ORGANIZATION_ID)
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

class UserBuilder(
    recordGraph: RecordGraph,
    parentNode: RecordNode,
    parentFkFields: List<TableField<*, *>>
) : RecordBuilder<UserRecord>(
    table = UserTable.USER,
    parentNode = parentNode,
    parentFkFields = parentFkFields,
    recordGraph = recordGraph
) {
    var name: String? = null
    var email: String? = null

    override fun buildRecord(): UserRecord {
        val record = UserRecord()
        record.set(UserTable.USER.NAME, name)
        record.set(UserTable.USER.EMAIL, email)
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

// ---------------------------------------------------------------------------
// Composite FK sample: doc (org_id, doc_id) <- doc_revision(org_id, doc_id)
// ---------------------------------------------------------------------------

class DocumentBuilder(
    private val graph: RecordGraph
) : RecordBuilder<DocRecord>(
    table = DocTable.DOC,
    parentNode = null,
    parentFkFields = emptyList(),
    recordGraph = graph
) {
    var orgId: Long? = null
    var title: String? = null

    private val childBlocks = mutableListOf<(RecordNode) -> Unit>()

    override fun buildRecord(): DocRecord {
        val record = DocRecord()
        record.set(DocTable.DOC.ORG_ID, orgId)
        record.set(DocTable.DOC.TITLE, title)
        return record
    }

    fun revision(block: RevisionBuilder.() -> Unit) {
        childBlocks.add { parentNode ->
            val builder = RevisionBuilder(
                recordGraph = graph,
                parentNode = parentNode,
                parentFkFields = listOf(
                    DocRevisionTable.DOC_REVISION.ORG_ID,
                    DocRevisionTable.DOC_REVISION.DOC_ID
                )
            )
            builder.block()
            builder.build()
        }
    }

    fun buildWithChildren(): RecordNode {
        val node = build()
        childBlocks.forEach { it(node) }
        return node
    }
}

class RevisionBuilder(
    recordGraph: RecordGraph,
    parentNode: RecordNode,
    parentFkFields: List<TableField<*, *>>
) : RecordBuilder<DocRevisionRecord>(
    table = DocRevisionTable.DOC_REVISION,
    parentNode = parentNode,
    parentFkFields = parentFkFields,
    recordGraph = recordGraph
) {
    var summary: String? = null

    override fun buildRecord(): DocRevisionRecord {
        val record = DocRevisionRecord()
        record.set(DocRevisionTable.DOC_REVISION.SUMMARY, summary)
        return record
    }
}

fun DslScope.document(block: DocumentBuilder.() -> Unit) {
    val builder = DocumentBuilder(recordGraph)
    builder.block()
    val node = builder.buildWithChildren()
    recordGraph.addRootNode(node)
}
