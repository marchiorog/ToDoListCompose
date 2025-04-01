@file:OptIn(ExperimentalMaterial3Api::class)

package br.edu.satc.todolistcompose.ui.screens

import android.content.Context
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.room.*
import kotlinx.coroutines.launch

@Entity(tableName = "tasks")
data class Task(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    @ColumnInfo(name = "title") val title: String,
    @ColumnInfo(name = "is_completed") val isCompleted: Boolean = false
)

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks ORDER BY id DESC")
    suspend fun getAllTasks(): List<Task>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: Task)
}

// Banco de dados usando Room
@Database(entities = [Task::class], version = 1)
abstract class TaskDatabase : RoomDatabase() {
    abstract fun taskDao(): TaskDao

    companion object {
        @Volatile
        private var INSTANCE: TaskDatabase? = null

        fun getDatabase(context: Context): TaskDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    TaskDatabase::class.java,
                    "task_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}


@Preview(showBackground = true)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val db = remember { TaskDatabase.getDatabase(context) }
    val taskDao = remember { db.taskDao() }

    var showBottomSheet by remember { mutableStateOf(false) }
    val tasks = remember { mutableStateListOf<Task>() }
    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior(rememberTopAppBarState())

    // Carrega as tarefas quando a tela é iniciada
    LaunchedEffect(Unit) {
        tasks.clear()
        tasks.addAll(taskDao.getAllTasks())
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    titleContentColor = MaterialTheme.colorScheme.primary
                ),
                title = { Text(text = "ToDoList UniSATC") },
                actions = {
                    IconButton(onClick = { /* Configurações futuras */ }) {
                        Icon(Icons.Rounded.Settings, contentDescription = "")
                    }
                },
                scrollBehavior = scrollBehavior
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                text = { Text("Nova tarefa") },
                icon = { Icon(Icons.Filled.Add, contentDescription = "") },
                onClick = { showBottomSheet = true }
            )
        }
    ) { innerPadding ->
        HomeContent(innerPadding, tasks)
        NewTask(showBottomSheet, taskDao) { newTask ->
            if (newTask != null) tasks.add(0, newTask)
            showBottomSheet = false
        }
    }
}

@Composable
fun HomeContent(innerPadding: PaddingValues, tasks: List<Task>) {
    Column(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .padding(top = innerPadding.calculateTopPadding())
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        tasks.forEach { task ->
            TaskCard(task)
        }
    }
}

@Composable
fun TaskCard(task: Task) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(4.dp),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        Column(
            modifier = Modifier.padding(8.dp)
        ) {
            Text(text = task.title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = if (task.isCompleted) "Concluída" else "Pendente",
                style = MaterialTheme.typography.bodySmall,
                color = if (task.isCompleted) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
            )
        }
    }
}

// ---------- Modal para Adicionar Nova Tarefa ----------

@Composable
fun NewTask(showBottomSheet: Boolean, taskDao: TaskDao, onComplete: (Task?) -> Unit) {
    val sheetState = rememberModalBottomSheetState()
    val scope = rememberCoroutineScope()
    var taskTitle by remember { mutableStateOf("") }

    if (showBottomSheet) {
        ModalBottomSheet(
            onDismissRequest = { onComplete(null) },
            sheetState = sheetState
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                OutlinedTextField(
                    value = taskTitle,
                    onValueChange = { taskTitle = it },
                    label = { Text(text = "Título da tarefa") }
                )
                Button(
                    modifier = Modifier.padding(top = 4.dp),
                    onClick = {
                        scope.launch {
                            if (taskTitle.isNotEmpty()) {
                                val newTask = Task(title = taskTitle, isCompleted = false)
                                taskDao.insertTask(newTask)
                                onComplete(newTask)
                            } else {
                                onComplete(null)
                            }
                        }
                        scope.launch { sheetState.hide() }.invokeOnCompletion {
                            if (!sheetState.isVisible) {
                                onComplete(null)
                            }
                        }
                    }
                ) {
                    Text("Salvar")
                }
            }
        }
    }
}
