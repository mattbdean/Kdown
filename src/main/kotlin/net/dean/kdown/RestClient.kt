package net.dean.kdown

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.squareup.okhttp.*
import kotlin.collections.mapOf
import kotlin.text.isEmpty
import kotlin.text.startsWith

/**
 * This class enables the retrieval of resources that must interact with a RESTful JSON API
 */
public class RestClient(private val http: OkHttpClient, public val userAgent: String) {
    public fun get(url: String, headers: Map<String, String> = mapOf()): RestResponse {
        val request = Request.Builder()
                .get()
                .url(url)
        for (elem in headers.entries) {
            request.addHeader(elem.key, elem.value)
        }
        request.addHeader("User-Agent", userAgent)

        return RestResponse(http.newCall(request.build()).execute())
    }

    public fun execute(request: Request): RestResponse {
        return RestResponse(http.newCall(request).execute())
    }
}

private val objectMapper: ObjectMapper = jacksonObjectMapper()

// Adapted from https://github.com/thatJavaNerd/JRAW/blob/master/src/main/java/net/dean/jraw/http/RestResponse.java
public data class RestResponse(private val response: Response) {
    /** A list of all the headers received from the server */
    public val headers: Headers
    /** The root node of the JSON */
    public val json: JsonNode
    /** The raw data of the response's content */
    public val raw: String
    /** The Content-Type returned from the response */
    public val type: MediaType

    init {
        this.headers = response.headers();
        this.raw = response.body().string()
        this.type = MediaType.parse(response.header("Content-Type"));

        if (!type.toString().startsWith("application/json") && !raw.isEmpty()) {
            throw IllegalArgumentException("Content type was not application/json")
        }
        this.json = objectMapper.readTree(raw)
    }
}

public interface ApiConsumer {
    val rest: RestClient

    @Throws(IllegalStateException::class) fun checkForError(root: JsonNode)
}
