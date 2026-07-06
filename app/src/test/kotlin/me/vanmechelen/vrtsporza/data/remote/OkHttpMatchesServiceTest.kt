package me.vanmechelen.vrtsporza.data.remote

import kotlinx.coroutines.test.runTest
import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.IOException

class OkHttpMatchesServiceTest {

    private lateinit var server: MockWebServer
    private val client = OkHttpClient()

    private fun fixture(name: String): String =
        checkNotNull(javaClass.classLoader?.getResourceAsStream(name)).bufferedReader().use { it.readText() }

    @Before fun setUp() { server = MockWebServer().apply { start() } }
    @After fun tearDown() { server.shutdown() }

    @Test
    fun fetchDetailFetchesAndExtracts() = runTest {
        server.enqueue(MockResponse().setBody(fixture("sporza_match.html")))
        val service = OkHttpMatchesService(client)
        val detail = service.fetchDetail(server.url("/match").toString())
        assertTrue(detail.events.isNotEmpty())
        assertTrue(detail.stream.isNotEmpty())
    }

    @Test
    fun httpErrorThrows() = runTest {
        server.enqueue(MockResponse().setResponseCode(500))
        val service = OkHttpMatchesService(client)
        val error = runCatching { service.fetchDetail(server.url("/match").toString()) }.exceptionOrNull()
        assertTrue("expected IOException, got $error", error is IOException)
    }
}
