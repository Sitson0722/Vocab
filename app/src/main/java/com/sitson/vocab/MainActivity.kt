package com.sitson.vocab

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { VocabApp() }
    }
}

@Composable
fun VocabApp() {
    MaterialTheme {
        Surface(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                Text("Today", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
                Text("Build understanding and active use separately.")
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ProgressCard("Understand", "0 / 6", Modifier.weight(1f))
                    ProgressCard("Use", "0 / 4", Modifier.weight(1f))
                }
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Next review", style = MaterialTheme.typography.titleLarge)
                        Text("The medicine produced a subtle improvement in his condition.")
                        Text("Choose the meaning that best fits this context.")
                    }
                }
                Button(onClick = {}, modifier = Modifier.fillMaxWidth()) { Text("Start learning") }
                Text("Works offline with cached learning material.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun ProgressCard(label: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier) {
        Column(Modifier.padding(16.dp)) {
            Text(label, style = MaterialTheme.typography.labelLarge)
            Text(value, style = MaterialTheme.typography.headlineSmall)
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun AppPreview() = VocabApp()
