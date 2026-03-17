package com.nickanderssohn.declarativejooq

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TopologicalSorterTest {

    @Test
    fun `single table with no FKs returns that table name`() {
        val graph = mapOf("users" to emptySet<String>())
        val result = TopologicalSorter.sort(graph)
        assertEquals(listOf("users"), result)
    }

    @Test
    fun `two tables where B depends on A returns A then B`() {
        // users depends on organizations (has FK to organizations)
        val graph = mapOf(
            "organizations" to emptySet(),
            "users" to setOf("organizations")
        )
        val result = TopologicalSorter.sort(graph)
        val orgIdx = result.indexOf("organizations")
        val userIdx = result.indexOf("users")
        assertTrue(orgIdx < userIdx, "organizations must appear before users, got: $result")
        assertEquals(2, result.size)
    }

    @Test
    fun `three tables linear chain C depends on B depends on A returns A B C`() {
        val graph = mapOf(
            "a" to emptySet(),
            "b" to setOf("a"),
            "c" to setOf("b")
        )
        val result = TopologicalSorter.sort(graph)
        assertEquals(3, result.size)
        assertTrue(result.indexOf("a") < result.indexOf("b"), "a must come before b, got: $result")
        assertTrue(result.indexOf("b") < result.indexOf("c"), "b must come before c, got: $result")
    }

    @Test
    fun `two independent tables both appear in result`() {
        val graph = mapOf(
            "table_a" to emptySet<String>(),
            "table_b" to emptySet<String>()
        )
        val result = TopologicalSorter.sort(graph)
        assertEquals(2, result.size)
        assertTrue(result.contains("table_a"), "table_a must be in result")
        assertTrue(result.contains("table_b"), "table_b must be in result")
    }

    @Test
    fun `self-edge is stripped and table appears without error`() {
        // Table A has a self-referential FK (A depends on A)
        val graph = mapOf(
            "category" to setOf("category")
        )
        val result = TopologicalSorter.sort(graph)
        assertEquals(listOf("category"), result)
    }

    @Test
    fun `cycle between two tables throws IllegalStateException with Cycle detected`() {
        // A depends on B, B depends on A
        val graph = mapOf(
            "a" to setOf("b"),
            "b" to setOf("a")
        )
        val exception = assertThrows(IllegalStateException::class.java) {
            TopologicalSorter.sort(graph)
        }
        assertTrue(
            exception.message?.contains("Cycle detected") == true,
            "Exception message should contain 'Cycle detected', got: ${exception.message}"
        )
    }

    @Test
    fun `diamond dependency D depends on B and C both depend on A returns A before B and C and both before D`() {
        // Diamond: A <- B <- D, A <- C <- D
        val graph = mapOf(
            "a" to emptySet(),
            "b" to setOf("a"),
            "c" to setOf("a"),
            "d" to setOf("b", "c")
        )
        val result = TopologicalSorter.sort(graph)
        assertEquals(4, result.size)
        val aIdx = result.indexOf("a")
        val bIdx = result.indexOf("b")
        val cIdx = result.indexOf("c")
        val dIdx = result.indexOf("d")
        assertTrue(aIdx < bIdx, "a must come before b, got: $result")
        assertTrue(aIdx < cIdx, "a must come before c, got: $result")
        assertTrue(bIdx < dIdx, "b must come before d, got: $result")
        assertTrue(cIdx < dIdx, "c must come before d, got: $result")
    }
}
