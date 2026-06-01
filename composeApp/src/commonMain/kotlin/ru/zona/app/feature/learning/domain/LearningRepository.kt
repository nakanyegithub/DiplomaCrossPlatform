package ru.zona.app.feature.learning.domain

import ru.zona.app.core.result.Outcome
import ru.zona.app.feature.learning.data.CourseDetailDto
import ru.zona.app.feature.learning.data.CourseDto
import ru.zona.app.feature.learning.data.CreateCourseRequest
import ru.zona.app.feature.learning.data.CreateExerciseRequest
import ru.zona.app.feature.learning.data.CreateLessonRequest
import ru.zona.app.feature.learning.data.ExerciseDto
import ru.zona.app.feature.learning.data.LessonDto
import ru.zona.app.feature.learning.data.SubmitAnswerResponse

interface LearningRepository {
    suspend fun catalog(query: String?): Outcome<List<CourseDto>>
    suspend fun myCourses(): Outcome<List<CourseDto>>
    suspend fun detail(courseId: Long): Outcome<CourseDetailDto>
    suspend fun enroll(courseId: Long): Outcome<CourseDto>
    suspend fun exercises(lessonId: Long): Outcome<List<ExerciseDto>>
    suspend fun submit(exerciseId: Long, answer: String): Outcome<SubmitAnswerResponse>
    suspend fun createCourse(req: CreateCourseRequest): Outcome<CourseDto>
    suspend fun addLesson(courseId: Long, title: String): Outcome<LessonDto>
    suspend fun addExercise(lessonId: Long, req: CreateExerciseRequest): Outcome<ExerciseDto>
}
