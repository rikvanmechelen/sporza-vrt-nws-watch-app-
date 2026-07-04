package be.vanmechelen.vrtnws.ui

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import be.vanmechelen.vrtnws.data.NewsRepository
import be.vanmechelen.vrtnws.ui.article.ArticleViewModel
import be.vanmechelen.vrtnws.ui.headlines.HeadlinesViewModel

fun headlinesViewModelFactory(repository: NewsRepository) = viewModelFactory {
    initializer { HeadlinesViewModel(repository) }
}

fun articleViewModelFactory(repository: NewsRepository, id: String, url: String) = viewModelFactory {
    initializer { ArticleViewModel(repository, id, url) }
}
