Kdown [![Travis](http://img.shields.io/travis/thatJavaNerd/Kdown.svg?style=flat)](https://travis-ci.org/thatJavaNerd/Kdown)
=====

Kdown provides a simple but powerful interface to download files both synchronously and asynchronously.

```kotlin
val kdown = Kdown()
val url = "https://i.imgur.com/Yf2kWzd.png"
val directory = File("my-download-dir")

// Download the file, not caring about Content-Types
kdown.download(url, directory)
// Specify one or more Content-Types that the response must have
kdown.download(url, directory, "image/png", "image/jpeg", "image/gif")
```

`downloadAsync()` comes with a few other optional arguments that takes advantage of Kotlin's language features

```kotlin

// Just download the file. There's no way to tell when this will be completed or
// if it actually succeeded
kdown.downloadAsync(url, directory)

// Pass function literals that will be called after the file has been downloaded
// or the request has failed
kdown.downloadAsync(DownloadRequest(url, directory),
        success = { downloadedFile -> println("Downloaded $url to $downloadedFile") },
        fail = { (request, exception) ->
            // handle me
        })
```

### Resources
Kdown tries to make the concept of downloading files a little more abstract with the concept of a "resource". A resource is a set of one or more files that is derived from a given URL. For example, an imgur album with the URL of [`https://imgur.com/a/C1yQx`](https://imgur.com/a/C1yQx) can be interpreted as either a file (the HTML page) or a resource (the images in the album).

In most cases, when we send a download request to `https://imgur.com/a/C1yQx`, we don't really want to download the file, but rather the resource. To do this, we can add a `ResourceIdentifier` to our `Kdown` object

```kotlin
kdown.identifiers.add(object: ResourceIdentifier {
    override fun canFind(url: URL): Boolean {
        // check if url is an imgur album (regex, etc.)
    }

    override fun find(url: URL): Set<String> {
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

Kdown comes built in with several `ResourceIdentifier`s, including the one that describes the process above (`ImgurResourceIdentifier`)
