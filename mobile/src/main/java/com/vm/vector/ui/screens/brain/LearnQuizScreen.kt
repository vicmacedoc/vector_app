package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.vm.core.ui.theme.DeleteRed
import com.vm.core.ui.theme.IconGray
import com.vm.core.ui.theme.NavyDeep
import com.vm.core.ui.theme.OffWhite
import com.vm.core.ui.theme.PriorityLow
import com.vm.core.ui.theme.PriorityMid
import com.vm.core.ui.theme.PureBlack
import com.vm.core.ui.theme.PureWhite
import com.vm.core.ui.theme.SlateGray
import com.vm.core.ui.theme.VectorTheme
import com.vm.vector.data.GeminiLearningClient.QuizQuestion
import com.vm.vector.ui.viewmodel.LearnQuizState
import com.vm.vector.ui.viewmodel.LearnQuizViewModel
import com.vm.vector.ui.viewmodel.LearnQuizViewModelFactory
import com.vm.vector.ui.viewmodel.QuizAnswer
import com.vm.vector.ui.viewmodel.QuizPhase

@Composable
fun LearnQuizScreen(
    nodeId: String,
    onBack: () -> Unit,
    viewModel: LearnQuizViewModel = viewModel(
        key = "learn_quiz_$nodeId",
        factory = LearnQuizViewModelFactory(LocalContext.current, nodeId),
    ),
) {
    val state by viewModel.state.collectAsState()

    VectorTheme {
        Scaffold(
            containerColor = PureWhite,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (state.isReview) "Review" else "Quiz",
                            style = MaterialTheme.typography.labelLarge,
                            color = SlateGray,
                        )
                        Text(
                            text = state.title,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = PureBlack,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.Close, contentDescription = "Close quiz", tint = PureBlack)
                    }
                }

                when (state.phase) {
                    QuizPhase.LOADING -> CenteredMessage {
                        CircularProgressIndicator(color = NavyDeep)
                        Text(
                            "Writing questions…",
                            color = SlateGray,
                            modifier = Modifier.padding(top = 16.dp),
                        )
                    }
                    QuizPhase.ERROR -> CenteredMessage {
                        Text(
                            state.error.orEmpty(),
                            color = PureBlack,
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = viewModel::load,
                            colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
                            modifier = Modifier.padding(top = 16.dp),
                        ) { Text("Try again") }
                    }
                    QuizPhase.QUESTION -> QuestionStep(state = state, viewModel = viewModel)
                    QuizPhase.SUMMARY -> SummaryStep(state = state, viewModel = viewModel, onDone = onBack)
                }
            }
        }
    }
}

@Composable
private fun CenteredMessage(content: @Composable () -> Unit) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

@Composable
private fun QuestionStep(state: LearnQuizState, viewModel: LearnQuizViewModel) {
    val question = state.current ?: return
    val answer = state.currentAnswer
    val answered = answer.score != null
    Column(modifier = Modifier.fillMaxSize()) {
        LinearProgressIndicator(
            progress = { (state.index + 1f) / state.questions.size },
            color = NavyDeep,
            trackColor = OffWhite,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
        )
        Text(
            text = "Question ${state.index + 1} of ${state.questions.size} · " +
                if (question.isMultipleChoice) "Multiple choice" else "Open answer",
            style = MaterialTheme.typography.labelMedium,
            color = SlateGray,
            modifier = Modifier.padding(top = 8.dp),
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 12.dp, bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                question.prompt,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = PureBlack,
            )
            if (question.isMultipleChoice) {
                question.options.forEachIndexed { i, option ->
                    OptionRow(
                        text = option,
                        state = when {
                            !answered -> OptionState.IDLE
                            i == question.correctIndex -> OptionState.CORRECT
                            i == answer.selectedIndex -> OptionState.WRONG
                            else -> OptionState.IDLE
                        },
                        enabled = !answered,
                        onClick = { viewModel.selectOption(i) },
                    )
                }
                if (answered && question.explanation.isNotBlank()) {
                    FeedbackCard(
                        title = if (answer.score == 1f) "Correct" else "Not quite",
                        color = if (answer.score == 1f) PriorityLow else DeleteRed,
                        body = question.explanation,
                    )
                }
            } else {
                OpenAnswer(question = question, answer = answer, viewModel = viewModel)
            }
        }
        Button(
            onClick = viewModel::next,
            enabled = answered,
            colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
        ) {
            Text(if (state.index < state.questions.lastIndex) "Next" else "See results")
        }
    }
}

private enum class OptionState { IDLE, CORRECT, WRONG }

@Composable
private fun OptionRow(text: String, state: OptionState, enabled: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(12.dp)
    val accent = when (state) {
        OptionState.IDLE -> NavyDeep.copy(alpha = 0.35f)
        OptionState.CORRECT -> PriorityLow
        OptionState.WRONG -> DeleteRed
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (state == OptionState.IDLE) PureWhite else accent.copy(alpha = 0.08f))
            .border(if (state == OptionState.IDLE) 1.dp else 2.dp, accent, shape)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(18.dp)
                .border(2.dp, if (state == OptionState.IDLE) NavyDeep else accent, CircleShape)
                .padding(4.dp)
                .background(if (state == OptionState.IDLE) Color.Transparent else accent, CircleShape),
        )
        Text(text, color = PureBlack, modifier = Modifier.padding(start = 12.dp))
    }
}

