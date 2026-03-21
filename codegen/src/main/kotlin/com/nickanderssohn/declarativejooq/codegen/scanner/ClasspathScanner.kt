package com.nickanderssohn.declarativejooq.codegen.scanner

import io.github.classgraph.ClassGraph
import java.io.File

/**
 * Uses ClassGraph to discover jOOQ-generated table and record classes from a compiled
 * class directory. Returns fully-qualified class names for use by [MetadataExtractor].
 */
class ClasspathScanner {

    fun findTableClassNames(classDir: File, packageFilter: String? = null): List<String> {
        val scanner = ClassGraph()
            .overrideClasspath(classDir.absolutePath)
            .enableClassInfo()
        if (packageFilter != null) {
            scanner.acceptPackages(packageFilter)
        }
        return scanner
            .scan()
            .use { scan ->
                scan
                    .getSubclasses("org.jooq.impl.TableImpl")
                    .filterNot { it.isAbstract || it.isInnerClass }
                    .map { it.name }
            }
    }

    fun findRecordClassNames(classDir: File, packageFilter: String? = null): List<String> {
        val scanner = ClassGraph()
            .overrideClasspath(classDir.absolutePath)
            .enableClassInfo()
        if (packageFilter != null) {
            scanner.acceptPackages(packageFilter)
        }
        return scanner
            .scan()
            .use { scan ->
                scan
                    .getSubclasses("org.jooq.impl.UpdatableRecordImpl")
                    .filterNot { it.isAbstract || it.isInnerClass }
                    .map { it.name }
            }
    }
}
