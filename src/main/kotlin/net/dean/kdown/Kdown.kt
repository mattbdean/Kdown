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
    /** Headers that will be sent with every request */
    public val defaultHeaders: MutableMap<String, String> = HashMap()
    /** UrlTransformers that will be queried before sending the final request */
    public val identifiers: MutableList<ResourceIdentifier> = ArrayList()
    public val http: OkHttpClient = OkHttpClient()
    public val rest: RestClient = RestClient(http, userAgent);
    public val createDirectories: Boolean = true

    {
        defaultHeaders.put("User-Agent", userAgent)
    }

    /**
     * Downloads a file asynchronously. The two callback functions ([success] and [fail]) are called on the successful
     * download of a file and on a failure respectively. Note that if a UrlTransformer is used and it creates multiple
     * download targets, [success] and [fail] will be called *for each file*, while [complete] will be called only after
     * every HTTP request has returned (regardless of success).
     */
    public fun downloadAsync(url: String, directory: File, vararg contentTypes: String,
                       success: (file: File) -> Unit = {},
                       fail: (request: Request, e: Exception) -> Unit = {(request, e) -> },
                       complete: (succeeded: Int, failed: Int) -> Unit = {(succeeded, failed) -> }) {

        fun checkCompletion(total: Int, progress: Int, succeeded: Int, failed: Int) {
            if (total == progress) complete(succeeded, failed)
        }

        val request = DownloadRequest(url, directory, *contentTypes)
        log.info("Enqueuing request to download content from '${request.url}' into '${request.directory}'")
        // Make a GET request to the resolved URL
        val targets = findDownloadTargets(request.url)
        if (targets.size() == 0) {
            log.info("No targets found")
            return
        }

        var succeededRequests = 0
        var failedRequests = 0
        val totalRequests = targets.size()
        var requestCompleteCount = 0

        // Add a call for each target
        targets.forEach {
            val httpRequest = buildRequest(it)
            http.newCall(httpRequest).enqueue(object: Callback {
                override fun onFailure(request: Request, e: IOException) {
                    failedRequests++
                    fail(request, e)
                    checkCompletion(totalRequests, ++requestCompleteCount, succeededRequests, failedRequests)
                }
                override fun onResponse(response: Response?) {
                    succeededRequests++
                    try {
                        success(transferResponse(request, response!!))
                    } catch (e: Exception) {
                        fail(httpRequest, e)
                    }
                    checkCompletion(totalRequests, ++requestCompleteCount, succeededRequests, failedRequests)
                }
            })
        }
    }

    /** Downloads a resource synchronously and returns the set of files that were downloaded */
    public fun download(url: String, directory: File, vararg contentTypes: String): Set<File> {
        val request = DownloadRequest(url, directory, *contentTypes)
        log.info("Requested to download content from '${request.url}' into '${request.directory}'")
        // Make a GET request to the resolved URL
        val targets = findDownloadTargets(request.url)
        if (targets.size() == 0) {
            log.info("No targets found")
            return setOf()
        }

        // Download all the files in the list
        val downloads: MutableSet<File> = HashSet()
        targets.forEach {
            val httpRequest = buildRequest(it)
            val response = http.newCall(httpRequest).execute()
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

    /**
     * Transfers the body of a response (file) to the download request's directory
     *
     * Throws an IllegalStateException if the response header did not include a Content-Type header or if its value was
     *     not in the the request's list of Content-Types.
     * Throws a NetworkException if the HTTP request was not successful
     * Throws an IOException if [createDirectories] is true and they could not be created
     * Throws an IllegalArgumentException if the download directory already exists, but as a file
     */
    throws(javaClass<IllegalStateException>(),
            javaClass<IllegalArgumentException>(),
            javaClass<NetworkException>(),
            javaClass<IOException>())
    private fun transferResponse(request: DownloadRequest, response: Response): File {
        if (!response.isSuccessful()) {
            throw NetworkException(response.code())
        }

        // Verify the Content-Type header
        val responseContentType = response.header("Content-Type", "")
        if (responseContentType.equals("")) { // Default value is an empty string
            throw IllegalStateException("No Content-Type header returned")
        }

        if (!checkContentType(responseContentType, *request.contentTypes)) {
            throw IllegalStateException("No valid content types matched the Content-Type '$responseContentType'")
        }

        // Get the file name
        val fileName = response.request().url().getPath().split('/').last()
        log.info("File name detected as '$fileName'")

        // Create directories
        if (createDirectories) {
            if (request.directory.isFile()) {
                throw IllegalArgumentException("Download directory already exists as a file: ${request.directory}")
            }
            if (!request.directory.isDirectory() && !request.directory.mkdirs()) {
                throw IOException("Could not create directory ${request.directory}")
            }
        }

        // Write the response body to the file
        if (!request.directory.isDirectory()) {
            throw IllegalArgumentException("Download directory is not a directory or does not exist: ${request.directory}" +
                " (you can automatically create directories by enabling Kdown.createDirectories)")
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
        for (identifier in identifiers) {
            val u = URL(url)
            if (identifier.canFind(u)) {
                val resolved = identifier.find(u)
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
    private fun checkContentType(given: String, vararg acceptable: String): Boolean {
        if (acceptable.size() == 0) return true
        return acceptable.filter { it.startsWith(given) }.size() > 0
    }
}

/**
 * Provides a combination of variables used to make a download, including the URL to request, the directory to save the
 * file to, and the Content-Types that will be acceptable for the HTTP response. If no Content-Types are given, the
 * response's content type is discarded and the file will be downloaded in any case. If a [UrlTransformer] turns this
 * request into multiple files, each file will be checked against the Content-Types.
 */
private data class DownloadRequest(public val url: String,
                                  public val directory: File,
                                  public vararg val contentTypes: String)

/**
 * Indicates that an HTTP response has returned a code that is not in the range of [200, 300)
 */
public class NetworkException(public val code: Int): Exception("Request returned unsuccessul response: $code")

val log: Logger = LoggerFactory.getLogger(javaClass<Kdown>().getSimpleName())