@Composable
private fun OpenAnswer(question: QuizQuestion, answer: QuizAnswer, viewModel: LearnQuizViewModel) {
    val graded = answer.score != null
    OutlinedTextField(
        value = answer.text,
        onValueChange = viewModel::setOpenText,
        enabled = !graded && !answer.grading,
        placeholder = { Text("Your answer", color = PureBlack.copy(alpha = 0.45f)) },
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
        colors = brainFieldColors(),
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 120.dp),
    )
    if (!graded) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = viewModel::checkOpenAnswer,
                enabled = answer.text.isNotBlank() && !answer.grading,
                colors = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite),
            ) {
                if (answer.grading) {
                    CircularProgressIndicator(color = PureWhite, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    Text("Checking…", modifier = Modifier.padding(start = 8.dp))
                } else {
                    Text("Check")
                }
            }
            Spacer(modifier = Modifier.weight(1f))
            TextButton(onClick = viewModel::skipQuestion, enabled = !answer.grading) {
                Text("Skip", color = SlateGray)
            }
        }
        answer.gradeError?.let { Text(it, color = DeleteRed, style = MaterialTheme.typography.bodySmall) }
    } else {
        val score = answer.score ?: 0f
        val (title, color) = when {
            answer.verdict == "skipped" -> "Skipped" to SlateGray
            score >= 0.8f -> "Correct · ${(score * 100).toInt()}%" to PriorityLow
            score >= 0.4f -> "Partly right · ${(score * 100).toInt()}%" to PriorityMid
            else -> "Not quite · ${(score * 100).toInt()}%" to DeleteRed
        }
        FeedbackCard(title = title, color = color, body = answer.feedback.ifBlank { question.explanation })
        if (question.referenceAnswer.isNotBlank()) {
            FeedbackCard(title = "Reference answer", color = NavyDeep, body = question.referenceAnswer)
        }
    }
}

@Composable
private fun FeedbackCard(title: String, color: Color, body: String) {
    val shape = RoundedCornerShape(12.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(color.copy(alpha = 0.06f))
            .border(1.dp, color.copy(alpha = 0.4f), shape)
            .padding(14.dp),
    ) {
        Text(title, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.labelLarge)
        if (body.isNotBlank()) {
            Text(body, color = PureBlack, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun SummaryStep(state: LearnQuizState, viewModel: LearnQuizViewModel, onDone: () -> Unit) {
    val percent = (state.score * 100).toInt()
    val passed = viewModel.passed()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp, bottom = 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            "$percent%",
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold,
            color = if (passed) PriorityLow else NavyDeep,
        )
        Text(
            text = if (passed) "Above your ${viewModel.settings.passPercent}% pass mark" else "Below your ${viewModel.settings.passPercent}% pass mark",
            color = SlateGray,
            modifier = Modifier.padding(top = 4.dp),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            state.questions.forEachIndexed { i, question ->
                val score = state.answers[i]?.score ?: 0f
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .border(1.dp, IconGray.copy(alpha = 0.35f), RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .background(
                                when {
                                    score >= 0.8f -> PriorityLow
                                    score >= 0.4f -> PriorityMid
                                    else -> DeleteRed
                                },
                                CircleShape,
                            ),
                    )
                    Text(
                        question.prompt,
                        color = PureBlack,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = 10.dp),
                    )
                    Text(
                        "${(score * 100).toInt()}%",
                        color = SlateGray,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
        }

        val primary = ButtonDefaults.buttonColors(containerColor = NavyDeep, contentColor = PureWhite)
        val outcome = state.outcome
        if (state.isReview && outcome == null) {
            val days = viewModel.nextIntervalDays()
            val solidLabel = if (days != null) "Still solid (next review in $days days)" else "Still solid (mastered)"
            Text(
                "Suggested: ${if (passed) "still solid" else "needs more"}",
                style = MaterialTheme.typography.labelLarge,
                color = SlateGray,
                modifier = Modifier.padding(top = 24.dp),
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (passed) {
                    Button(onClick = { viewModel.completeReview(true) }, colors = primary, modifier = Modifier.fillMaxWidth()) {
                        Text(solidLabel)
                    }
                    OutlinedButton(onClick = { viewModel.completeReview(false) }, modifier = Modifier.fillMaxWidth()) {
                        Text("Needs more", color = NavyDeep)
                    }
                } else {
                    Button(onClick = { viewModel.completeReview(false) }, colors = primary, modifier = Modifier.fillMaxWidth()) {
                        Text("Needs more")
                    }
                    OutlinedButton(onClick = { viewModel.completeReview(true) }, modifier = Modifier.fillMaxWidth()) {
                        Text(solidLabel, color = NavyDeep)
                    }
                }
            }
        } else {
            outcome?.let {
                Text(it, color = SlateGray, modifier = Modifier.padding(top = 24.dp))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(onClick = viewModel::load, modifier = Modifier.weight(1f)) {
                    Text("New quiz", color = NavyDeep)
                }
                Button(onClick = onDone, colors = primary, modifier = Modifier.weight(1f)) {
                    Text("Done")
                }
            }
        }
    }
}
