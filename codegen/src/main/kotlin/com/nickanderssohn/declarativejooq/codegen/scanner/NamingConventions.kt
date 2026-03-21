package com.nickanderssohn.declarativejooq.codegen.scanner

/**
 * Utility for detecting and normalizing database naming conventions.
 * Supports SNAKE_CASE (e.g., organization_id), PASCAL_CASE (e.g., OrganizationId),
 * and CAMEL_CASE (e.g., organizationId) as inputs, normalizing to camelCase or PascalCase output.
 */
object NamingConventions {

    enum class Convention { SNAKE_CASE, CAMEL_CASE, PASCAL_CASE }

    /**
     * Detect the naming convention of a single identifier.
     * - Contains `_` -> SNAKE_CASE
     * - First char is uppercase -> PASCAL_CASE
     * - Otherwise -> CAMEL_CASE (single lowercase words default here, functionally equivalent to SNAKE_CASE)
     */
    fun detect(name: String): Convention {
        return when {
            name.contains('_') -> Convention.SNAKE_CASE
            name.isNotEmpty() && name[0].isUpperCase() -> Convention.PASCAL_CASE
            name.any { it.isUpperCase() } -> Convention.CAMEL_CASE
            else -> Convention.SNAKE_CASE // single lowercase word, treat as SNAKE_CASE
        }
    }

    /**
     * Split any naming convention into a normalized list of lowercase words.
     * - SNAKE_CASE: split on `_`
     * - PASCAL_CASE / CAMEL_CASE: split on camelCase word boundaries
     * - All-caps segments like "ID" become "id"
     */
    fun splitWords(name: String): List<String> {
        if (name.contains('_')) {
            // SNAKE_CASE: split on underscores, lowercase each part
            return name
                .split('_')
                .filter { it.isNotEmpty() }
                .map { it.lowercase() }
        }

        // PASCAL_CASE or CAMEL_CASE: split on camelCase boundaries
        // Strategy: insert boundary before each uppercase letter that follows a lowercase letter,
        // or before an uppercase letter followed by a lowercase letter (handles "XMLParser" -> "XML", "Parser")
        return name
            .foldIndexed(mutableListOf(StringBuilder())) { i, words, c ->
                val isUpper = c.isUpperCase()
                val prevIsLower = i > 0 && name[i - 1].isLowerCase()
                val nextIsLower = i + 1 < name.length && name[i + 1].isLowerCase()

                if (isUpper && (prevIsLower || (nextIsLower && words.last().isNotEmpty()))) {
                    words.add(StringBuilder())
                }
                words.last().append(c)
                words
            }
            .map { it.toString().lowercase() }
            .filter { it.isNotEmpty() }
    }

    /**
     * Normalize any naming convention to camelCase.
     * Examples:
     *   "organization_id" -> "organizationId"
     *   "OrganizationId"  -> "organizationId"
     *   "organizationId"  -> "organizationId"
     *   "ID"              -> "id"
     *   "name"            -> "name"
     */
    fun toCamelCase(name: String): String {
        val words = splitWords(name)
        if (words.isEmpty()) return name
        return words[0] + words
            .drop(1)
            .joinToString("") {
                it.replaceFirstChar { c -> c.uppercaseChar() }
            }
    }

    /**
     * Normalize any naming convention to PascalCase.
     * Examples:
     *   "organization"  -> "Organization"
     *   "user_profile"  -> "UserProfile"
     *   "UserProfile"   -> "UserProfile"
     *   "userProfile"   -> "UserProfile"
     */
    fun toPascalCase(name: String): String {
        val words = splitWords(name)
        if (words.isEmpty()) return name
        return words.joinToString("") {
            it.replaceFirstChar { c -> c.uppercaseChar() }
        }
    }

    /**
     * Remove the ID suffix from a name, respecting the naming convention.
     * - SNAKE_CASE: removes trailing `_id` (e.g., "organization_id" -> "organization")
     * - PASCAL_CASE: removes trailing `Id` if the name has more than one word (e.g., "OrganizationId" -> "Organization")
     * - CAMEL_CASE: removes trailing `Id` if the name has more than one word (e.g., "organizationId" -> "organization")
     * - Bare "id" / "Id" (single word) is left unchanged.
     * - Names without ID suffix are returned unchanged.
     */
    fun stripIdSuffix(name: String): String {
        val words = splitWords(name)

        // Only strip if there are multiple words and the last word is "id"
        if (words.size > 1 && words.last() == "id") {
            val strippedWords = words.dropLast(1)
            // Return in the original convention
            return when (detect(name)) {
                Convention.SNAKE_CASE -> strippedWords.joinToString("_")
                Convention.PASCAL_CASE -> strippedWords.joinToString("") {
                    it.replaceFirstChar { c -> c.uppercaseChar() }
                }
                Convention.CAMEL_CASE -> strippedWords[0] + strippedWords
                    .drop(1)
                    .joinToString("") {
                        it.replaceFirstChar { c -> c.uppercaseChar() }
                    }
            }
        }

        return name
    }

    /**
     * Compare two names for equality by normalizing both to a lowercase word list.
     * This allows cross-convention comparison (e.g., "organization" == "Organization" == "user_profile" vs "UserProfile").
     */
    fun normalizedEquals(a: String, b: String): Boolean {
        return splitWords(a) == splitWords(b)
    }
}
