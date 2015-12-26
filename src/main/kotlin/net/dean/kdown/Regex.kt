package net.dean.kdown

import kotlin.text.isEmpty
import kotlin.text.toCharArray

/**
 * Collection of utility method pertaining to the creation of regular expressions
 */
public object RegexUtils {
    /**
     * Creates a regular expression from the given glob expression. Supported characters are '*' and '?'.
     *
     * The asterisk ('*') matches zero or more characters, so the expression `C:\Users\*\file.txt` would match
     * `C:\Users\Me\file.txt`, `C:\Users\\file.txt`, and `C:\Users\Me\Projects\file.txt`
     *
     * The question mark ('?') matches any character, so the expression `C:\Users\Me\file?.txt` would match
     * `C:\Users\Me\file1.txt`, `C:\Users\Me\file2.txt`, and so on.
     *
     * Any asterisks are replaced with `(.*)`, which is a capturing group that matches any number of characters. The
     * values captured inside these can be found later by Matcher.group(int), or by
     * RegexResourceIdentifier.captureGroup(String, CharSequence, Int). Question marks are replaced by `(?)` and
     * their values can be accessed in the same way.
     *
     * For more complex operations, use standard regular expressions.
     *
     * Adapted from http://stackoverflow.com/a/1248627/1275092
     */
    public fun compileGlob(glob: String, anchor: Boolean = true): String {
        val sb = StringBuilder()
        with (sb) {
            for (c in glob.toCharArray()) {
                when (c) {
                    // Insert capturing groups on asterisks and question marks
                    '*' -> append("(.*)")
                    '?' -> append("(.)")
                    '.' -> append("\\.")
                    '\\' -> append("\\\\")
                    else -> append(c)
                }
            }
            // Finish it off with an anchor
            if (anchor) append('$')
        }

        return sb.toString()
    }

    /**
     * Creates a regular expression formatted for a URL. The protocol defaults to a regular expression that matches
     * both HTTP and HTTPS (`http[s]?`). The final regex will be compiled as such:
     *
     *     ${protocol}://${host}${path}
     *
     * For example, `ofUrl(host = "example\\.com", path = "directory")` will return
     * `http[s]?://example\\.com/directory`
     */
    public fun ofUrl(protocol: String = "http[s]?",
                                       host: String,
                                       path: String): String =
            "${protocol}://${host}${path}"


    /**
     * Creates a regular expression formatted for a URL. If no protocol is given, then the default will be used,
     * which matches both HTTP and HTTPS protocols. Both [host] and [path] will be turned into a regular
     * expression before being sent to [ofUrl(String, String, String)]
     */
    public fun ofUrlGlob(protocol: String = "",
                                        host: String,
                                        path: String): String {
        if (protocol.isEmpty()) {
            // Don't include the anchor on the host
            return ofUrl(host = compileGlob(host, false), path = compileGlob(path))
        } else {
            return ofUrl(protocol = compileGlob(protocol), host = compileGlob(host), path = compileGlob(path))
        }
    }
}
