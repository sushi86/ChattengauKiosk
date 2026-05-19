package net.maerkl.kassierapp.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SortimentMapperTest {

    private fun doc(
        id: String = "s1",
        name: String? = "Hauptsortiment",
        articleIds: Any? = listOf("a1", "a2"),
    ): DocumentSnapshot {
        val d = mockk<DocumentSnapshot>()
        every { d.id } returns id
        every { d.getString("name") } returns name
        every { d.get("articleIds") } returns articleIds
        return d
    }

    @Test
    fun `maps complete document`() {
        val s = SortimentMapper.fromDocument(doc())!!
        assertEquals("s1", s.id)
        assertEquals("Hauptsortiment", s.name)
        assertEquals(listOf("a1", "a2"), s.articleIds)
    }

    @Test
    fun `returns null when name missing`() {
        assertNull(SortimentMapper.fromDocument(doc(name = null)))
    }

    @Test
    fun `defaults articleIds to empty list when missing`() {
        val s = SortimentMapper.fromDocument(doc(articleIds = null))!!
        assertEquals(emptyList<String>(), s.articleIds)
    }

    @Test
    fun `preserves articleIds order`() {
        val s = SortimentMapper.fromDocument(doc(articleIds = listOf("c", "a", "b")))!!
        assertEquals(listOf("c", "a", "b"), s.articleIds)
    }
}
