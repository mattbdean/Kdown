package net.dean.kdown.test

import net.dean.kdown.RegexUtils
import org.testng.Assert.assertEquals
import org.testng.Assert.assertTrue
import org.testng.annotations.Test
import kotlin.text.Regex
import kotlin.text.matches

public class RegexTest {
    public @Test fun testGlobConversion() {
        assertGlobMatches("https://example.com/path/*", "https://example.com/path/123")
        assertGlobMatches("/home/*/sample.txt", "/home/me/sample.txt")
        assertGlobMatches("/home/me/sample*.txt", "/home/me/sample100.txt")
    }

    public @Test fun testCreateUrlRegex() {
        assertEquals(RegexUtils.ofUrl(host = "example\\.com", path = "/path"), """http[s]?://example\.com/path""")
        assertEquals(RegexUtils.ofUrl(protocol = "http", host = "example\\.com", path = "/sample.txt"),
                """http://example\.com/sample.txt""")
        assertEquals(RegexUtils.ofUrlGlob(host = "*.example.com", path = "/resource/*/file.txt"),
                """http[s]?://(.*)\.example\.com/resource/(.*)/file\.txt$""")
    }

    private fun assertMatches(regex: String, test: String) {
        assertTrue(test.matches(Regex(regex)), "Regex '$regex' did not match string '$test'")
    }

    private fun assertGlobMatches(glob: String, test: String) {
        val regex = RegexUtils.compileGlob(glob)
        assertTrue(test.matches(Regex(regex)), "Glob (compiled to '$regex') did not match string '$test'")
    }
}
