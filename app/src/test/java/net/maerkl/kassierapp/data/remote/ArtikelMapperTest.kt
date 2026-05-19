package net.maerkl.kassierapp.data.remote

import com.google.firebase.firestore.DocumentSnapshot
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ArtikelMapperTest {

    private fun doc(
        id: String = "a1",
        name: String? = "Bratwurst",
        emoji: String? = "🌭",
        preis: Long? = 250L,
        taxRate: Long? = 7L,
        aktiv: Boolean? = true,
    ): DocumentSnapshot {
        val d = mockk<DocumentSnapshot>()
        every { d.id } returns id
        every { d.getString("name") } returns name
        every { d.getString("emoji") } returns emoji
        every { d.getLong("preis") } returns preis
        every { d.getLong("taxRate") } returns taxRate
        every { d.getBoolean("aktiv") } returns aktiv
        return d
    }

    @Test
    fun `maps complete document`() {
        val a = ArtikelMapper.fromDocument(doc())!!
        assertEquals("a1", a.id)
        assertEquals("Bratwurst", a.name)
        assertEquals("🌭", a.emoji)
        assertEquals(250L, a.preisCent)
        assertEquals(7, a.taxRate)
        assertEquals(true, a.aktiv)
    }

    @Test
    fun `returns null when name missing`() {
        assertNull(ArtikelMapper.fromDocument(doc(name = null)))
    }

    @Test
    fun `returns null when preis missing`() {
        assertNull(ArtikelMapper.fromDocument(doc(preis = null)))
    }

    @Test
    fun `defaults taxRate to 0 when missing`() {
        val a = ArtikelMapper.fromDocument(doc(taxRate = null))!!
        assertEquals(0, a.taxRate)
    }

    @Test
    fun `defaults aktiv to false when missing`() {
        val a = ArtikelMapper.fromDocument(doc(aktiv = null))!!
        assertEquals(false, a.aktiv)
    }

    @Test
    fun `emoji may be null`() {
        val a = ArtikelMapper.fromDocument(doc(emoji = null))!!
        assertNull(a.emoji)
    }

    @Test
    fun `rejects negative preis`() {
        assertNull(ArtikelMapper.fromDocument(doc(preis = -1L)))
    }

    @Test
    fun `rejects oversized preis`() {
        assertNull(ArtikelMapper.fromDocument(doc(preis = 1_000_000_01L)))
    }

    @Test
    fun `rejects unknown taxRate`() {
        assertNull(ArtikelMapper.fromDocument(doc(taxRate = 5L)))
    }

    @Test
    fun `accepts taxRate 0 7 and 19`() {
        assertEquals(0, ArtikelMapper.fromDocument(doc(taxRate = 0L))!!.taxRate)
        assertEquals(7, ArtikelMapper.fromDocument(doc(taxRate = 7L))!!.taxRate)
        assertEquals(19, ArtikelMapper.fromDocument(doc(taxRate = 19L))!!.taxRate)
    }
}
