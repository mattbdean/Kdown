package net.dean.kdown

import com.fasterxml.jackson.databind.JsonNode
import java.net.URL
import java.util.*
import java.util.regex.Pattern
import kotlin.collections.mapOf
import kotlin.collections.setOf
import kotlin.text.*

/**
 * The ResourceIdentifier trait is used to change one URL into one or many other URLs. A sample use case would be if an
 * imgur album was given to download (such as [this one](https://imgur.com/a/szz8j)). One could make use of the imgur
 * API to retrieve a list of images in said album and return this data in the [find] method.
 *
 * This pattern allows for the downloading of "resources", not just files, where a resource can consist of one or more
 * file.
 */
public interface ResourceIdentifier {
    /**
     * Tests if this ResourceIdentifier will operate on the given URL. If true, then [find] will be called directly
     * after.
     */
    public fun canFind(url: URL): Boolean

    /**
     * Finds the resources located in the given URL. The resulting Set must contain at least one element, and every
     * element in the Set must be a valid URL. If this method is called, then it can be assumed that [canFind] has
     * already returned true.
     */
    public fun find(url: URL): Set<String>
}

/**
 * Denotes that this ResourceIdentifier can download different versions of a file
 */
public interface AltDownloadFormats<T : Enum<T>> {
    public var resourceVersion: T
}

/**
 * This class is an abstraction of [ResourceIdentifier] that will transform a URL if and only it matches a regular
 * expression found in the key set of [regexes]. [regexes] is a Map of regular expressions to a unique identification
 * string for that resource. For example, on regex might match a single image, and another might match an entire
 * collection of images.
 */
public abstract class RegexResourceIdentifier(public val regexes: Map<String, String>) : ResourceIdentifier {

    override fun canFind(url: URL): Boolean {
        val urlString = url.toExternalForm()

        for (regex in regexes.keys) {
            if (urlString.matches(Regex(regex))) return true
        }
        return false
    }

    override fun find(url: URL): Set<String> {
        val urlString = url.toExternalForm()

        for (it in regexes.entries) {
            val regex = it.key
            val resourceType = it.value
            if (urlString.matches(Regex(regex))) {
                return find(url, resourceType, regex)
            }
        }

        throw IllegalStateException("Could not find any regex that matches $url")
    }

    /**
     * This method is responsible for identifying the files in a resource. [effectiveRegex] is the key in [regexes] that
     * matched the given URL, and [resourceType] is the unique identifying value for that regular expression.
     */
    abstract fun find(url: URL, resourceType: String, effectiveRegex: String): Set<String>

    /** Retrieves capture group [n] in the given regular expression */
    protected fun captureGroup(regex: String, str: CharSequence, n: Int): String {
        val matcher = Pattern.compile(regex).matcher(str)
        matcher.matches()
        return matcher.group(n)
    }

    /**
     * Removes any characters after the question mark, and then removes any characters after a number sign (#). For
     * example, both `stripQuery("/path/subpath?foo=bar#ref")` and `stripQuery("/path/subpath#ref")` return
     * `/path/subpath`.
     */
    public fun stripQuery(path: String): String {
        fun stripFrom(char: Char, str: String): String {
            val index = str.indexOf(char)
            if (index != -1 && index - 1 > 0) {
                return str.substring(0, index)
            }

            return str
        }
        return stripFrom('?', stripFrom('#', path))
    }
}

/** A basic RegexResourceIdentifier whose only resource type is named "it" */
public abstract class SimpleRegexResourceIdentifier(regex: String) : RegexResourceIdentifier(mapOf(regex to "it"))

/**
 * Represents the different versions of a image/gif image available to download from Imgur
 */
public enum class ImgurGifFormat(private val overrideName: String = "") {

    /** GIF image (image/gif) */
    GIF("link"),
    /** Imgur's GIFV format (video/webm) */
    GIFV(),
    /** WebM (video/webm) */
    WEBM(),
    /** MP4 video (video/mp4) */
    MP4();

    public val jsonName: String
        get() = if (overrideName.isEmpty()) name.toLowerCase() else overrideName
}

