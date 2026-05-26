package ru.fuezl.gymdiary.domain.usecase

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ValidatorsTest {
    @Test
    fun exerciseNameValidation_returnsRussianErrors() {
        assertEquals("Название не может быть пустым", ExerciseValidator.validateName(""))
        assertEquals("Название не может быть пустым", ExerciseValidator.validateName("   "))
        assertEquals("Название не может быть пустым", ExerciseValidator.validateName("\n\t"))
        assertEquals("Название должно быть не короче 2 символов", ExerciseValidator.validateName("A"))
        assertEquals("Название должно быть не короче 2 символов", ExerciseValidator.validateName(" A "))
        assertNull(ExerciseValidator.validateName("AB"))
        assertNull(ExerciseValidator.validateName("Жим"))
        assertNull(ExerciseValidator.validateName(" Жим "))
    }

    @Test
    fun setValidation_rejectsInvalidValues() {
        assertEquals("Вес должен быть числом", SetValidator.validate(Double.NaN, 5))
        assertEquals("Вес должен быть числом", SetValidator.validate(Double.POSITIVE_INFINITY, 5))
        assertEquals("Вес должен быть числом", SetValidator.validate(Double.NEGATIVE_INFINITY, 5))
        assertEquals("Вес не может быть отрицательным", SetValidator.validate(-1.0, 5))
        assertEquals("Вес не может быть отрицательным", SetValidator.validate(-0.001, 5))
        assertEquals("Количество повторений не может быть отрицательным", SetValidator.validate(10.0, -1))
        assertNull(SetValidator.validate(0.0, 0))
        assertNull(SetValidator.validate(0.001, 0))
        assertNull(SetValidator.validate(10.0, 5))
    }
}
