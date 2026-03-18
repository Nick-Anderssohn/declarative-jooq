package com.nickanderssohn.declarativejooq.codegen

import com.nickanderssohn.declarativejooq.codegen.scanner.NamingConventions
import com.nickanderssohn.declarativejooq.codegen.scanner.NamingConventions.Convention
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class NamingConventionsTest {

    // -----------------------------------------------------------------------
    // Convention detection
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "detect({0}) == {1}")
    @CsvSource(
        "organization_id, SNAKE_CASE",
        "user_profile_name, SNAKE_CASE",
        "id, SNAKE_CASE",
        "name, SNAKE_CASE",
        "OrganizationId, PASCAL_CASE",
        "UserProfileName, PASCAL_CASE",
        "Id, PASCAL_CASE",
        "organizationId, CAMEL_CASE",
        "userProfileName, CAMEL_CASE"
    )
    fun detectConvention(input: String, expected: Convention) {
        assertEquals(expected, NamingConventions.detect(input))
    }

    // -----------------------------------------------------------------------
    // toCamelCase
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "toCamelCase({0}) == {1}")
    @CsvSource(
        "organization_id, organizationId",
        "OrganizationId, organizationId",
        "organizationId, organizationId",
        "user_profile, userProfile",
        "UserProfile, userProfile",
        "userProfile, userProfile",
        "name, name",
        "id, id",
        "ID, id"
    )
    fun toCamelCase(input: String, expected: String) {
        assertEquals(expected, NamingConventions.toCamelCase(input))
    }

    // -----------------------------------------------------------------------
    // toPascalCase
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "toPascalCase({0}) == {1}")
    @CsvSource(
        "organization, Organization",
        "user_profile, UserProfile",
        "UserProfile, UserProfile",
        "userProfile, UserProfile"
    )
    fun toPascalCase(input: String, expected: String) {
        assertEquals(expected, NamingConventions.toPascalCase(input))
    }

    // -----------------------------------------------------------------------
    // stripIdSuffix
    // -----------------------------------------------------------------------

    @ParameterizedTest(name = "stripIdSuffix({0}) == {1}")
    @CsvSource(
        "organization_id, organization",
        "OrganizationId, Organization",
        "organizationId, organization",
        "parent_id, parent",
        "ParentId, Parent",
        "parentId, parent",
        "id, id",
        "name, name"
    )
    fun stripIdSuffix(input: String, expected: String) {
        assertEquals(expected, NamingConventions.stripIdSuffix(input))
    }

    // -----------------------------------------------------------------------
    // normalizedEquals
    // -----------------------------------------------------------------------

    @Test
    fun normalizedEqualsTrue() {
        assertTrue(NamingConventions.normalizedEquals("organization", "Organization"))
        assertTrue(NamingConventions.normalizedEquals("user_profile", "UserProfile"))
        assertTrue(NamingConventions.normalizedEquals("user_profile", "userProfile"))
    }

    @Test
    fun normalizedEqualsFalse() {
        assertFalse(NamingConventions.normalizedEquals("organization", "user"))
    }
}
