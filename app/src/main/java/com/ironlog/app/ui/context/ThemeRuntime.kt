package com.ironlog.app.ui.context

import com.ironlog.app.ui.theme.IronLogThemes
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object ThemeRuntime {
    private val _themeName = MutableStateFlow(IronLogThemes.DEFAULT_THEME)
    val themeName: StateFlow<String> = _themeName.asStateFlow()

    fun setTheme(name: String) {
        _themeName.value = name
    }
}
