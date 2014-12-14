package net.dean.kdown

import java.net.URL
import java.util.regex.Pattern
import java.util.HashSet
import com.fasterxml.jackson.databind.JsonNode

/**
 * The ResourceIdentifier trait is used to change one URL into one or many other URLs. A sample use case would be if an
 * imgur album was given to download (such as [this one](https://imgur.com/a/szz8j)). One could make use of the imgur
 * API to retrieve a list of images in said album and return this data in the [find] method.
 *
 * This pattern allows for the downloading of "resources", not just files, where a resource can consist of one or more
 * file.
 */
public trait ResourceIdentifier {
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
public trait AltDownloadFormats<T : Enum<T>> {
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

        for (regex in regexes.keySet()) {
            if (urlString.matches(regex)) return true
        }
        return false
    }

    override fun find(url: URL): Set<String> {
        val urlString = url.toExternalForm()

        for ((regex, resourceType) in regexes) {
            if (urlString.matches(regex)) {
                return transform(url, resourceType, regex)
            }
        }

        throw IllegalStateException("Could not find any regex that matches $url")
    }

    /**
     * This method is responsible for identifying the files in a resource. [effectiveRegex] is the key in [regexes] that
     * matched the given URL, and [resourceType] is the unique identifying value for that regular expression.
     */
    abstract fun transform(url: URL, resourceType: String, effectiveRegex: String): Set<String>

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
 * Represents the different versions of a image/gif image available to download from imgur
 */
public enum class ImgurGifFormat(private val overrideName: String = "") {
    public val jsonName: String
        get() = if (overrideName.isEmpty()) name().toLowerCase() else overrideName

    GIF : ImgurGifFormat("link")
    GIFV: ImgurGifFormat()
    WEBM: ImgurGifFormat()
    MP4 : ImgurGifFormat()
}

/**
 * This class uses the imgur API to retrieve links based on the given resource URL. Links to albums (/a/...), galleries
 * (/gallery/...) and images (/...) are supported. When downloading GIFs, there are several different file types to
 * choose from: GIF, GIFV, WEBM, and MP4. This can be changed by modifying the value of [resourceVersion].
 */
public class ImgurResourceIdentifier(val rest: RestClient, val clientId: String) :
            AltDownloadFormats<ImgurGifFormat>,
            RegexResourceIdentifier(mapOf(
                    RegexUtils.ofUrlGlob(host = "imgur.com", path = "/a/*") to "album",
                    RegexUtils.ofUrlGlob(host = "imgur.com", path = "/gallery/*") to "gallery",
                    RegexUtils.ofUrl(host = "imgur\\.com", path = "/([a-zA-Z1-9]{6,})") to "image"
            )) {

    public override var resourceVersion: ImgurGifFormat = ImgurGifFormat.GIF
    private val headers = mapOf("Authorization" to "Client-ID $clientId")

    override fun transform(url: URL, resourceType: String, effectiveRegex: String): Set<String> {
        val urlString = url.toExternalForm()
        fun id(): String = stripQuery(captureGroup(effectiveRegex, urlString, 1))

        when (resourceType) {
            "album" -> {
                val album = id()
                // The capture group might have caught some query args or fragments
                val json = rest.get("https://api.imgur.com/3/album/$album/images", headers = headers).json
                return parseLinks(json!!.get("data"))
            }
            "gallery" -> {
                val galleryAlbum = id()
                val json = rest.get("https://api.imgur.com/3/gallery/album/$galleryAlbum", headers = headers).json
                checkError(json!!)
                return parseLinks(json.get("data").get("images"))
            }
            "image" -> {
                val id = id()
                val json = rest.get("https://api.imgur.com/3/image/$id", headers = headers).json
                checkError(json!!)
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

    private fun checkError(json: JsonNode?) {
        json!! // Assert not null

        if (!json.get("success").asBoolean(false)) {
            throw IllegalStateException("Imgur API returned an error: ${json.get("data").get("error").asText()}")
        }
    }
}

