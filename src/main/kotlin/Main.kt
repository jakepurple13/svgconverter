import androidx.compose.animation.Crossfade
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.generator.FileInfo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.File

val outputDirectory = File("~/Desktop/svgconverter/")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FrameWindowScope.App() {
    var svgText by remember { mutableStateOf("") }
    var showPreview by remember { mutableStateOf(false) }
    val listOfConversions = remember { mutableStateListOf<FileInfo>() }
    val filesToConvert = remember { mutableStateListOf<File>() }
    var chosenVectorFile by remember { mutableStateOf<FileInfo?>(null) }

    var dragState by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        window.dropTarget = DropTarget().apply {
            addDropTargetListener(
                object : DropTargetAdapter() {
                    override fun dragEnter(dtde: DropTargetDragEvent?) {
                        super.dragEnter(dtde)
                        dragState = true
                    }

                    override fun drop(event: DropTargetDropEvent) {
                        event.acceptDrop(DnDConstants.ACTION_COPY)
                        val draggedFileName = event.transferable.getTransferData(DataFlavor.javaFileListFlavor)
                        println(draggedFileName)
                        when (draggedFileName) {
                            is List<*> -> {
                                draggedFileName.forEach { file ->
                                    file?.toString()?.let {
                                        if (it.endsWith(".svg")) {
                                            filesToConvert.add(File(it))
                                        }
                                    }
                                }
                            }
                        }
                        event.dropComplete(true)
                        dragState = false
                    }

                    override fun dragExit(dte: DropTargetEvent?) {
                        super.dragExit(dte)
                        dragState = false
                    }
                }
            )
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Svg Converter") },
                actions = {
                    val clipboard = LocalClipboardManager.current
                    IconButton(
                        onClick = { clipboard.setText(AnnotatedString(svgText)) }
                    ) { Icon(Icons.Default.CopyAll, null) }
                }
            )
        },
        bottomBar = {
            BottomAppBar(
                actions = {
                    Button(
                        onClick = { showPreview = !showPreview },
                    ) {
                        Text("Include Preview?")
                        Switch(
                            checked = showPreview,
                            onCheckedChange = { showPreview = it }
                        )
                    }
                },
                floatingActionButton = {
                    LargeFloatingActionButton(
                        onClick = {
                            filesToConvert.forEach { it.copyTo(File(outputDirectory, it.name)) }
                            listOfConversions.clear()
                            Svg2Compose.parseToString(
                                applicationIconPackage = "com",
                                accessorName = "Hello",
                                outputSourceDirectory = outputDirectory,
                                vectorsDirectory = outputDirectory,
                                generatePreview = showPreview
                            )
                                .onEach { println(it.toString()) }
                                .let { listOfConversions.addAll(it) }
                        },
                    ) { Text("Convert") }
                }
            )
        }
    ) { padding ->
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            OutlinedCard(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            ) {
                Crossfade(dragState) { target ->
                    if(target) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text("Drag-N-Drop file(s) here to convert")
                        }
                    } else {
                        LazyColumn(
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            if(filesToConvert.isNotEmpty()) {
                                stickyHeader {
                                    TopAppBar(
                                        title = { Text("To Convert") }
                                    )
                                }
                            }

                            items(filesToConvert) {
                                OutlinedCard(
                                    modifier = Modifier.animateItemPlacement()
                                ) {
                                    ListItem(
                                        headlineContent = { Text(it.name) },
                                        trailingContent = {
                                            IconButton(
                                                onClick = { filesToConvert.remove(it) }
                                            ) { Icon(Icons.Default.Close, null) }
                                        }
                                    )
                                }
                            }

                            if(listOfConversions.isNotEmpty()) {
                                stickyHeader {
                                    TopAppBar(
                                        title = { Text("Converted") }
                                    )
                                }
                            }
                            items(listOfConversions) {
                                OutlinedCard(
                                    onClick = {
                                        chosenVectorFile = it
                                        svgText = it.fileSpec.toString()
                                    }
                                ) {
                                    ListItem(
                                        headlineContent = { Text(it.name) },
                                        trailingContent = if(chosenVectorFile == it) {
                                            {
                                                Icon(Icons.Default.CheckCircle, null)
                                            }
                                        } else null
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
            ) {
                SelectionContainer(
                    modifier = Modifier
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                ) {
                    Text(
                        svgText,
                        modifier = Modifier
                            .padding(16.dp)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

fun main() {
    Runtime.getRuntime().addShutdownHook(Thread { outputDirectory.deleteRecursively() })
    application {
        Window(onCloseRequest = ::exitApplication) {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Color(0xff8bd0f0),
                    onPrimary = Color(0xff003546),
                    primaryContainer = Color(0xff004d64),
                    onPrimaryContainer = Color(0xffbee9ff),
                    inversePrimary = Color(0xff126682),
                    secondary = Color(0xffb4cad6),
                    onSecondary = Color(0xff1f333c),
                    secondaryContainer = Color(0xff354a54),
                    onSecondaryContainer = Color(0xffd0e6f2),
                    tertiary = Color(0xffc6c2ea),
                    onTertiary = Color(0xff2f2d4d),
                    tertiaryContainer = Color(0xff454364),
                    onTertiaryContainer = Color(0xffe3dfff),
                    background = Color(0xff191c1e),
                    onBackground = Color(0xffe1e2e4),
                    surface = Color(0xff191c1e),
                    onSurface = Color(0xffe1e2e4),
                    surfaceVariant = Color(0xff40484c),
                    onSurfaceVariant = Color(0xffc5c7c9),
                    inverseSurface = Color(0xffe1e2e4),
                    inverseOnSurface = Color(0xff2e3133),
                    outline = Color(0xff8a9297),
                )
            ) {
                Surface {
                    App()
                }
            }
        }
    }
}

