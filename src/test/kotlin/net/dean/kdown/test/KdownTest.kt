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
import com.squareup.okhttp.Response

public class KdownTest {
    private val dl = Kdown("KdownTest by gfilehub.com/thatJavaNerd")
    private val url = "https://i.imgur.com/jxQ3mNq.jpg"
    private val dir = File("test-downloads")

    public test fun directDownload() {
        println(dir.canonicalPath)
        assertDownloaded(dl.download(DownloadRequest(url, dir)))
    }

    public test fun downloadWfilehContentTypes() {
        assertDownloaded(dl.download(DownloadRequest(url, dir, "image/jpeg", "image/png", "image/gif")))
    }

    public test(expectedExceptions = array(javaClass<IllegalStateException>())) fun downloadInvalidContentType() {
        dl.download(DownloadRequest(url, dir, "invalid/type"))
    }

    public test fun downloadWfilehBasicTransformer() {
        // Note that this is not testing the BasicTransformer class, but rather the UrlTransformer trafile in general
        dl.transformers.add(object: BasicTransformer(url) {
            override fun transform(url: URL): Set<String> {
                return setOf("https://i.imgur.com/ILyfCJr.gif")
            }
        })
        assertDownloaded(dl.download(DownloadRequest(url, dir)))
    }

    public test fun downloadUrlMultipleTargets() {
        val expected = setOf(
                "https://i.imgur.com/R0aLTh9.png",
                "https://i.imgur.com/FULeSIt.png",
                "https://i.imgur.com/irzzg2F.png"
        )
        dl.transformers.add(object: BasicTransformer(url) {
            override fun transform(url: URL): Set<String> {
                return expected
            }
        })

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
        dl.transformers.add(object: BasicTransformer(url) {
            override fun transform(url: URL): Set<String> {
                return expected
            }
        })

        dl.downloadAsync(DownloadRequest(url, dir),
                success = { assertDownloaded(it) },
                fail = {(request, exception) -> Assert.fail("Async request to ${request.url()} failed", exception)})
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

/**
 * A transformer that will transform a URL if and only if the given URL is equal to the URL given in the constructor
 */
private abstract class BasicTransformer(private val url: String) : UrlTransformer {
    override fun willTransform(url: URL): Boolean {
        return url.equals(url)
    }
}
