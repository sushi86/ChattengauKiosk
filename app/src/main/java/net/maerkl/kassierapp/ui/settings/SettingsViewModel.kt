package net.maerkl.kassierapp.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import net.maerkl.kassierapp.KassierApplication
import net.maerkl.kassierapp.data.local.AppDatabase
import net.maerkl.kassierapp.data.local.Article
import net.maerkl.kassierapp.data.local.ArticleCollection
import net.maerkl.kassierapp.data.local.MANUAL_PRICE_ARTICLE_NAME

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KassierApplication
    private val dao = app.database.articleDao()
    private val saleDao = app.database.saleDao()
    private val collectionDao = app.database.articleCollectionDao()
    private val settings = app.settingsDataStore

    val allCollections = collectionDao.getAll()

    val activeCollectionId = settings.activeCollectionId
        .stateIn(viewModelScope, SharingStarted.Eagerly, 1L)

    @OptIn(ExperimentalCoroutinesApi::class)
    val allArticles = activeCollectionId.flatMapLatest { collectionId ->
        dao.getAllArticles(collectionId).map { articles ->
            articles.filter { it.name != MANUAL_PRICE_ARTICLE_NAME }
        }
    }

    val affiliateKey = settings.affiliateKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val oauthToken = settings.oauthToken.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val pin = settings.pin.stateIn(viewModelScope, SharingStarted.Eagerly, "0000")

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    private val _merchantInfo = MutableStateFlow<String?>(null)
    val merchantInfo = _merchantInfo.asStateFlow()

    fun selectCollection(id: Long) {
        viewModelScope.launch {
            settings.saveActiveCollectionId(id)
        }
    }

    fun addCollection(name: String) {
        viewModelScope.launch {
            val id = collectionDao.insert(ArticleCollection(name = name))
            settings.saveActiveCollectionId(id)
            AppDatabase.insertDefaultArticles(dao, id)
            _snackbarMessage.emit("Collection \"$name\" erstellt")
        }
    }

    fun renameCollection(collection: ArticleCollection, newName: String) {
        viewModelScope.launch {
            collectionDao.update(collection.copy(name = newName))
        }
    }

    fun deleteCollection(collection: ArticleCollection) {
        viewModelScope.launch {
            val count = collectionDao.getCount()
            if (count <= 1) {
                _snackbarMessage.emit("Letzte Collection kann nicht gelöscht werden")
                return@launch
            }
            dao.deleteAllByCollection(collection.id)
            saleDao.deleteAllByCollection(collection.id)
            collectionDao.delete(collection)
            if (activeCollectionId.value == collection.id) {
                val first = collectionDao.getFirst()
                if (first != null) {
                    settings.saveActiveCollectionId(first.id)
                }
            }
            _snackbarMessage.emit("\"${collection.name}\" gelöscht")
        }
    }

    fun addArticle(name: String, price: Double, emoji: String, isActive: Boolean, currentCount: Int) {
        viewModelScope.launch {
            val collectionId = activeCollectionId.value
            dao.insert(Article(name = name, price = price, emoji = emoji, isActive = isActive, sortOrder = currentCount, collectionId = collectionId))
            bumpManualPriceToEnd(collectionId)
        }
    }

    fun updateArticle(article: Article, name: String, price: Double, emoji: String, isActive: Boolean) {
        viewModelScope.launch {
            dao.update(article.copy(name = name, price = price, emoji = emoji, isActive = isActive))
        }
    }

    fun deleteArticle(article: Article) {
        viewModelScope.launch {
            dao.delete(article)
        }
    }

    fun reorderArticles(articles: List<Article>) {
        viewModelScope.launch {
            articles.forEachIndexed { index, article ->
                dao.updateSortOrder(article.id, index)
            }
            bumpManualPriceToEnd(activeCollectionId.value)
        }
    }

    private suspend fun bumpManualPriceToEnd(collectionId: Long) {
        val maxSort = dao.getMaxSortOrderExcluding(MANUAL_PRICE_ARTICLE_NAME, collectionId)
        dao.updateManualPriceSortOrder(MANUAL_PRICE_ARTICLE_NAME, collectionId, maxSort + 1)
    }

    fun fetchMerchantInfo() {
        viewModelScope.launch {
            val token = oauthToken.value
            if (token.isBlank()) {
                _merchantInfo.value = null
                return@launch
            }
            try {
                val json = withContext(Dispatchers.IO) {
                    val url = URL("https://api.sumup.com/v0.1/me")
                    val conn = url.openConnection() as HttpURLConnection
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.connectTimeout = 5000
                    conn.readTimeout = 5000
                    try {
                        if (conn.responseCode == 200) {
                            conn.inputStream.bufferedReader().readText()
                        } else null
                    } finally {
                        conn.disconnect()
                    }
                }
                if (json != null) {
                    val obj = JSONObject(json)
                    val merchantCode = obj.optString("merchant_code", "")
                    val personalProfile = obj.optJSONObject("personal_profile")
                    val firstName = personalProfile?.optString("first_name", "") ?: ""
                    val lastName = personalProfile?.optString("last_name", "") ?: ""
                    val name = "$firstName $lastName".trim()
                    _merchantInfo.value = if (name.isNotEmpty()) "$name ($merchantCode)" else merchantCode
                } else {
                    _merchantInfo.value = null
                }
            } catch (_: Exception) {
                _merchantInfo.value = null
            }
        }
    }

    fun saveSumUpConfig(affiliateKey: String, oauthToken: String) {
        viewModelScope.launch {
            settings.saveAffiliateKey(affiliateKey)
            settings.saveOauthToken(oauthToken)
            _snackbarMessage.emit("Gespeichert")
        }
    }

    fun changePin(newPin: String) {
        viewModelScope.launch {
            settings.savePin(newPin)
            _snackbarMessage.emit("PIN geändert")
        }
    }

    fun resetArticlesToDefaults() {
        viewModelScope.launch {
            val collectionId = activeCollectionId.value
            dao.deleteAllByCollection(collectionId)
            AppDatabase.insertDefaultArticles(dao, collectionId)
            _snackbarMessage.emit("Artikel zurückgesetzt")
        }
    }
}
