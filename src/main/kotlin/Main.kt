import androidx.compose.animation.Crossfade
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CopyAll
import androidx.compose.material.icons.generator.FileInfo
import androidx.compose.material.icons.generator.Svg2Compose
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.AwtWindow
import androidx.compose.ui.window.FrameWindowScope
import androidx.compose.ui.window.application
import com.dokar.sonner.ToastType
import com.dokar.sonner.Toaster
import com.dokar.sonner.rememberToasterState
import com.wakaztahir.codeeditor.model.CodeLang
import com.wakaztahir.codeeditor.prettify.PrettifyParser
import com.wakaztahir.codeeditor.theme.CodeThemeType
import com.wakaztahir.codeeditor.utils.parseCodeAsAnnotatedString
import io.kamel.image.KamelImage
import io.kamel.image.asyncPainterResource
import java.awt.FileDialog
import java.awt.Frame
import java.awt.datatransfer.DataFlavor
import java.awt.dnd.*
import java.io.File

// Makes use of a modified version of https://github.com/DevSrSouza/svg-to-compose to convert svg/drawable to compose
// The modification are so that the only files that are dealt with are reading in, no folders containing them,
// no output of files, just file to string.
// And https://github.com/Qawaz/compose-code-editor to show the kotlin code beautifully

val outputDirectory = File(System.getProperty("user.home") + "/Desktop/svgconverter/")

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun FrameWindowScope.App() {
    val toaster = rememberToasterState()
    var showPreview by remember { mutableStateOf(true) }
    val listOfConversions = remember { mutableStateListOf<FileInfo>() }
    val filesToConvert = remember { mutableStateListOf<File>() }
    var chosenVectorFile by remember { mutableStateOf<FileInfo?>(null) }
    var language by remember { mutableStateOf(CodeLang.Kotlin) }
    val svgText by remember {
        derivedStateOf {
            when (language) {
                CodeLang.Kotlin -> chosenVectorFile?.fileSpec?.toString()
                CodeLang.Swift -> chosenVectorFile?.swiftFileSpec?.toString()
                else -> chosenVectorFile?.file?.readText()
            } ?: ""
        }
    }

    var dragState by remember { mutableStateOf(false) }

    var upload by remember { mutableStateOf(false) }
    if (upload) {
        FileDialog(
            FileDialogMode.Load,
            block = {
                setFilenameFilter { _, name -> name.endsWith(".svg") || name.endsWith(".xml") }
                isMultipleMode = true
            }
        ) { _, files ->
            if (!files.isNullOrEmpty()) {
                filesToConvert.addAll(files)
            }
            upload = false
        }
    }

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
                                        if (it.endsWith(".svg") || it.endsWith(".xml")) {
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
        bottomBar = {
            BottomAppBar(
                actions = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OutlinedButton(
                            onClick = { showPreview = !showPreview },
                        ) {
                            Text("Include Preview?")
                            Spacer(Modifier.width(8.dp))
                            Switch(
                                checked = showPreview,
                                onCheckedChange = { showPreview = it }
                            )
                        }

                        Button(
                            onClick = { upload = true }
                        ) { Text("Upload Files") }
                    }
                },
                floatingActionButton = {
                    LargeFloatingActionButton(
                        onClick = {
                            val current = System.currentTimeMillis()
                            listOfConversions.clear()
                            //Use this for when we want them all in a folder
                            /*outputDirectory.deleteRecursively()
                            filesToConvert.forEach {
                                runCatching { it.copyTo(File(outputDirectory, it.name)) }
                                    .onFailure { it.printStackTrace() }
                            }

                            Svg2Compose.parseToString(
                                accessorName = "Hello",
                                outputSourceDirectory = outputDirectory,
                                vectorsDirectory = outputDirectory,
                                generatePreview = showPreview
                            )
                            */

                            Svg2Compose.parseToString(
                                accessorName = "Hello",
                                fileList = filesToConvert,
                                generatePreview = showPreview
                            ).let(listOfConversions::addAll)
                            toaster.show(
                                "Took ${System.currentTimeMillis() - current}ms",
                                type = ToastType.Info
                            )
                        }
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
                    if (target) {
                        Box(
                            contentAlignment = Alignment.Center,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Text("Drag-N-Drop file(s) here to convert")
                        }
                    } else {
                        Crossfade(
                            filesToConvert.isEmpty() && listOfConversions.isEmpty()
                        ) { target2 ->
                            if (target2) {
                                if (filesToConvert.isEmpty() && listOfConversions.isEmpty()) {
                                    Box(
                                        contentAlignment = Alignment.Center,
                                        modifier = Modifier.fillMaxSize()
                                    ) {
                                        Text("Drag and Drop SVG/Drawable file(s) here")
                                    }
                                }
                            } else {
                                LazyColumn(
                                    verticalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier.fillMaxHeight()
                                ) {
                                    if (filesToConvert.isNotEmpty()) {
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
                                                leadingContent = {
                                                    KamelImage(
                                                        resource = asyncPainterResource(data = it),
                                                        contentDescription = it.name,
                                                        onLoading = { progress -> CircularProgressIndicator({ progress }) },
                                                        modifier = Modifier.size(50.dp)
                                                    )
                                                },
                                                trailingContent = {
                                                    IconButton(
                                                        onClick = { filesToConvert.remove(it) }
                                                    ) { Icon(Icons.Default.Close, null) }
                                                }
                                            )
                                        }
                                    }

                                    if (listOfConversions.isNotEmpty()) {
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
                                                //svgText = it.fileSpec.toString()
                                            }
                                        ) {
                                            ListItem(
                                                headlineContent = { Text(it.name + ".kt") },
                                                leadingContent = it.file?.let { file ->
                                                    {
                                                        KamelImage(
                                                            resource = asyncPainterResource(data = file),
                                                            contentDescription = it.name,
                                                            onLoading = { progress -> CircularProgressIndicator({ progress }) },
                                                            modifier = Modifier.size(50.dp)
                                                        )
                                                    }
                                                },
                                                trailingContent = it.takeIf { it == chosenVectorFile }?.let {
                                                    {
                                                        Icon(Icons.Default.CheckCircle, null)
                                                    }
                                                }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            val verticalScroll = rememberScrollState()
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .padding(end = 4.dp)
            ) {
                Column(
                    modifier = Modifier.matchParentSize()
                ) {
                    val clipboard = LocalClipboardManager.current
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        var showDropDown by remember { mutableStateOf(false) }
                        ExposedDropdownMenuBox(
                            showDropDown,
                            onExpandedChange = { showDropDown = it },
                            modifier = Modifier.padding(horizontal = 4.dp)
                        ) {
                            ExposedDropdownMenu(
                                showDropDown,
                                onDismissRequest = { showDropDown = false }
                            ) {
                                allowedLanguages.forEach {
                                    DropdownMenuItem(
                                        text = { Text(it.name) },
                                        onClick = {
                                            language = it
                                            showDropDown = false
                                        }
                                    )
                                }
                            }
                            OutlinedTextField(
                                language.name,
                                onValueChange = {},
                                readOnly = true,
                                singleLine = true,
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = showDropDown) },
                                modifier = Modifier.menuAnchor()
                            )
                        }

                        IconButton(
                            onClick = { clipboard.setText(AnnotatedString(svgText)) },
                            enabled = svgText.isNotEmpty(),
                        ) { Icon(Icons.Default.CopyAll, null) }
                    }

                    HorizontalDivider()

                    SelectionContainer(
                        modifier = Modifier
                            .fillMaxHeight()
                            .verticalScroll(verticalScroll),
                    ) {
                        val parser = remember { PrettifyParser() }
                        val themeState by remember { mutableStateOf(CodeThemeType.Monokai) }
                        val theme = remember(themeState) { themeState.theme }
                        val parsedCode = remember(svgText, language) {
                            runCatching {
                                parseCodeAsAnnotatedString(
                                    parser = parser,
                                    theme = theme,
                                    lang = language,
                                    code = svgText
                                )
                            }.fold(
                                onSuccess = { it },
                                onFailure = {
                                    it.printStackTrace()
                                    AnnotatedString(svgText)
                                }
                            )
                        }
                        Text(
                            parsedCode,
                            modifier = Modifier
                                .padding(16.dp)
                                .fillMaxHeight(),
                        )
                    }
                }
                VerticalScrollbar(
                    rememberScrollbarAdapter(verticalScroll),
                    style = LocalScrollbarStyle.current.copy(
                        hoverColor = MaterialTheme.colorScheme.primary,
                        unhoverColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .padding(end = 2.dp, top = 40.dp)
                        .padding(vertical = 16.dp)
                        .fillMaxHeight()
                        .align(Alignment.CenterEnd)
                )
            }
        }
    }
    Toaster(
        state = toaster,
        darkTheme = true,
        richColors = true
    )
}

enum class FileDialogMode(internal val id: Int) {
    Load(FileDialog.LOAD),

    @Suppress("unused")
    Save(FileDialog.SAVE)
}

@Composable
private fun FileDialog(
    mode: FileDialogMode,
    title: String = "Choose a file",
    parent: Frame? = null,
    block: FileDialog.() -> Unit = {},
    onCloseRequest: (result: String?, results: List<File>?) -> Unit,
) = AwtWindow(
    create = {
        object : FileDialog(parent, title, mode.id) {
            override fun setVisible(value: Boolean) {
                super.setVisible(value)
                if (value) {
                    onCloseRequest(
                        directory + File.separator + file,
                        files?.toList()
                    )
                }
            }
        }.apply(block)
    },
    dispose = FileDialog::dispose
)

private val allowedLanguages = listOf(
    CodeLang.Kotlin,
    CodeLang.Swift,
    CodeLang.XML,
    CodeLang.HTML,
)

fun main() {
    Runtime.getRuntime().addShutdownHook(Thread { outputDirectory.deleteRecursively() })
    application {
        WindowWithBar(
            onCloseRequest = ::exitApplication,
            windowTitle = "Svg Converter"
        ) {
            Surface {
                with(LocalWindow.current) {
                    App()
                }
            }
        }
    }
}
