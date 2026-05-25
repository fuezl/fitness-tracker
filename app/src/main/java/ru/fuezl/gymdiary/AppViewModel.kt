package ru.fuezl.gymdiary

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.fuezl.gymdiary.data.repository.ExerciseRepository
import ru.fuezl.gymdiary.data.repository.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class AppViewModel @Inject constructor(
    settingsRepository: SettingsRepository,
    private val exerciseRepository: ExerciseRepository,
) : ViewModel() {
    val settings = settingsRepository.observeSettings().stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ru.fuezl.gymdiary.core.model.UserSettings(),
    )

    init {
        viewModelScope.launch { exerciseRepository.seedDefaultsIfEmpty() }
    }
}
