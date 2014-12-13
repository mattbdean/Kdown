package net.dean.kdown

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.net.URL
import java.util.ArrayList
import java.util.HashMap
import java.util.HashSet
import com.squareup.okhttp.Callback
import com.squareup.okhttp.OkHttpClient
import com.squareup.okhttp.Request
import com.squareup.okhttp.Response
import org.slf4j.Logger
import org.slf4j.LoggerFactory

public class Kdown(userAgent: String) {
    private val client: OkHttpClient = OkHttpClient()
    /** Headers that will be sent with every request */
    public val defaultHeaders: MutableMap<String, String> = HashMap()
    /** UrlTransformers that will be queried before sending the final request */
    public val transformers: MutableList<UrlTransformer> = ArrayList();

    {
        defaultHeaders.put("User-Agent", userAgent)
    }

    /**
     * Downloads a file asynchronously. The two callback functions ([success] and [fail]) are called on the successful
     * download of a file and on a failure respectively. Note that if a UrlTransformer is used and it creates multiple
     * download targets, [success] and [fail] will be called *for each file*
     */
    public fun downloadAsync(request: DownloadRequest,
                       success: (file: File) -> Unit = {},
                       fail: (request: Request, e: Exception) -> Unit = {(request, e) -> }) {
        log.info("Enqueuing request to download content from '${request.url}' into '${request.directory}'")
        // Make a GET request to the resolved URL
        val targets = findDownloadTargets(request.url)
        if (targets.size == 0) {
            log.info("No targets found")
            return
        }

        // Add an call for each target
        targets.forEach {
            val httpRequest = buildRequest(it)
            client.newCall(httpRequest).enqueue(object: Callback {
                override fun onFailure(request: Request, e: IOException) { fail(request, e) }
                override fun onResponse(response: Response?) {
                    try {
                        success(transferResponse(request, response!!))
                    } catch (e: Exception) {
                        fail(httpRequest, e)
                    }
                }
            })
        }
    }

    /** Downloads a file synchronously and returns the set of files that were downloaded */
    public fun download(request: DownloadRequest): Set<File> {
        log.info("Requested to download content from '${request.url}' into '${request.directory}'")
        // Make a GET request to the resolved URL
        val targets = findDownloadTargets(request.url)
        if (targets.size == 0) {
            log.info("No targets found")
            return setOf()
        }

        // Download all the files in the list
        val downloads: MutableSet<File> = HashSet()
        targets.forEach {
            val httpRequest = buildRequest(it)
            val response = client.newCall(httpRequest).execute()
            downloads.add(transferResponse(request, response))
        }

        return downloads
    }

    /** Builds a GET request to the given URL and adds the default headers */
    private fun buildRequest(url: String): Request {
        val requestBuilder = Request.Builder()
                .get()
                .url(url)

        // Add default headers
        for ((key, value) in defaultHeaders) {
            requestBuilder.addHeader(key, value)
        }

        return requestBuilder.build()
    }

    /** Transfers the body of a response (file) to the download request's directory */
    throws(javaClass<IllegalStateException>())
    private fun transferResponse(request: DownloadRequest, response: Response): File {
        if (!response.isSuccessful()) {
            throw NetworkException(response.code())
        }

        // Verify the Content-Type header
        val responseContentType = response.header("Content-Type", "")
        if (responseContentType.equals("")) { // Default value is an empty string
            throw IllegalStateException("No Content-Type header returned")
        }

        if (!checkContentType(responseContentType, request.contentTypes)) {
            throw IllegalStateException("No valid content types matched the Content-Type '$responseContentType'")
        }

        // Get the file name
        val fileName = response.request().url().getPath().split('/').last()
        log.info("File name detected as '$fileName'")

        // Write the response body to the file
        if (!request.directory.isDirectory()) {
            throw IllegalArgumentException("Download directory does not exist or is not a directory")
        }

        val input = response.body().byteStream()
        val location = File(request.directory, fileName)
        val out = FileOutputStream(location)
        val buffer = ByteArray(4096)
        var len: Int
        while (true) {
            len = input.read(buffer)
            if (len == -1)
                break
            out.write(buffer, 0, len)
        }

        input.close()
        out.close()
        log.info("Downloaded file to $location")
        return location
    }

    /** Finds the first transform that can transform the given URL and returns the new URL(s) */
    private fun findDownloadTargets(url: String): Set<String> {
        log.debug("Trying to resolve URL '$url'")
        for (transformer in transformers) {
            val u = URL(url)
            if (transformer.willTransform(u)) {
                val resolved = transformer.transform(u)
                log.debug("Resolved '$url' to '$resolved'")
                return resolved
            }
        }

        return setOf(url)
    }

    /**
     * Checks if any of the given acceptable Content-Types starts with the given Content-Type. Returns true if
     * [acceptable] is empty
     */
    private fun checkContentType(given: String, acceptable: Array<String>): Boolean {
        if (acceptable.size == 0) return true
        return acceptable.filter { it.startsWith(given) }.size > 0
    }
}

/**
 * The UrlTransformer trait is used to change one URL into one or many other URLs. A sample use case would be if an
 * imgur album was given to download (such as [this one](https://imgur.com/a/szz8j)). One could make use of the imgur
 * API to retrieve a list of images in said gallery and return this data in the [transform] method.
 */
public trait UrlTransformer {
    /**
     * Tests if this UrlTransformer will operate on the given URL. If true, then [transform] will be called directly
     * after.
     */
    public fun willTransform(url: URL): Boolean
    /**
     * Transforms one URL into a set of other URLs. The resulting Set must contain at least one element, and every
     * element in the Set must be a valid URL
     */
    public fun transform(url: URL): Set<String>
}

/**
 * Provides a combination of variables used to make a download, including the URL to request, the directory to save the
 * file to, and the Content-Types that will be acceptable for the HTTP response. If no Content-Types are given, the
 * response's content type is discarded and the file will be downloaded in any case
 */
public data class DownloadRequest(public val url: String,
                                  public val directory: File,
                                  public vararg val contentTypes: String)

/**
 * Indicates that an HTTP response has returned a code that is not in the range of [200, 300)
 */
public class NetworkException(public val code: Int): Exception("Request returned unsuccessul response: $code")

private val log: Logger = LoggerFactory.getLogger(javaClass<Kdown>().getSimpleName())

