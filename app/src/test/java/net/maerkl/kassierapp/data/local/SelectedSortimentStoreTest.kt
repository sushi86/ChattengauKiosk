package net.maerkl.kassierapp.data.local

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SelectedSortimentStoreTest {

    private class InMemoryStore(initial: String? = null) : SelectedSortimentStore {
        private val _state = MutableStateFlow(initial)
        override val selectedSortimentId: StateFlow<String?> = _state.asStateFlow()
        override fun set(id: String?) { _state.value = id }
    }

    @Test
    fun `initial value is null`() {
        val s = InMemoryStore()
        assertNull(s.selectedSortimentId.value)
    }

    @Test
    fun `set persists value`() {
        val s = InMemoryStore()
        s.set("sort-1")
        assertEquals("sort-1", s.selectedSortimentId.value)
    }

    @Test
    fun `set null clears value`() {
        val s = InMemoryStore("sort-1")
        s.set(null)
        assertNull(s.selectedSortimentId.value)
    }
}
