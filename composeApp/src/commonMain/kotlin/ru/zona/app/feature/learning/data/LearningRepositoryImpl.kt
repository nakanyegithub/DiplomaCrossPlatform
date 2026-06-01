package ru.zona.app.feature.learning.data

import io.ktor.client.call.body
import ru.zona.app.core.network.safeApiCall
import ru.zona.app.core.result.Outcome
import ru.zona.app.feature.learning.domain.LearningRepository

class LearningRepositoryImpl(private val api: LearningApi) : LearningRepository {
    override suspend fun catalog(query: String?): Outcome<List<CourseDto>> =
        safeApiCall({ api.catalog(query) }, { it.body() })

    override suspend fun myCourses(): Outcome<List<CourseDto>> =
        safeApiCall({ api.myCourses() }, { it.body() })

    override suspend fun detail(courseId: Long): Outcome<CourseDetailDto> =
        safeApiCall({ api.detail(courseId) }, { it.body() })

    override suspend fun enroll(courseId: Long): Outcome<CourseDto> =
        safeApiCall({ api.enroll(courseId) }, { it.body() })

    override suspend fun exercises(lessonId: Long): Outcome<List<ExerciseDto>> =
        safeApiCall({ api.exercises(lessonId) }, { it.body() })

    override suspend fun submit(exerciseId: Long, answer: String): Outcome<SubmitAnswerResponse> =
        safeApiCall({ api.submit(exerciseId, answer) }, { it.body() })

    override suspend fun createCourse(req: CreateCourseRequest): Outcome<CourseDto> =
        safeApiCall({ api.createCourse(req) }, { it.body() })

    override suspend fun addLesson(courseId: Long, title: String): Outcome<LessonDto> =
        safeApiCall({ api.addLesson(courseId, CreateLessonRequest(title)) }, { it.body() })

    override suspend fun addExercise(lessonId: Long, req: CreateExerciseRequest): Outcome<ExerciseDto> =
        safeApiCall({ api.addExercise(lessonId, req) }, { it.body() })
}
