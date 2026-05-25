package ru.fuezl.gymdiary.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {
    @Test
    fun exerciseNameValidation_returnsRussianErrors() {
        assertEquals("Название не может быть пустым", ExerciseValidator.validateName(""))
        assertEquals("Название не может быть пустым", ExerciseValidator.validateName("   "))
        assertEquals("Название должно быть не короче 2 символов", ExerciseValidator.validateName("A"))
        assertEquals("Название должно быть не короче 2 символов", ExerciseValidator.validateName(" A "))
        assertNull(ExerciseValidator.validateName("AB"))
        assertNull(ExerciseValidator.validateName("Жим"))
    }

    @Test
    fun setValidation_rejectsInvalidValues() {
        assertEquals("Вес не может быть отрицательным", SetValidator.validate(-1.0, 5, null))
        assertEquals("Количество повторений не может быть отрицательным", SetValidator.validate(10.0, -1, null))
        assertEquals("RPE должен быть от 1 до 10", SetValidator.validate(10.0, 5, 0.9))
        assertEquals("RPE должен быть от 1 до 10", SetValidator.validate(10.0, 5, 11.0))
        assertNull(SetValidator.validate(0.0, 0, null))
        assertNull(SetValidator.validate(10.0, 5, 1.0))
        assertNull(SetValidator.validate(10.0, 5, 8.5))
        assertNull(SetValidator.validate(10.0, 5, 10.0))
    }
}
