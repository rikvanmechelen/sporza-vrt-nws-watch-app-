package be.vanmechelen.vrtnws.ui.article

import be.vanmechelen.vrtnws.ui.theme.Section
import org.junit.Assert.assertEquals
import org.junit.Test

class ArticleReaderModelTest {

    @Test
    fun `news section reads as VRT NWS`() {
        val source = readerSourceFor(Section.NEWS)
        assertEquals("VRT NWS", source.label)
        assertEquals("N", source.mark)
    }

    @Test
    fun `sport section reads as Sporza`() {
        val source = readerSourceFor(Section.SPORT)
        assertEquals("Sporza", source.label)
        assertEquals("S", source.mark)
    }

    @Test
    fun `kicker takes the first of several pipe-delimited tags`() {
        assertEquals(
            "WIELRENNEN",
            kickerLabel("WIELRENNEN|TOUR DE FRANCE WIELRENNEN|LENNERT LISSENS"),
        )
    }

    @Test
    fun `kicker passes a plain single tag through, trimmed`() {
        assertEquals("Gezondheid", kickerLabel("  Gezondheid  "))
    }

    @Test
    fun `kicker is null when absent or blank`() {
        assertEquals(null, kickerLabel(null))
        assertEquals(null, kickerLabel("   "))
    }
}
