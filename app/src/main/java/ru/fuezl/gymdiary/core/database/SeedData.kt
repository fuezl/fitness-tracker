package ru.fuezl.gymdiary.core.database

import ru.fuezl.gymdiary.core.model.Equipment
import ru.fuezl.gymdiary.core.model.MuscleGroup

object SeedData {
    fun exercises(now: Long = System.currentTimeMillis()): List<ExerciseEntity> =
        defaultExercises.map { spec ->
            ExerciseEntity(
                name = spec.name,
                muscleGroup = spec.muscleGroup,
                equipment = spec.equipment,
                isCustom = false,
                createdAt = now,
                updatedAt = now
            )
        }

    private val defaultExercises = listOf(
        SeedExercise("Жим штанги лёжа", MuscleGroup.CHEST, Equipment.BARBELL),
        SeedExercise("Жим гантелей лёжа", MuscleGroup.CHEST, Equipment.DUMBBELL),
        SeedExercise("Отжимания", MuscleGroup.CHEST, Equipment.BODYWEIGHT),
        SeedExercise("Подтягивания", MuscleGroup.BACK, Equipment.BODYWEIGHT),
        SeedExercise("Тяга верхнего блока", MuscleGroup.BACK, Equipment.CABLE),
        SeedExercise("Тяга штанги в наклоне", MuscleGroup.BACK, Equipment.BARBELL),
        SeedExercise("Приседания со штангой", MuscleGroup.LEGS, Equipment.BARBELL),
        SeedExercise("Жим ногами", MuscleGroup.LEGS, Equipment.MACHINE),
        SeedExercise("Румынская тяга", MuscleGroup.LEGS, Equipment.BARBELL),
        SeedExercise("Жим штанги стоя", MuscleGroup.SHOULDERS, Equipment.BARBELL),
        SeedExercise("Махи гантелями в стороны", MuscleGroup.SHOULDERS, Equipment.DUMBBELL),
        SeedExercise("Подъем штанги на бицепс", MuscleGroup.BICEPS, Equipment.BARBELL),
        SeedExercise("Подъем гантелей на бицепс", MuscleGroup.BICEPS, Equipment.DUMBBELL),
        SeedExercise("Французский жим", MuscleGroup.TRICEPS, Equipment.BARBELL),
        SeedExercise("Разгибание рук на блоке", MuscleGroup.TRICEPS, Equipment.CABLE),
        SeedExercise("Скручивания", MuscleGroup.ABS, Equipment.BODYWEIGHT),
        SeedExercise("Планка", MuscleGroup.ABS, Equipment.BODYWEIGHT),
        SeedExercise("Беговая дорожка", MuscleGroup.CARDIO, Equipment.CARDIO_MACHINE)
    )
}

private data class SeedExercise(val name: String, val muscleGroup: MuscleGroup, val equipment: Equipment)
