package com.nickanderssohn.declarativejooq

import org.jooq.DSLContext

/**
 * Inserts all records from a [RecordGraph] into the database in FK-dependency order.
 * Uses [TopologicalSorter] to determine table-level insert order, resolves parent FK
 * values before each insert, and performs a second pass for self-referential FKs.
 */
class TopologicalInserter(private val dslContext: DSLContext) {
    fun insertAll(graph: RecordGraph): DslResult {
        val allNodes = graph.allNodes()
        if (allNodes.isEmpty()) {
            return DslResult(LinkedHashMap())
        }

        // Build table dependency graph from the actual nodes
        val tableGraph = buildTableGraph(allNodes, graph)

        // Get topological order
        val sortedTables = TopologicalSorter.sort(tableGraph)

        // Group nodes by table name, preserving declaration order within each group
        val sortedNodes = allNodes.sortedBy { it.declarationIndex }

        // Insert in topological order
        sortedTables
            .flatMap { tableName ->
                sortedNodes.filter { it.table.name == tableName }
            }
            .forEach { node ->
                // Resolve FK from parent if applicable (skip self-referential on first pass)
                if (node.parentNode != null && node.parentFkFields.isNotEmpty() && !node.isSelfReferential) {
                    resolveParentFk(node)
                }

                // Resolve placeholder FK references (overrides parent-context FK if same field)
                node
                    .placeholderRefs
                    .forEach { ref ->
                        ref
                            .fkFields
                            .zip(ref.refFields)
                            .forEach { (fkField, refField) ->
                                val targetValue = ref
                                    .targetNode
                                    .record
                                    .get(refField)
                                    ?: throw IllegalStateException(
                                        "Placeholder target field is null — target must be inserted before referencing node. " +
                                                "Table: ${ref.targetNode.table.name}, FK field: ${fkField.name}"
                                    )

                                @Suppress("UNCHECKED_CAST")
                                (node.record as org.jooq.Record).set(
                                    fkField as org.jooq.Field<Any?>,
                                    targetValue
                                )
                            }
                    }

                // Attach to DSLContext and insert
                node.record.attach(dslContext.configuration())
                node.record.store()
            }

        // Second pass: update self-referential FK values now that all PKs are known
        allNodes
            .filter { it.isSelfReferential && it.parentNode != null }
            .forEach { node ->
                resolveParentFk(node)
                node.record.store()
            }

        return ResultAssembler.assemble(allNodes)
    }

    private fun resolveParentFk(node: RecordNode) {
        val parent = node.parentNode
            ?: throw IllegalStateException("resolveParentFk called on node without parent")
        node
            .parentFkFields
            .zip(node.parentRefFields)
            .forEach { (fkField, refField) ->
                val parentValue = parent
                    .record
                    .get(refField)
                    ?: throw IllegalStateException(
                        "Parent record field ${refField.name} is null — parent must be inserted before child"
                    )

                @Suppress("UNCHECKED_CAST")
                (node.record as org.jooq.Record).set(
                    fkField as org.jooq.Field<Any?>,
                    parentValue
                )
            }
    }

    private fun buildTableGraph(nodes: List<RecordNode>, graph: RecordGraph): Map<String, Set<String>> {
        val parentEdges = nodes
            .filter { it.parentNode != null }
            .map { it.table.name to it.parentNode!!.table.name }

        val placeholderEdges = graph
            .placeholderRefs
            .map { (sourceNode, ref) ->
                sourceNode.table.name to ref.targetNode.table.name
            }

        val allTableNames = nodes
            .map { it.table.name }
            .toSet()

        return (parentEdges + placeholderEdges)
            .groupBy(
                { it.first },
                { it.second }
            )
            .mapValues { (_, targets) -> targets.toSet() }
            .let { edgeMap ->
                allTableNames.associateWith { tableName ->
                    edgeMap[tableName] ?: emptySet()
                }
            }
    }
}
