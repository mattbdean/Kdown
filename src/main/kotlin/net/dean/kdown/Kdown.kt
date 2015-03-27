package net.dean.kdown

import java.io.File
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
import java.io.FileOutputStream

public class Kdown(userAgent: String) {
    /** Headers that will be sent with every request */
    public val defaultHeaders: MutableMap<String, String> = HashMap()
    /** UrlTransformers that will be queried before sending the final request */
    public val identifiers: MutableList<ResourceIdentifier> = ArrayList()
    public val http: OkHttpClient = OkHttpClient()
    public val rest: RestClient = RestClient(http, userAgent);
    public var createDirectories: Boolean = true
    public var bufferSize: Int = 4096

    init {
        defaultHeaders.put("User-Agent", userAgent)
    }

    /** Downloads a resource asynchronously. If [contentTypes] is empty, then any Content-Type will be accepted. */
    public fun downloadAsync(url: String, directory: File, contentTypes: Set<String>,
                             tracker: ProgressTracker = ProgressTrackerAdapter()) {

        val succeeded: MutableList<URL> = ArrayList()
        val failed: MutableList<URL> = ArrayList()

        fun checkCompletion(succeeded: List<URL>, failed: List<URL>, expectedTotal: Int) {
            if (expectedTotal == succeeded.size() + failed.size())
                tracker.resourceComplete(succeeded, failed)
        }

        val request = DownloadRequest(URL(url), directory, contentTypes)
        log.info("Enqueuing request to download content from '${request.url}' into '${request.directory}'")
        // Make a GET request to the resolved URL
        val targets = findDownloadTargets(request.url)
        if (targets.size() == 0) {
            log.info("No targets found")
            return
        }

        // Add a call for each target
        targets.forEach { url ->
            val httpRequest = buildRequest(url)
            http.newCall(httpRequest).enqueue(object: Callback {
                override fun onResponse(response: Response) {
                    try {
                        // Transfer the file from memory to the file system
                        val newFile = transferResponse(request, response, tracker)
                        tracker.fileComplete(url, newFile)
                        succeeded.add(url)
                        checkCompletion(succeeded, failed, targets.size())
                    } catch (e: IOException) {
                        tracker.fileFailed(url, e)
                    }
                }
                override fun onFailure(request: Request, e: IOException) {
                    failed.add(url)
                    tracker.fileFailed(url, e)
                }
            })
        }
    }

    /** Downloads a resource synchronously and returns the set of files that were downloaded */
    public fun download(url: String, directory: File, contentTypes: Set<String> = setOf(), tracker: ProgressTracker = ProgressTrackerAdapter()): Set<File> {
        val request = DownloadRequest(URL(url), directory, contentTypes)
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
            downloads.add(transferResponse(request, response, tracker))
        }

        return downloads
    }

    /** Builds a GET request to the given URL and adds the default headers */
    private fun buildRequest(url: URL): Request {
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
     * @throws IllegalStateException If the response header did not include a Content-Type header or if its value was
     *     not in the the request's list of Content-Types.
     * @throws NetworkException If the HTTP request was not successful
     * @throws IOException If [createDirectories] is true and they could not be created
     * @throws IllegalArgumentException If the download directory already exists, but as a file
     */
    throws(javaClass<IllegalStateException>(),
            javaClass<IllegalArgumentException>(),
            javaClass<NetworkException>(),
            javaClass<IOException>())
    private fun transferResponse(request: DownloadRequest, response: Response, tracker: ProgressTracker): File {
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

        // Create directories
        if (createDirectories) {
            if (request.directory.isFile()) {
                throw IllegalArgumentException("Download directory already exists as a file: ${request.directory}")
            }
            if (!request.directory.isDirectory() && !request.directory.mkdirs()) {
                throw IOException("Could not create directory ${request.directory}")
            }
        }

        val contentLength = response.header("Content-Length")
        val totalLength: Long

        try {
            totalLength = contentLength.toLong()
        } catch (e: NumberFormatException) {
            throw IllegalStateException("Could not parse Content-Length header (value was '$contentLength')")
        }

        // Write the response body to the file
        if (!request.directory.isDirectory()) {
            throw IllegalArgumentException("Download directory is not a directory or does not exist: ${request.directory}")
        }

        val input = response.body().source()
        val location = File(request.directory, fileName)
        val out = FileOutputStream(location)
        val buffer = ByteArray(bufferSize)
        var len: Int
        var writtenBytes: Long = 0
        while (!input.exhausted()) {
            len = input.read(buffer)
            writtenBytes += len
            out.write(buffer, 0, len)
            tracker.fileProgressed(response.request().url(), location, writtenBytes, totalLength, writtenBytes.toDouble() / totalLength)
        }

        input.close()
        out.close()
        log.info("Downloaded file to $location")
        return location
    }

    /** Finds the first transformer that can transform the given URL and returns the new URL(s) */
    private fun findDownloadTargets(url: URL): Set<URL> {
        log.debug("Trying to resolve URL '$url'")
        for (identifier in identifiers) {
            if (identifier.canFind(url)) {
                val resolved = identifier.find(url)
                log.debug("Resolved '$url' to '$resolved'")
                return resolved.map { URL(it) }.toSet()
            }
        }

        return setOf(url)
    }

    /**
     * Checks if any of the given acceptable Content-Types starts with the given Content-Type. Returns true if
     * [acceptable] is empty
     */
    private fun checkContentType(given: String, acceptable: Set<String>): Boolean {
        if (acceptable.size() == 0) return true
        return acceptable.filter { it.startsWith(given) }.size() > 0
    }
}

/**
 * Provides a combination of variables used to make a download, including the URL to request, the directory to save the
 * file to, and the Content-Types that will be acceptable for the HTTP response. If no Content-Types are given, the
 * response's content type is discarded and the file will be downloaded in any case. If a [ResourceIdentifier] turns
 * this request into multiple files, each file will be checked against the Content-Types.
 */
private data class DownloadRequest(public val url: URL,
                                   public val directory: File,
                                   public val contentTypes: Set<String>)

public trait ProgressTracker {
    /** Called when a single file has failed to download. */
    public fun fileFailed(source: URL, cause: Exception)
    /** Called after some part of the file has been downloaded. */
    public fun fileProgressed(source: URL, location: File, currentBytes: Long, totalBytes: Long, percentage: Double)
    /** Called when a single file has been successfully downloaded. */
    public fun fileComplete(source: URL, location: File)
    /** Called when all the files in a resource have been downloaded. */
    public fun resourceComplete(succeeded: List<URL>, failed: List<URL>)
}

/** Simple implementation of ProgressTracker whose methods do nothing. */
public open class ProgressTrackerAdapter : ProgressTracker {
    override fun fileFailed(source: URL, cause: Exception) {}
    override fun fileProgressed(source: URL, location: File, currentBytes: Long, totalBytes: Long, percentage: Double) {}
    override fun fileComplete(source: URL, location: File) {}
    override fun resourceComplete(succeeded: List<URL>, failed: List<URL>) {}
}

/** Indicates that an HTTP response has returned a code that is not in the range of [200, 300) */
public class NetworkException(public val code: Int): RuntimeException("Request returned unsuccessful response: $code")

val log: Logger = LoggerFactory.getLogger("Kdown")

