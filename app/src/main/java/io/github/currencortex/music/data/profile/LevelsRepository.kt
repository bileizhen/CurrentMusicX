package io.github.currencortex.music.data.profile

import io.github.currencortex.music.core.network.*
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.*

@Serializable data class PointSource(val id: String, val points: Int, val dailyLimit: Int)
@Serializable data class LevelThreshold(val level: Int, val points: Long)
@Serializable data class LevelTrialRules(val level: Int, val plusLevel: Int, val questionCount: Int,
    val passScore: Int, val maxScore: Int)
@Serializable data class LevelRules(val dailyPoints: Int = 0, val maxPoints: Long,
    val timeZone: String, val pointSources: List<PointSource>, val listenSeconds: Int,
    val levels: List<LevelThreshold>, val trial: LevelTrialRules) {
    // dailyPoints is the visit award on current servers, not the sum of daily quotas.
    val dailyMaximum get() = pointSources.sumOf { it.points.toLong() * it.dailyLimit }
    fun next(progress: LevelProgress) = levels.sortedBy { it.level }.firstOrNull { it.level > progress.level }
    fun validate(): LevelRules = apply {
        if (maxPoints <= 0 || listenSeconds <= 0 || levels.isEmpty() ||
            levels.any { it.level <= 0 || it.points < 0 } ||
            pointSources.any { it.points < 0 || it.dailyLimit < 0 } ||
            trial.questionCount !in 1..200 || trial.maxScore <= 0 || trial.passScore !in 0..trial.maxScore)
            throw ApiException(ErrorKind.Parse)
    }
}
@Serializable data class LevelProgress(val level: Int, val points: Long, val today: Map<String, Int> = emptyMap())
@Serializable data class TrialQuestion(val prompt: String, val options: List<String>)
@Serializable data class LevelTrial(val questions: List<TrialQuestion>) {
    fun validAnswers(answers: List<Int>) = answers.size == questions.size &&
        questions.indices.all { answers[it] in questions[it].options.indices }
}
@Serializable data class LevelTrialResult(val score: Int, val passed: Boolean, val level: Int)

interface LevelsApi {
    suspend fun rules(owner: RequestSession): LevelRules
    suspend fun progress(owner: RequestSession): LevelProgress
    suspend fun trial(owner: RequestSession, rules: LevelTrialRules): LevelTrial
    suspend fun submit(owner: RequestSession, trial: LevelTrial, answers: List<Int>): LevelTrialResult
}

class LevelsRepository(private val api: ApiClient) : LevelsApi {
    override suspend fun rules(owner: RequestSession): LevelRules = api.decode<LevelRules>(api.request("GET", "levels/rules",
        expectedSession = RequestSession(owner.server, null), projectOnly = true)).validate()
    override suspend fun progress(owner: RequestSession): LevelProgress = api.decode<LevelProgress>(api.request("GET", "levels/progress",
        authenticated = true, expectedSession = owner, projectOnly = true))
    override suspend fun trial(owner: RequestSession, rules: LevelTrialRules): LevelTrial =
        api.decode<LevelTrial>(api.request("GET", "levels/trial", authenticated = true,
            expectedSession = owner, projectOnly = true)).also {
            if (it.questions.size != rules.questionCount || it.questions.any { q -> q.prompt.isBlank() || q.options.size !in 2..16 })
                throw ApiException(ErrorKind.Parse)
        }
    override suspend fun submit(owner: RequestSession, trial: LevelTrial, answers: List<Int>): LevelTrialResult {
        require(trial.validAnswers(answers)) { "请完成所有题目" }
        return api.decode(api.request("POST", "levels/trial", body = buildJsonObject {
            put("answers", JsonArray(answers.map(::JsonPrimitive)))
        }, authenticated = true, expectedSession = owner, projectOnly = true, retryConnection = false))
    }
}
