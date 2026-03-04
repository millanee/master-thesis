package com.millane.thesis.application.study

enum class StudyGroup { A, B, C }

enum class InterventionType {
    GOAL_ADVANCEMENT,
    SELF_TRACKING,
    DESIGN_FRICTION
}

/**
 * 3x3 Latin Square (balanced for position):
 * A: Goal -> Self -> Friction
 * B: Self -> Friction -> Goal
 * C: Friction -> Goal -> Self
 */
val LatinSquare: Map<StudyGroup, List<InterventionType>> = mapOf(
    StudyGroup.A to listOf(
        InterventionType.GOAL_ADVANCEMENT,
        InterventionType.SELF_TRACKING,
        InterventionType.DESIGN_FRICTION
    ),
    StudyGroup.B to listOf(
        InterventionType.SELF_TRACKING,
        InterventionType.DESIGN_FRICTION,
        InterventionType.GOAL_ADVANCEMENT
    ),
    StudyGroup.C to listOf(
        InterventionType.DESIGN_FRICTION,
        InterventionType.GOAL_ADVANCEMENT,
        InterventionType.SELF_TRACKING
    )
)