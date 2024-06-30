package org.vartha.news


import com.google.gson.GsonBuilder
import com.mongodb.client.MongoClients
import com.mongodb.client.MongoCollection
import com.mongodb.client.model.ReplaceOptions
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.w3c.dom.Element
import org.w3c.dom.NodeList
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URI
import java.net.URL
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.text.SimpleDateFormat
import javax.xml.parsers.DocumentBuilderFactory

data class NewsItem(
    val source: String,
    val loc: String,
    val publicationDate: String,
    var title: String,
    var images: List<String>?,
    val caption: String?,
    val category: String,
    val summarizedNews: String
)

fun main() = runBlocking {
    val nzHeraldNews = async { fetchNZHeraldNews("https://www.nzherald.co.nz/arcio/sitemap/") }
    val oneNews = async { fetchOneNews("https://www.1news.co.nz/arc/outboundfeeds/news-sitemap/") }
    val rnzNews = async { fetchRNZNews("https://www.rnz.co.nz/sitemap-news") }

    val allNews = mutableListOf<NewsItem>()
    allNews.addAll(rnzNews.await())
    allNews.addAll(nzHeraldNews.await())
    allNews.addAll(oneNews.await())

    // Sort the combined news list based on publication time
    val sortedNews = allNews.sortedByDescending { it.publicationDate }

    // Fetch and update images in parallel
    sortedNews.chunked(100).map { batch ->
        async {
            batch.forEach { newsItem ->
                if (newsItem.source == "NZ Herald") {
                    newsItem.title = fetchTitleFromUrl(newsItem.loc)
                }
                newsItem.images = fetchAndProcessImages(newsItem)
            }
        }
    }.awaitAll()

    insertIntoMongoDB(sortedNews)
    val gson = GsonBuilder().disableHtmlEscaping().create()
    val jsonOutput = gson.toJson(sortedNews)
    println(jsonOutput)
}

private fun fetchHtmlFromUrl(urlString: String): String {
    val connection = URL(urlString).openConnection() as HttpURLConnection
    connection.apply {
        requestMethod = "GET"
        setRequestProperty("User-Agent", "Mozilla/5.0")
        connect()
    }
    return connection.inputStream.bufferedReader().readText().also {
        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
            throw IOException("Server returned HTTP response code: ${connection.responseCode} for URL: $urlString")
        }
    }
}

fun extractCategory(url: String): String {
    val regex = Regex("co\\.nz/([^/]+)/")
    val category = regex.find(url)?.groups?.get(1)?.value ?: "Uncategorized"
    return if (category == "news") "nz-news" else category
}

fun formatDate(dateString: String): String {
    val formats = listOf(
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssZ"),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'"),
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX")
    )

    for (format in formats) {
        try {
            val parsedDate = format.parse(dateString)
            return SimpleDateFormat("dd/MMM/yyyy hh:mm a").format(parsedDate)
        } catch (e: Exception) {
            // Try the next format
        }
    }
    // If no valid format found, return the original string
    return dateString
}

suspend fun fetchRNZNews(xmlURL: String): List<NewsItem> {
    return fetchNews(xmlURL) { node ->
        val loc = node.getElementsByTagName("loc").item(0).textContent
        val title = node.getElementsByTagName("news:title").item(0)?.textContent ?: ""
        val publicationDate = node.getElementsByTagName("news:publication_date").item(0)?.textContent ?: ""
        val category = extractCategory(loc)
        NewsItem("RNZ", loc, formatDate(publicationDate), title, null, null, category, "")
    }
}

suspend fun fetchNZHeraldNews(xmlURL: String): List<NewsItem> {
    return fetchNews(xmlURL) { node ->
        val loc = node.getElementsByTagName("loc").item(0).textContent
        val lastmod = node.getElementsByTagName("lastmod").item(0)?.textContent ?: ""
        val category = extractCategory(loc)
        NewsItem("NZ Herald", loc, formatDate(lastmod), "", null, null, category, "")
    }
}

suspend fun fetchOneNews(xmlURL: String): List<NewsItem> {
    return fetchNews(xmlURL) { node ->
        val loc = node.getElementsByTagName("loc").item(0).textContent
        val publicationDate = node.getElementsByTagName("news:publication_date").item(0)?.textContent ?: ""
        val title = node.getElementsByTagName("news:title").item(0)?.textContent ?: ""
        val image = node.getElementsByTagName("image:loc").item(0)?.textContent
        val caption = node.getElementsByTagName("image:caption").item(0)?.textContent
        val category = extractCategory(loc)
        NewsItem("1 News", loc, formatDate(publicationDate), title, image?.let { listOf(it) }, caption, category, "")
    }
}

