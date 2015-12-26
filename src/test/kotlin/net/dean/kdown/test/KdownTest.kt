package net.dean.kdown.test

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import net.dean.kdown.*
import org.testng.Assert.*
import org.testng.annotations.BeforeClass
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.io.File
import java.io.InputStream
import java.net.URL
import kotlin.collections.forEach
import kotlin.collections.setOf

public class KdownTest {
    private val dl = Kdown("KdownTest by github.com/thatJavaNerd")
    private val url = "https://i.imgur.com/jxQ3mNq.jpg"
    private val urlGif = "https://imgur.com/2ZKaO7c"
    private val dir = File("test-downloads/one/two/three");

    public @Test fun directDownload() {
        assertDownloaded(dl.download(url, dir))
    }

    public @Test fun downloadWithQueryAndFragment() {
        assertDownloaded(dl.download(url + "?foo=bar#test", dir))
    }

    public @Test fun downloadWithContentTypes() {
        assertDownloaded(dl.download(url, dir, setOf("image/jpeg", "image/png", "image/gif")))
    }

    public @Test(expectedExceptions = arrayOf(IllegalStateException::class)) fun downloadInvalidContentType() {
        assertDownloaded(dl.download(url, dir, setOf("invalid/type")))
    }

    public @Test fun downloadWithBasicTransformer() {
        // Note that this is not testing the BasicTransformer class, but rather the ResourceIdentifier trait in general
        dl.identifiers.add(BasicIdentifier(url, setOf("https://i.imgur.com/ILyfCJr.gif")))
        assertDownloaded(dl.download(url, dir))
    }

    public @Test fun downloadUrlMultipleTargets() {
        val expected = setOf(
                "https://i.imgur.com/R0aLTh9.png",
                "https://i.imgur.com/FULeSIt.png",
                "https://i.imgur.com/irzzg2F.png"
        )
        dl.identifiers.add(BasicIdentifier(url, expected))

        val actual = dl.download(url, dir)
        assertEquals(actual.size, expected.size, "Expected and actual download lists were not of the same size")
        assertDownloaded(actual)
    }

    public @Test fun downloadAsync() {
        val expected = setOf(
                "https://i.imgur.com/R0aLTh9.png",
                "https://i.imgur.com/FULeSIt.png",
                "https://i.imgur.com/irzzg2F.png"
        )
        dl.identifiers.add(BasicIdentifier(url, expected))

        dl.downloadAsync(url, dir, setOf(), object: ProgressTrackerAdapter() {
            override fun fileFailed(source: URL, cause: Exception) {
                fail("Async request to $source failed", cause)
            }

            override fun fileComplete(source: URL, location: File) {
                assertDownloaded(location)
            }
        })
    }

    public @Test fun imgurAlbum() {
        val expected = setOf(
                "https://i.imgur.com/ExsgFVf.png",
                "https://i.imgur.com/Otd8M6k.png",
                "https://i.imgur.com/rYAV4yU.png",
                "https://i.imgur.com/kkd6QHb.png",
                "https://i.imgur.com/Yf2kWzd.png",
                "https://i.imgur.com/Yr0L3hI.png",
                "https://i.imgur.com/l0JtV5D.png"
        )
        dl.identifiers.add(ImgurResourceIdentifier(dl, getSecret("IMGUR")))
        val actual = dl.download("https://imgur.com/a/C1yQx?extraQuery", dir)
        assertEquals(actual.size, expected.size, "Expected and actual download lists were not of the same size")
        assertDownloaded(actual)
    }

    public @Test fun imgurGallery() {
        val expected = setOf(
                "https://i.imgur.com/KZRfzgE.png",
                "https://i.imgur.com/awMcACA.png",
                "https://i.imgur.com/NrJ2mJ6.png",
                "https://i.imgur.com/NiQ6laA.png",
                "https://i.imgur.com/AMtHczv.png",
                "https://i.imgur.com/7kPfCsu.png",
                "https://i.imgur.com/ZTdBuBl.png"
        )

        dl.identifiers.add(ImgurResourceIdentifier(dl, getSecret("IMGUR")))
        val actual = dl.download("https://imgur.com/gallery/0rH2B", dir)
        assertEquals(actual.size, expected.size, "Expected and actual download lists were not of the same size")
        assertDownloaded(actual)
    }

    public @Test fun imgurImage() {
        dl.identifiers.add(ImgurResourceIdentifier(dl, getSecret("IMGUR")))
        val actual = dl.download(urlGif, dir, setOf("image/gif"))
        assertDownloaded(actual)
    }

    public @Test fun imgurGifAltFormats() {
        val identifier = ImgurResourceIdentifier(dl, getSecret("IMGUR"))
        identifier.resourceVersion = ImgurGifFormat.WEBM

        dl.identifiers.add(identifier)
        val actual = dl.download(urlGif, dir, setOf("video/webm"))
        assertDownloaded(actual)
    }

    public @Test fun gfycat() {
        dl.identifiers.add(GfycatResourceIdentifier(dl))
        assertDownloaded(dl.download("https://gfycat.com/EagerSillyDogfish", dir, setOf("video/webm")))
    }

    public @Test fun downloadAsyncWithCompleteCallback() {
        val url = "https://imgur.com/abc123"
        dl.identifiers.add(BasicIdentifier(url, setOf(
                "https://i.imgur.com/R63Dt47.jpg", // Will complete fine
                "https://i.imgur.com/rxTnbQs.jpg", // Will complete fine
                "https://i.imgur.com/failme.jpg"   // Will fail miserably
        )))
        val expectedSuccessCount = 2
        val expectedFailCount = 1

        var successCount = 0
        var failCount = 0
        dl.downloadAsync(url, dir, contentTypes = setOf(), tracker = object: ProgressTrackerAdapter() {
            override fun fileFailed(source: URL, cause: Exception) {
                failCount++
            }

            override fun fileComplete(source: URL, location: File) {
                successCount++
            }

            override fun resourceComplete(succeeded: List<URL>, failed: List<URL>) {
                // Test the successes
                assertEquals(successCount, expectedSuccessCount, "Calculated success count did not match the expected success count")
                assertEquals(succeeded, expectedSuccessCount, "Internally-calculated success count did not match the expected success count")

                // Test the failures
                assertEquals(failCount, expectedFailCount, "Calculated failure count did not match the expected failure count")
                assertEquals(failed, expectedFailCount, "Internally-calculated failure count did not match the expected failure count")
            }
        })
    }

    public @BeforeClass fun beforeClass() {
        dir.mkdirs()
    }

    public @BeforeMethod fun beforeTest() {
        dl.identifiers.clear()
    }

    private fun assertDownloaded(files: Set<File>) {
        files.forEach {
            assertDownloaded(it)
        }
    }

    private fun assertDownloaded(file: File) {
        assertTrue(file.isFile, "File $file does not exist or is not a file")
        if (!file.delete()) {
            System.err.println("Could not delete ${file.absolutePath}")
        }
    }
}

private val secrets: JsonNode by lazy {
    val input: InputStream? = KdownTest::class.java.getResourceAsStream("/secrets.json")
            ?: throw IllegalStateException("Please create the file src/test/resources/secrets.json")
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
private class BasicIdentifier(private val url: String, private val result: Set<String>) : ResourceIdentifier {
    override fun find(url: URL): Set<String> {
        return result
    }

    override fun canFind(url: URL): Boolean {
        return url.equals(url)
    }
}

