package ru.fuezl.gymdiary.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import ru.fuezl.gymdiary.core.model.ThemeMode
import ru.fuezl.gymdiary.core.model.UserSettings
import ru.fuezl.gymdiary.data.repository.SettingsRepository
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(private val repository: SettingsRepository) : ViewModel() {
    val settings = repository.observeSettings().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), UserSettings())
    private val messagesChannel = Channel<String>(Channel.BUFFERED)
    val messages = messagesChannel.receiveAsFlow()
    private val exportChannel = Channel<String>(Channel.BUFFERED)
    val exportEvents = exportChannel.receiveAsFlow()

    fun updateTheme(themeMode: ThemeMode) = viewModelScope.launch { repository.updateTheme(themeMode) }

    fun updateRestTimer(enabled: Boolean, seconds: Int) = viewModelScope.launch {
        repository.updateRestTimer(enabled, seconds)
    }

    fun updateHaptics(enabled: Boolean) = viewModelScope.launch { repository.updateHaptics(enabled) }

    fun exportData() = viewModelScope.launch {
        runCatching { repository.exportData() }
            .onSuccess { exportChannel.send(it) }
            .onFailure { messagesChannel.send("Не удалось экспортировать данные") }
    }

    fun importData(json: String) = viewModelScope.launch {
        runCatching { repository.importData(json) }
            .onSuccess { messagesChannel.send("Данные успешно импортированы") }
            .onFailure { messagesChannel.send("Некорректный формат файла") }
    }

    fun clearAllData() = viewModelScope.launch {
        repository.clearAllData()
        messagesChannel.send("Данные очищены")
    }
}
