package ru.fuezl.gymdiary

import android.app.Application
import android.content.Context
import androidx.test.runner.AndroidJUnitRunner

class HiltTestRunner : AndroidJUnitRunner() {
    override fun newApplication(cl: ClassLoader?, className: String?, context: Context?): Application =
        super.newApplication(cl, GymDiaryApplication::class.java.name, context)
}
