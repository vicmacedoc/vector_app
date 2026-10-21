package com.vm.vector.ui.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.vm.core.models.LearningTileStatus
import com.vm.vector.VectorApplication
import com.vm.vector.data.BrainFileStore
import com.vm.vector.data.BrainRepository
import com.vm.vector.data.GeminiLearningClient
import com.vm.vector.data.GeminiLearningClient.QuizQuestion
import com.vm.vector.data.LearningContext
import com.vm.vector.data.LearningContextBuilder
import com.vm.vector.data.LearningSettings
import com.vm.vector.data.PreferenceManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class QuizPhase { LOADING, ERROR, QUESTION, SUMMARY }

data class QuizAnswer(
    val selectedIndex: Int? = null,
    val text: String = "",
    /** 0..1 once graded; null while unanswered or grading. */
    val score: Float? = null,
    val verdict: String = "",
    val feedback: String = "",
    val grading: Boolean = false,
    val gradeError: String? = null,
)

data class LearnQuizState(
    val phase: QuizPhase = QuizPhase.LOADING,
    val error: String? = null,
    val title: String = "",
    val status: LearningTileStatus? = null,
    val reviewStep: Int = 0,
    val questions: List<QuizQuestion> = emptyList(),
    val index: Int = 0,
    val answers: Map<Int, QuizAnswer> = emptyMap(),
    /** Set once the score or review outcome is stored. */
    val outcome: String? = null,
) {
    val current: QuizQuestion? get() = questions.getOrNull(index)
    val currentAnswer: QuizAnswer get() = answers[index] ?: QuizAnswer()
    val score: Float
        get() = if (questions.isEmpty()) 0f else questions.indices.sumOf { (answers[it]?.score ?: 0f).toDouble() }.toFloat() / questions.size
    val isReview: Boolean get() = status == LearningTileStatus.TO_REVIEW
}

class LearnQuizViewModel(
    application: Application,
    private val repository: BrainRepository,
    private val preferences: PreferenceManager,
    private val gemini: GeminiLearningClient,
    private val contextBuilder: LearningContextBuilder,
    private val nodeId: String,
) : AndroidViewModel(application) {

    private val _state = MutableStateFlow(LearnQuizState())
    val state: StateFlow<LearnQuizState> = _state.asStateFlow()

    var settings: LearningSettings = LearningSettings()
        private set
    private var learning: LearningContext? = null

    init {
        load()
    }

    fun load() {
        _state.value = LearnQuizState(phase = QuizPhase.LOADING)
        viewModelScope.launch {
            settings = preferences.learningSettings.first()
            val tile = repository.getLearnTile(nodeId)
            val result = runCatching {
                val built = learning ?: contextBuilder.build(nodeId, includePdfs = true).also { learning = it }
                _state.update { it.copy(title = built.title) }
                gemini.generateQuiz(built, settings.quizSize).getOrThrow()
            }
            result
                .onSuccess { questions ->
                    _state.update {
                        it.copy(
                            phase = QuizPhase.QUESTION,
                            status = tile?.status,
                            reviewStep = tile?.reviewStep ?: 0,
                            questions = questions,
                        )
                    }
                }
                .onFailure { e ->
                    _state.update {
                        it.copy(phase = QuizPhase.ERROR, error = e.message ?: "Could not create the quiz")
                    }
                }
        }
    }

    fun selectOption(optionIndex: Int) {
        val state = _state.value
        val question = state.current ?: return
        if (state.currentAnswer.score != null) return
        val correct = optionIndex == question.correctIndex
        setAnswer(
            QuizAnswer(
                selectedIndex = optionIndex,
                score = if (correct) 1f else 0f,
                verdict = if (correct) "correct" else "incorrect",
            ),
        )
    }

    fun setOpenText(text: String) {
        val answer = _state.value.currentAnswer
        if (answer.score != null || answer.grading) return
        setAnswer(answer.copy(text = text, gradeError = null))
    }

    fun checkOpenAnswer() {
        val state = _state.value
        val question = state.current ?: return
        val answer = state.currentAnswer
        val built = learning ?: return
        if (answer.text.isBlank() || answer.grading || answer.score != null) return
        val index = state.index
        setAnswer(answer.copy(grading = true, gradeError = null))
        viewModelScope.launch {
            gemini.gradeOpenAnswer(question.prompt, question.referenceAnswer, answer.text.trim(), built)
                .onSuccess { grade ->
                    setAnswer(
                        answer.copy(score = grade.score, verdict = grade.verdict, feedback = grade.feedback, grading = false),
                        index,
                    )
                }
                .onFailure { e ->
                    setAnswer(answer.copy(grading = false, gradeError = e.message ?: "Could not grade the answer"), index)
                }
        }
    }

    /** Skips grading for an open question (counts as 0). */
    fun skipQuestion() {
        val answer = _state.value.currentAnswer
        if (answer.score != null || answer.grading) return
        setAnswer(answer.copy(score = 0f, verdict = "skipped", gradeError = null))
    }

    fun next() {
        val state = _state.value
        if (state.currentAnswer.score == null) return
        if (state.index < state.questions.lastIndex) {
            _state.value = state.copy(index = state.index + 1)
        } else {
            _state.value = state.copy(phase = QuizPhase.SUMMARY)
            if (!state.isReview) saveScore()
        }
    }

    fun passed(): Boolean = _state.value.score * 100f >= settings.passPercent

    /** Days until the next review if this review counts as solid; null means mastered. */
    fun nextIntervalDays(): Int? = settings.intervalDays(_state.value.reviewStep + 1)

    fun completeReview(solid: Boolean) {
        val score = _state.value.score
        viewModelScope.launch {
            repository.completeReview(nodeId, solid, score, settings)
            _state.update {
                it.copy(
                    outcome = when {
                        !solid -> "Back to In Progress · the review schedule restarts"
                        else -> nextIntervalDays()?.let { days -> "Next review in $days days" } ?: "Mastered · no more reviews"
                    },
                )
            }
        }
    }

    private fun saveScore() {
        val score = _state.value.score
        viewModelScope.launch {
            repository.saveQuizScore(nodeId, score)
            _state.update { it.copy(outcome = "Score saved") }
        }
    }

    private fun setAnswer(answer: QuizAnswer, index: Int = _state.value.index) {
        _state.update { it.copy(answers = it.answers + (index to answer)) }
    }
}

class LearnQuizViewModelFactory(
    private val context: android.content.Context,
    private val nodeId: String,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        val app = context.applicationContext as VectorApplication
        val fileStore = BrainFileStore(app, app.database)
        val repo = BrainRepository(app.database, fileStore)
        return LearnQuizViewModel(
            application = app,
            repository = repo,
            preferences = PreferenceManager(app),
            gemini = GeminiLearningClient(app),
            contextBuilder = LearningContextBuilder(repo),
            nodeId = nodeId,
        ) as T
    }
}
