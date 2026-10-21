package com.vm.vector.ui.screens.brain

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.vm.core.models.BrainSection
import com.vm.core.ui.theme.PureBlack
import com.vm.vector.data.BrainSearchResult

@Composable
fun SearchResultsPanel(
    results: BrainSearchResult,
    modifier: Modifier = Modifier,
) {
    val memories = results.nodes.filter { it.section == BrainSection.MEMORY }
    val learnTiles = results.nodes.filter { it.section == BrainSection.MODEL }
    LazyColumn(modifier = modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        if (results.notes.isNotEmpty()) {
            item { SectionHeader("Notes") }
            items(results.notes, key = { it.id }) { note ->
                Text(
                    text = note.title.ifBlank { note.body.take(40) },
                    style = MaterialTheme.typography.bodyLarge,
                    color = PureBlack,
                    modifier = Modifier.padding(vertical = 6.dp),
                )
            }
        }
        if (learnTiles.isNotEmpty()) {
            item { SectionHeader("Learn tiles") }
            items(learnTiles, key = { it.id }) { node ->
                Text(node.name, color = PureBlack, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        if (results.tasks.isNotEmpty()) {
            item { SectionHeader("Tasks") }
            items(results.tasks, key = { it.id }) { task ->
                Text(task.title, color = PureBlack, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
        if (memories.isNotEmpty()) {
            item { SectionHeader("Memories") }
            items(memories, key = { it.id }) { node ->
                Text(node.name, color = PureBlack, modifier = Modifier.padding(vertical = 6.dp))
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = PureBlack,
        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp),
    )
}
