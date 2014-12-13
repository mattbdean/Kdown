Kdown [![Travis](http://img.shields.io/travis/thatJavaNerd/Kdown.svg?style=flat)](https://travis-ci.org/thatJavaNerd/Kdown)
=====

Kdown provides a simple but powerful interface to download files both synchronously and asynchronously.

```kotlin
val kdown = Kdown()
val url = "https://i.imgur.com/Yf2kWzd.png"
val directory = File("my-download-dir")

// Download the file, not caring about Content-Types
kdown.download(DownloadRequest(url, directory))
// Specify one or more Content-Types that the request must have
kdown.download(DownloadRequest(url, directory, "image/png", "image/jpeg", "image/gif"))
```

`downloadAsync()` comes with a few other optional arguments that takes advantage of Kotlin's language features

```kotlin

// Just download the file. There's no way to tell when this will be completed or it actually succeeded
kdown.downloadAsync(DownloadRequest(url, directory))

// Pass function literals that will be called after the file has been downloaded
// or the request has failed
kdown.downloadAsync(DownloadRequest(url, directory),
        success = { downloadedFile -> println("Downloaded $url to $downloadedFile") },
        fail = { (request, exception) ->
            // handle me
        })
```

### URL Transformers
Kdown provides a way to intercept download requests and change them into one or more different URLs before they're executed. Suppose we make a request to an [imgur album](https://imgur.com/a/C1yQx). We don't really want to download that file (which would be an HTML page), we want the images *in* the album. To create this functionality, we can use a `UrlTransformer`.

```kotlin
kdown.transformers.add(object: UrlTransformer {
    override fun willTransform(url: URL): Boolean {
        // check if url is an imgur album (regex, etc.)
    }

    override fun transform(url: URL): Set<String> {
         // use the imgur API to query the images in the album and return them
    }
})
```

This way when we request an imgur album

```kotlin
kdown.download("https://imgur.com/a/C1yQx", directory)
```

It will download all of these files instead:
```
https://i.imgur.com/ExsgFVf.png
https://i.imgur.com/Otd8M6k.png
https://i.imgur.com/rYAV4yU.png
https://i.imgur.com/kkd6QHb.png
https://i.imgur.com/Yf2kWzd.png
https://i.imgur.com/Yr0L3hI.png
https://i.imgur.com/l0JtV5D.png
```
