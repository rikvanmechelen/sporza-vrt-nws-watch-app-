package be.vanmechelen.vrtnws.ui

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import be.vanmechelen.vrtnws.data.NewsRepository
import be.vanmechelen.vrtnws.model.NewsSource
import be.vanmechelen.vrtnws.ui.article.ArticleViewModel
import be.vanmechelen.vrtnws.ui.headlines.HeadlinesViewModel

fun headlinesViewModelFactory(repository: NewsRepository, source: NewsSource) = viewModelFactory {
    initializer { HeadlinesViewModel(repository, source) }
}

fun articleViewModelFactory(repository: NewsRepository, url: String) = viewModelFactory {
    initializer { ArticleViewModel(repository, url) }
}
