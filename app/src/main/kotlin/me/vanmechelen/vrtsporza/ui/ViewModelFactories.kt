package me.vanmechelen.vrtsporza.ui

import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import me.vanmechelen.vrtsporza.data.MatchesRepository
import me.vanmechelen.vrtsporza.data.NewsRepository
import me.vanmechelen.vrtsporza.model.NewsSource
import me.vanmechelen.vrtsporza.ui.article.ArticleViewModel
import me.vanmechelen.vrtsporza.ui.headlines.HeadlinesViewModel
import me.vanmechelen.vrtsporza.ui.matches.MatchDetailViewModel
import me.vanmechelen.vrtsporza.ui.matches.MatchesViewModel

fun headlinesViewModelFactory(repository: NewsRepository, source: NewsSource) = viewModelFactory {
    initializer { HeadlinesViewModel(repository, source) }
}

fun articleViewModelFactory(repository: NewsRepository, url: String) = viewModelFactory {
    initializer { ArticleViewModel(repository, url) }
}

fun matchesViewModelFactory(repository: MatchesRepository) = viewModelFactory {
    initializer { MatchesViewModel(repository) }
}

fun matchDetailViewModelFactory(repository: MatchesRepository, url: String) = viewModelFactory {
    initializer { MatchDetailViewModel(repository, url) }
}
