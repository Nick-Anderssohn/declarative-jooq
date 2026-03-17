package com.nickanderssohn.declarativejooq

object TopologicalSorter {
    /**
     * Sorts table names topologically based on FK dependencies.
     * @param tableGraph maps each table name to the set of table names it depends on (has FKs to)
     * @return table names in topological order (parents before children)
     * @throws IllegalStateException if a cycle is detected (excluding self-edges which are stripped)
     */
    fun sort(tableGraph: Map<String, Set<String>>): List<String> {
        // Build adjacency: strip self-edges
        val inDegree = tableGraph.keys.associateWith { 0 }.toMutableMap()
        val dependents = mutableMapOf<String, MutableList<String>>()

        for ((table, deps) in tableGraph) {
            for (dep in deps) {
                if (dep != table) { // strip self-edges
                    inDegree[table] = (inDegree[table] ?: 0) + 1
                    dependents.getOrPut(dep) { mutableListOf() }.add(table)
                    // Ensure dep is in inDegree even if not a key in tableGraph
                    if (dep !in inDegree) inDegree[dep] = 0
                }
            }
        }

        val queue = ArrayDeque(inDegree.filter { it.value == 0 }.keys.sorted()) // sorted for determinism
        val result = mutableListOf<String>()

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()
            result.add(current)
            dependents[current]?.sorted()?.forEach { dependent ->
                inDegree[dependent] = inDegree[dependent]!! - 1
                if (inDegree[dependent] == 0) queue.add(dependent)
            }
        }

        if (result.size != inDegree.size) {
            val remaining = inDegree.keys - result.toSet()
            throw IllegalStateException(
                "Cycle detected in FK dependency graph. Tables involved: $remaining"
            )
        }
        return result
    }
}