suspend inline fun <reified T> fetchNews(xmlURL: String, crossinline mapper: suspend (Element) -> T): List<NewsItem> =
    coroutineScope {
        val xmlContent = URL(xmlURL).readText()
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(xmlContent.byteInputStream())
        val nodeList: NodeList = document.getElementsByTagName("url")
        val newsList = mutableListOf<Deferred<T>>()

        for (i in 0 until nodeList.length) {
            val node = nodeList.item(i) as? Element ?: continue
            val newsItemDeferred = async {
                mapper(node)
            }
            newsList.add(newsItemDeferred)
        }

        // Wait for all news items to be processed
        val fetchedNewsList = newsList.awaitAll()

        // Summarize the news in parallel
        fetchedNewsList.map { item ->
            async {
                val loc = (item as NewsItem).loc
                (item as NewsItem).copy(summarizedNews = "")
            }
        }.awaitAll()
    }

suspend fun fetchAndProcessImages(newsItem: NewsItem): List<String>? {
    return withContext(Dispatchers.IO) {
        try {
            val htmlContent = fetchHtmlFromUrl(newsItem.loc)
            val imageUrls = extractImageUrls(htmlContent)
            when (newsItem.source) {
                "NZ Herald" -> imageUrls.filter { it.contains("resizer/v2/") }.map { getNZHeraldImage(it, 1024, 1024) }
                "RNZ" -> listOfNotNull(getRNZImage(imageUrls, 1050))
                else -> newsItem.images ?: emptyList()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}

fun extractImageUrls(htmlContent: String): List<String> {
    // Implement a simple regex-based image URL extraction logic
    val imageUrlPattern = Regex("""<img[^>]+src="([^">]+)"""")
    return imageUrlPattern.findAll(htmlContent).map { it.groupValues[1] }.toList()
}

fun getNZHeraldImage(url: String, newWidth: Int, newHeight: Int): String {
    val widthPattern = Regex("""(\b(w|width)=)\d+""")
    val heightPattern = Regex("""(\b(h|height)=)\d+""")
    return url.replace(widthPattern, "$1$newWidth").replace(heightPattern, "$1$newHeight")
}

fun getRNZImage(imageUrls: List<String>, desiredWidth: Int): String? {
    val widthPattern = Regex("""(\b(w|width)_$desiredWidth)\b""")
    return imageUrls.find { widthPattern.containsMatchIn(it) }
}

fun fetchTitleFromUrl(url: String): String {
    return try {
        val doc: Document = Jsoup.connect(url).get()
        doc.select("meta[property=og:title]").attr("content")
    } catch (e: Exception) {
        e.printStackTrace()
        "Title not found"
    }
}

/*
suspend fun summarizeNews(url: String): String {
    return withContext(Dispatchers.IO) {
        try {
            val requestBody = JSONObject().apply {
                put(
                    "contents",
                    JSONArray().put(JSONObject().put("parts", JSONArray().put(JSONObject().put("text", url))))
                )
            }

            val client = HttpClient.newHttpClient()
            val request = HttpRequest.newBuilder()
                .uri(URI.create("$GEMINI_API_URL?key=$YOUR_API_KEY"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(requestBody.toString()))
                .build()

            val response = client.send(request, HttpResponse.BodyHandlers.ofString())

            if (response.statusCode() == 200) {
                val responseJson = JSONObject(response.body())
                responseJson.getJSONArray("candidates").getJSONObject(0)
                    .getJSONObject("content").getJSONArray("parts")
                    .getJSONObject(0).getString("text")
            } else {
                val errorMessage = "Error from Gemini API: ${response.statusCode()} - ${response.body()}"
                println(errorMessage)
                throw RuntimeException(errorMessage)
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error summarizing article: ${e.message}"
        }
    }
}
*/

//insert into mongo db
private fun insertIntoMongoDB(newsList: List<NewsItem>) {
    val mongoClient =
        MongoClients.create("mongodb+srv://madhukalyanm:m4dhuk4ly4nN@cluster0.imxivg7.mongodb.net/?retryWrites=true&w=majority&appName=Cluster0")
    val database = mongoClient.getDatabase("vartha")
    val collectionName = "parsednews"
    val collection: MongoCollection<org.bson.Document> = database.getCollection(collectionName)

    newsList.forEach { newsItem ->
        val document = org.bson.Document()
            .append("loc", newsItem.loc)
            .append("title", newsItem.title)
            .append("publicationDate", newsItem.publicationDate)
            .append("source", newsItem.source)
            .append("images", newsItem.images)
            .append("category", newsItem.category)
            .append("summary", newsItem.summarizedNews)

        val filter = org.bson.Document("loc", newsItem.loc)
        val options = ReplaceOptions().upsert(true)

        collection.replaceOne(filter, document, options)
    }

    mongoClient.close()
}
