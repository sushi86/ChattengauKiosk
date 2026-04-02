package net.maerkl.kassierapp.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
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
            dao.insert(Article(name = name, price = price, emoji = emoji, isActive = isActive, sortOrder = currentCount, collectionId = activeCollectionId.value))
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
