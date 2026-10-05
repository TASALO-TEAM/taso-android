package com.tasalo.android.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class HiveUserTest {
    @Test
    fun accepts_plain_and_at_prefixed_names_in_lowercase() {
        assertEquals("tasalo", HiveUser.normalize("tasalo"))
        assertEquals("tasalo", HiveUser.normalize("@Tasalo"))
        assertEquals("ersusoficial", HiveUser.normalize("  @ersusoficial  "))
        assertEquals("user.name", HiveUser.normalize("user.name"))
        assertEquals("a-b-c", HiveUser.normalize("a-b-c"))
    }

    @Test
    fun extracts_the_user_from_a_blog_link() {
        assertEquals("tasalo", HiveUser.normalize("https://ecency.com/@tasalo/posts"))
        assertEquals("ersusoficial", HiveUser.normalize("https://peakd.com/@ersusoficial"))
    }

    @Test
    fun rejects_invalid_names() {
        assertNull(HiveUser.normalize(""))
        assertNull(HiveUser.normalize("ab"))
        assertNull(HiveUser.normalize("has space"))
        assertNull(HiveUser.normalize("1abc"))
        assertNull(HiveUser.normalize("abc-"))
        assertNull(HiveUser.normalize("abc--def"))
        assertNull(HiveUser.normalize("us.er"))
        assertNull(HiveUser.normalize("a@b.com"))
        assertNull(HiveUser.normalize("x".repeat(17)))
        assertNull(HiveUser.normalize("https://ecency.com/tasalo"))
    }
}