/**
 * This class uses the imgur API to retrieve links based on the given resource URL. Links to albums (/a/...), galleries
 * (/gallery/...) and images (/...) are supported. When downloading GIFs, there are several different file types to
 * choose from: GIF, GIFV, WEBM, and MP4. This can be changed by modifying the value of [resourceVersion].
 */
public class ImgurResourceIdentifier(dl: Kdown, val clientId: String) :
            AltDownloadFormats<ImgurGifFormat>,
            ApiConsumer,
            RegexResourceIdentifier(mapOf(
                    RegexUtils.ofUrlGlob(host = "imgur.com", path = "/a/*") to "album",
                    RegexUtils.ofUrlGlob(host = "imgur.com", path = "/gallery/*") to "gallery",
                    RegexUtils.ofUrl(host = "imgur\\.com", path = "/([a-zA-Z1-9]{6,})") to "image"
            )) {
    override val rest: RestClient = dl.rest

    public override var resourceVersion: ImgurGifFormat = ImgurGifFormat.GIF
    /** Whether or not to download galleries and images */
    public var downloadMultiple: Boolean = true
    private val headers = mapOf("Authorization" to "Client-ID $clientId")

    override fun find(url: URL, resourceType: String, effectiveRegex: String): Set<String> {
        val urlString = url.toExternalForm()
        fun id(): String = stripQuery(captureGroup(effectiveRegex, urlString, 1))

        when (resourceType) {
            "album" -> {
                if (!downloadMultiple) return setOf()
                val album = id()
                // The capture group might have caught some query args or fragments
                val json = rest.get("https://api.imgur.com/3/album/$album/images", headers = headers).json
                return parseLinks(json.get("data"))
            }
            "gallery" -> {
                if (!downloadMultiple) return setOf()
                val json = rest.get("https://api.imgur.com/3/gallery/album/${id()}", headers = headers).json
                checkForError(json)
                return parseLinks(json.get("data").get("images"))
            }
            "image" -> {
                val json = rest.get("https://api.imgur.com/3/image/${id()}", headers = headers).json
                checkForError(json)
                val data = json.get("data")
                val jsonKey = if (data.has(resourceVersion.jsonName)) resourceVersion.jsonName else "link"
                return setOf(data.get(jsonKey).asText())
            }
        }

        return setOf()
    }

    private fun parseLinks(imagesNode: JsonNode): Set<String> {
        val links: MutableSet<String> = HashSet()
        val desiredType = resourceVersion.jsonName
        for (node in imagesNode) {
            if (node.has(desiredType)) {
                links.add(node.get(desiredType).asText())
            } else {
                links.add(node.get("link").asText())
            }
        }

        return links
    }

    override fun checkForError(root: JsonNode) {
        if (!root.get("success").asBoolean(false)) {
            throw IllegalStateException("Imgur API returned an error: ${root.get("data").get("error").asText()}")
        }
    }
}

/**
 * Represents the list of formats each file on Gfycat is available in
 */
public enum class GfycatFormat {
    /** MP4 video (video/mp4) */
    MP4,
    /** GIF image (image/gif) */
    GIF,
    /** WebM video (video/webm) */
    WEBM
}

/**
 * This class will intercept any download request to gfycat.com and try to retrieve the content of the image/video on
 * that page
 */
public class GfycatResourceIdentifier(dl: Kdown) :
        ApiConsumer,
        AltDownloadFormats<GfycatFormat>,
        SimpleRegexResourceIdentifier(RegexUtils.ofUrlGlob(host = "gfycat.com", path = "/*")) {

    override var resourceVersion: GfycatFormat = GfycatFormat.WEBM
    override val rest: RestClient = dl.rest

    override fun find(url: URL, resourceType: String, effectiveRegex: String): Set<String> {
        val id = stripQuery(captureGroup(effectiveRegex, url.toExternalForm(), 1))
        val json = rest.get("https://gfycat.com/cajax/get/$id").json
        checkForError(json)
        // Get the desired version of the file
        return setOf(json.get("gfyItem").get(resourceVersion.name().toLowerCase() + "Url").asText())
    }

    override fun checkForError(root: JsonNode) {
        if (root.has("error")) {
            throw IllegalStateException("Gfycat API returned an error: ${root.get("error")}")
        }
    }
}

