package be.vanmechelen.vrtnws.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class OkHttpServicesTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)).bufferedReader().use { it.readText() }

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun feedServiceFetchesAndParses() = runTest {
        server.enqueue(MockResponse().setBody(fixture("feed_sample.xml")))
        val service = OkHttpFeedService(client, server.url("/feed").toString())
        val headlines = service.fetchHeadlines()
        assertTrue(headlines.size > 20)
        assertEquals("https://vrtnws.be/p.Wk6pRlL87", headlines.first().url)
    }

    @Test
    fun articleServiceFetchesAndExtracts() = runTest {
        server.enqueue(MockResponse().setBody(fixture("article_regular.html")))
        val service = OkHttpArticleService(client)
        val content = service.fetchBody(server.url("/article").toString())
        assertTrue(content.plainText.contains("Delhaize"))
    }

    @Test
    fun httpErrorThrows() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val service = OkHttpFeedService(client, server.url("/feed").toString())
        val error = runCatching { service.fetchHeadlines() }.exceptionOrNull()
        assertTrue("expected IOException, got $error", error is IOException)
    }
}
