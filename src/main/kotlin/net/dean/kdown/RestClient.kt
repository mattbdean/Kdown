package net.dean.kdown

import com.squareup.okhttp.Headers;
import com.squareup.okhttp.MediaType;
import com.squareup.okhttp.Response;
import com.squareup.okhttp.Request
import com.squareup.okhttp.OkHttpClient
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.JsonNode

/**
 * This class enables the retrieval of resources that must interact with a RESTful JSON API
 */
public class RestClient(private val http: OkHttpClient, public val userAgent: String) {
    public fun get(url: String, headers: Map<String, String> = mapOf()): RestResponse {
        val request = Request.Builder()
                .get()
                .url(url)
        for ((key, value) in headers) {
            request.addHeader(key, value)
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
public data class RestResponse(response: Response) {
    /** A list of all the headers received from the server */
    public val headers: Headers
    /** The root node of the JSON */
    public val json: JsonNode?
    /** The raw data of the response's content */
    public val raw: String
    /** The Content-Type returned from the response */
    public val type: MediaType

    {
        this.headers = response.headers();
        this.raw = response.body().string()
        this.type = MediaType.parse(response.header("Content-Type"));

        if (type.toString().startsWith("application/json") && !raw.isEmpty()) {
            this.json = objectMapper.readTree(raw)
        } else {
            // Init JSON-related final variables
            this.json = null;
        }
    }
}
