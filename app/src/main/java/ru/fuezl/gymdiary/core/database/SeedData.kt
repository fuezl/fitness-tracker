package ru.fuezl.gymdiary.core.database

import ru.fuezl.gymdiary.core.model.Equipment
import ru.fuezl.gymdiary.core.model.MuscleGroup

object SeedData {
    fun exercises(now: Long = System.currentTimeMillis()): List<ExerciseEntity> = listOf(
        ExerciseEntity(
            name = "Жим штанги лёжа",
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BARBELL,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Жим гантелей лёжа",
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.DUMBBELL,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Отжимания",
            muscleGroup = MuscleGroup.CHEST,
            equipment = Equipment.BODYWEIGHT,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Подтягивания",
            muscleGroup = MuscleGroup.BACK,
            equipment = Equipment.BODYWEIGHT,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Тяга верхнего блока",
            muscleGroup = MuscleGroup.BACK,
            equipment = Equipment.CABLE,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Тяга штанги в наклоне",
            muscleGroup = MuscleGroup.BACK,
            equipment = Equipment.BARBELL,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Приседания со штангой",
            muscleGroup = MuscleGroup.LEGS,
            equipment = Equipment.BARBELL,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(name = "Жим ногами", muscleGroup = MuscleGroup.LEGS, equipment = Equipment.MACHINE, isCustom = false, createdAt = now, updatedAt = now),
        ExerciseEntity(
            name = "Румынская тяга",
            muscleGroup = MuscleGroup.LEGS,
            equipment = Equipment.BARBELL,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Жим штанги стоя",
            muscleGroup = MuscleGroup.SHOULDERS,
            equipment = Equipment.BARBELL,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Махи гантелями в стороны",
            muscleGroup = MuscleGroup.SHOULDERS,
            equipment = Equipment.DUMBBELL,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Подъем штанги на бицепс",
            muscleGroup = MuscleGroup.BICEPS,
            equipment = Equipment.BARBELL,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Подъем гантелей на бицепс",
            muscleGroup = MuscleGroup.BICEPS,
            equipment = Equipment.DUMBBELL,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Французский жим",
            muscleGroup = MuscleGroup.TRICEPS,
            equipment = Equipment.BARBELL,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Разгибание рук на блоке",
            muscleGroup = MuscleGroup.TRICEPS,
            equipment = Equipment.CABLE,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(
            name = "Скручивания",
            muscleGroup = MuscleGroup.ABS,
            equipment = Equipment.BODYWEIGHT,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        ),
        ExerciseEntity(name = "Планка", muscleGroup = MuscleGroup.ABS, equipment = Equipment.BODYWEIGHT, isCustom = false, createdAt = now, updatedAt = now),
        ExerciseEntity(
            name = "Беговая дорожка",
            muscleGroup = MuscleGroup.CARDIO,
            equipment = Equipment.CARDIO_MACHINE,
            isCustom = false,
            createdAt = now,
            updatedAt = now
        )
    )
}
