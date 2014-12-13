package net.dean.kdown.test

import java.io.File
import java.net.URL
import org.testng.Assert
import org.testng.annotations.BeforeClass as beforeClass
import org.testng.annotations.Test as test
import org.testng.annotations.BeforeMethod as beforeMethod
import net.dean.kdown.Kdown
import net.dean.kdown.UrlTransformer
import net.dean.kdown.DownloadRequest
import net.dean.kdown.ImgurTransformer
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import kotlin.properties.Delegates
import com.fasterxml.jackson.databind.JsonNode
import java.io.InputStream

public class KdownTest {
    private val dl = Kdown("KdownTest by github.com/thatJavaNerd")
    private val url = "https://i.imgur.com/jxQ3mNq.jpg"
    private val dir = File("test-downloads")

    public test fun directDownload() {
        assertDownloaded(dl.download(DownloadRequest(url, dir)))
    }

    public test fun downloadWithContentTypes() {
        assertDownloaded(dl.download(DownloadRequest(url, dir, "image/jpeg", "image/png", "image/gif")))
    }

    public test(expectedExceptions = array(javaClass<IllegalStateException>())) fun downloadInvalidContentType() {
        assertDownloaded(dl.download(DownloadRequest(url, dir, "invalid/type")))
    }

    public test fun downloadWithBasicTransformer() {
        // Note that this is not testing the BasicTransformer class, but rather the UrlTransformer trafile in general
        dl.transformers.add(BasicTransformer(url, setOf("https://i.imgur.com/ILyfCJr.gif")))
        assertDownloaded(dl.download(DownloadRequest(url, dir)))
    }

    public test fun downloadUrlMultipleTargets() {
        val expected = setOf(
                "https://i.imgur.com/R0aLTh9.png",
                "https://i.imgur.com/FULeSIt.png",
                "https://i.imgur.com/irzzg2F.png"
        )
        dl.transformers.add(BasicTransformer(url, expected))

        val actual = dl.download(DownloadRequest(url, dir))
        Assert.assertEquals(actual.size(), expected.size(), "Expected and actual download lists were not of the same size")
        assertDownloaded(actual)
    }

    public test fun downloadAsync() {
        val expected = setOf(
                "https://i.imgur.com/R0aLTh9.png",
                "https://i.imgur.com/FULeSIt.png",
                "https://i.imgur.com/irzzg2F.png"
        )
        dl.transformers.add(BasicTransformer(url, expected))

        dl.downloadAsync(DownloadRequest(url, dir),
                success = { assertDownloaded(it) },
                fail = { (request, exception) -> Assert.fail("Async request to ${request.url()} failed", exception) })
    }

    public test fun imgurAlbum() {
        val expected = setOf(
                "https://i.imgur.com/ExsgFVf.png",
                "https://i.imgur.com/Otd8M6k.png",
                "https://i.imgur.com/rYAV4yU.png",
                "https://i.imgur.com/kkd6QHb.png",
                "https://i.imgur.com/Yf2kWzd.png",
                "https://i.imgur.com/Yr0L3hI.png",
                "https://i.imgur.com/l0JtV5D.png"
        )
        dl.transformers.add(ImgurTransformer(dl.rest, getSecret("IMGUR")))
        val actual = dl.download(DownloadRequest("https://imgur.com/a/C1yQx", dir))
        Assert.assertEquals(actual.size(), expected.size(), "Expected and actual download lists were not of the same size")
        assertDownloaded(actual)
    }

    public test fun imgurGallery() {
        val expected = setOf(
                "https://i.imgur.com/KZRfzgE.png",
                "https://i.imgur.com/awMcACA.png",
                "https://i.imgur.com/NrJ2mJ6.png",
                "https://i.imgur.com/NiQ6laA.png",
                "https://i.imgur.com/AMtHczv.png",
                "https://i.imgur.com/7kPfCsu.png",
                "https://i.imgur.com/ZTdBuBl.png"
        )

        dl.transformers.add(ImgurTransformer(dl.rest, getSecret("IMGUR")))
        val actual = dl.download(DownloadRequest("https://imgur.com/gallery/0rH2B", dir))
        Assert.assertEquals(actual.size(), expected.size(), "Expected and actual download lists were not of the same size")
        assertDownloaded(actual)
    }

    public beforeClass fun beforeClass() {
        dir.mkdirs()
    }

    public beforeMethod fun beforeTest() {
        dl.transformers.clear()
    }

    private fun assertDownloaded(files: Set<File>) {
        files.forEach {
            assertDownloaded(it)
        }
    }

    private fun assertDownloaded(file: File) {
        Assert.assertTrue(file.isFile(), "File $file does not exist or is not a file")
        if (!file.delete()) {
            System.err.println("Could not delete ${file.getAbsolutePath()}")
        }
    }
}

private val secrets: JsonNode by Delegates.lazy {
    val input: InputStream? = javaClass<KdownTest>().getResourceAsStream("/secrets.json")
    if (input == null)
        throw IllegalStateException("Please create the file src/test/resources/secrets.json")
    jacksonObjectMapper().readTree(input)
}

private fun getSecret(name: String): String {
    // If running locally, use credentials file
    // If running with Travis-CI, use env variables
    if (System.getenv("TRAVIS") != null && System.getenv("TRAVIS").equals("true")) {
        return System.getenv(name)
    } else {
        return secrets.get(name).asText()
    }
}

/**
 * A transformer that will transform a URL if and only if the given URL is equal to the URL given in the constructor
 */
private class BasicTransformer(private val url: String, private val result: Set<String>) : UrlTransformer {
    override fun transform(url: URL): Set<String> {
        return result
    }

    override fun willTransform(url: URL): Boolean {
        return url.equals(url)
    }
}

