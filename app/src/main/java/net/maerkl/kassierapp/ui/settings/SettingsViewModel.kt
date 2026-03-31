package net.maerkl.kassierapp.ui.settings

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import net.maerkl.kassierapp.KassierApplication
import net.maerkl.kassierapp.data.local.Article

class SettingsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as KassierApplication
    private val dao = app.database.articleDao()
    private val settings = app.settingsDataStore

    val allArticles = dao.getAllArticles()

    val affiliateKey = settings.affiliateKey.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val oauthToken = settings.oauthToken.stateIn(viewModelScope, SharingStarted.Eagerly, "")
    val pin = settings.pin.stateIn(viewModelScope, SharingStarted.Eagerly, "0000")

    private val _snackbarMessage = MutableSharedFlow<String>()
    val snackbarMessage = _snackbarMessage.asSharedFlow()

    fun addArticle(name: String, price: Double, emoji: String, isActive: Boolean, currentCount: Int) {
        viewModelScope.launch {
            dao.insert(Article(name = name, price = price, emoji = emoji, isActive = isActive, sortOrder = currentCount))
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
}
