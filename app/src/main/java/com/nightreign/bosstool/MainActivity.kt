package com.nightreign.bosstool

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nightreign.bosstool.ui.theme.NightreignTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // 初回はサンプルデータを端末へ展開し、データを読み込む
        BossRepository.ensureDataFiles(this)
        setContent {
            NightreignTheme {
                MainScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current

    // データを再読み込みするたびに increment し、依存する remember を更新する
    var dataVersion by remember { mutableIntStateOf(0) }
    val nightBosses = remember(dataVersion) { BossRepository.nightBossChoices() }

    var day1Text by remember { mutableStateOf("") }
    var day1Selected by remember { mutableStateOf<String?>(null) }
    var day2Text by remember { mutableStateOf("") }
    var day2Selected by remember { mutableStateOf<String?>(null) }

    val candidates = remember(day1Selected, day2Selected, dataVersion) {
        BossRepository.candidates(day1Selected, day2Selected)
    }

    var menuOpen by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Nightreign 3日目ボス候補") },
                actions = {
                    IconButton(onClick = {
                        BossRepository.reload(context)
                        dataVersion++
                    }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "再読み込み")
                    }
                    IconButton(onClick = { menuOpen = true }) {
                        Icon(Icons.Filled.MoreVert, contentDescription = "メニュー")
                    }
                    DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                        DropdownMenuItem(
                            text = { Text("ファイルを再読み込み") },
                            onClick = {
                                BossRepository.reload(context)
                                dataVersion++
                                menuOpen = false
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("初期データに戻す") },
                            onClick = {
                                BossRepository.restoreDefaults(context)
                                dataVersion++
                                menuOpen = false
                            },
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "夜ボスを選ぶと3日目のボス候補が表示されます。" +
                    "1日目・2日目の両方を入力すると候補が絞り込まれます。",
                style = MaterialTheme.typography.bodyMedium,
            )

            BossSelectField(
                label = "1日目の夜ボス",
                allBosses = nightBosses,
                text = day1Text,
                onTextChange = { day1Text = it; day1Selected = null },
                onPick = { day1Text = it.name; day1Selected = it.name },
            )

            BossSelectField(
                label = "2日目の夜ボス",
                allBosses = nightBosses,
                text = day2Text,
                onTextChange = { day2Text = it; day2Selected = null },
                onPick = { day2Text = it.name; day2Selected = it.name },
            )

            Button(
                onClick = {
                    day1Text = ""; day1Selected = null
                    day2Text = ""; day2Selected = null
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Icon(Icons.Filled.Clear, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("リセット")
            }

            HorizontalDivider()

            ResultSection(day1Selected, day2Selected, candidates)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun BossSelectField(
    label: String,
    allBosses: List<Boss>,
    text: String,
    onTextChange: (String) -> Unit,
    onPick: (Boss) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    val filtered = remember(text, allBosses) {
        if (text.isBlank()) allBosses else allBosses.filter { bossMatches(it, text) }
    }

    ExposedDropdownMenuBox(
        expanded = expanded && filtered.isNotEmpty(),
        onExpandedChange = { expanded = it },
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = {
                onTextChange(it)
                expanded = true
            },
            label = { Text(label) },
            singleLine = true,
            trailingIcon = {
                if (text.isNotEmpty()) {
                    IconButton(onClick = {
                        onTextChange("")
                        expanded = false
                    }) {
                        Icon(Icons.Filled.Clear, contentDescription = "クリア")
                    }
                } else {
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded)
                }
            },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth(),
        )
        ExposedDropdownMenu(
            expanded = expanded && filtered.isNotEmpty(),
            onDismissRequest = { expanded = false },
        ) {
            filtered.take(50).forEach { boss ->
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(boss.name, style = MaterialTheme.typography.bodyLarge)
                            if (boss.reading != boss.name) {
                                Text(
                                    boss.reading,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                            }
                        }
                    },
                    onClick = {
                        onPick(boss)
                        expanded = false
                    },
                )
            }
        }
    }
}

@Composable
private fun ResultSection(
    day1: String?,
    day2: String?,
    candidates: List<String>,
) {
    Text(
        "3日目のボス候補",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.Bold,
    )
    when {
        day1 == null && day2 == null -> {
            Text(
                "1日目か2日目の夜ボスを選択してください。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        candidates.isEmpty() -> {
            Text(
                "該当する候補が見つかりませんでした。データ（boss_table.txt）を確認してください。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
            )
        }

        else -> {
            Text(
                "${candidates.size} 件",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            candidates.forEach { name ->
                ElevatedCard(modifier = Modifier.fillMaxWidth()) {
                    Text(
                        name,
                        modifier = Modifier.padding(16.dp),
                        style = MaterialTheme.typography.titleMedium,
                    )
                }
            }
        }
    }
}
