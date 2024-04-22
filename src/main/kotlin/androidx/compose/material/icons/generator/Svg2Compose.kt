package androidx.compose.material.icons.generator

import androidx.compose.material.icons.generator.*
import com.android.ide.common.vectordrawable.Svg2Vector
import com.squareup.kotlinpoet.ClassName
import com.squareup.kotlinpoet.FileSpec
import com.squareup.kotlinpoet.MemberName
import java.io.File
import java.util.*

typealias IconNameTransformer = (iconName: String, group: String) -> String

object Svg2Compose {

    /**
     * Generates source code for the [vectors] files.
     *
     * Supported types: SVG, Android Vector Drawable XML
     *
     * @param applicationIconPackage Represents what will be the final package of the generated Vector Source. ex com.yourcompany.yourapplication.icons
     * @param accessorName will be usage to access the Vector in the code like `MyIconPack.IconName` or `MyIconPack.IconGroupDir.IconName`
     */
    fun parse(
        applicationIconPackage: String,
        accessorName: String,
        outputSourceDirectory: File,
        vectorsDirectory: File,
        type: VectorType = VectorType.SVG,
        iconNameTransformer: IconNameTransformer = { it, _ -> it.toKotlinPropertyName() },
        allAssetsPropertyName: String = "AllAssets",
        generatePreview: Boolean = true,
    ): ParsingResult {
        fun nameRelative(vectorFile: File) = vectorFile.relativeTo(vectorsDirectory).path

        val drawableDir = drawableTempDirectory()

        val groupStack = Stack<GeneratedGroup>()

        vectorsDirectory.walkTopDown()
            .maxDepth(10)
            .onEnter { file ->
                val dirIcons = file.listFiles()
                    ?.filter { it.isDirectory.not() }
                    ?.filter { it.extension.equals(type.extension, ignoreCase = true) }
                    .orEmpty()

                val previousGroup = groupStack.peekOrNull()

                // if there is no previous group, this is the root dir, and the group name should be the accessorName
                val groupName = if (previousGroup == null) accessorName else file.name.toKotlinPropertyName()
                val groupPackage = previousGroup?.let { group ->
                    "${group.groupPackage}.${
                        group.groupName.second.lowercase(
                            Locale.getDefault()
                        )
                    }"
                } ?: applicationIconPackage
                val iconsPackage = "$groupPackage.${groupName.lowercase(Locale.getDefault())}"

                val (groupFileSpec, groupClassName) = IconGroupGenerator(
                    groupPackage,
                    groupName
                ).createFileSpec(previousGroup?.groupClass)

                val generatedIconsMemberNames: Map<VectorFile, MemberName> =
                    if (dirIcons.isNotEmpty()) {
                        val drawables: List<Pair<File, File>> = when (type) {
                            VectorType.SVG -> dirIcons.map {
                                val iconName = nameRelative(it).withoutExtension

                                val parsedFile = File(drawableDir, "${iconName}.xml")
                                parsedFile.parentFile.mkdirs()

                                Svg2Vector.parseSvgToXml(it, parsedFile.outputStream())

                                it to parsedFile
                            }.toList()

                            VectorType.DRAWABLE -> dirIcons.toList().map { it to it }
                        }

                        val icons: Map<VectorFile, Icon> = drawables.associate { (vectorFile, drawableFile) ->
                            vectorFile to Icon(
                                iconNameTransformer(
                                    drawableFile.nameWithoutExtension.trim(),
                                    groupName
                                ),
                                drawableFile.name,
                                drawableFile.readText()
                            )
                        }

                        val writer = IconWriter(
                            icons.values,
                            groupClassName,
                            "",
                            generatePreview
                        )

                        val memberNames = writer.generateTo(outputSourceDirectory) { true }

                        icons.mapValues { entry ->
                            memberNames.first { it.simpleName == entry.value.kotlinName }
                        }
                    } else {
                        emptyMap<VectorFile, MemberName>()
                    }

                val result = GeneratedGroup(
                    groupPackage,
                    file to groupName,
                    generatedIconsMemberNames,
                    groupClassName,
                    groupFileSpec,
                    childGroups = emptyList()
                )

                if (previousGroup != null) {
                    groupStack.pop()
                    groupStack.push(previousGroup.copy(childGroups = previousGroup.childGroups + result))
                }

                groupStack.push(result)

                true
            }
            .onLeave {
                val group = if (groupStack.size > 1)
                    groupStack.pop()
                else
                    groupStack.peek()

                val allAssetsGenerator = AllIconAccessorGenerator(
                    group.generatedIconsMemberNames.values,
                    group.groupClass,
                    allAssetsPropertyName,
                    group.childGroups
                )

                for (propertySpec in allAssetsGenerator.createPropertySpec(group.groupFileSpec)) {
                    group.groupFileSpec.addProperty(propertySpec)
                }

                group.groupFileSpec.build().writeTo(outputSourceDirectory)
            }
            .toList() // consume, to onEnter and onLeave be triggered

        return groupStack.pop().asParsingResult()
    }

    fun parseToString(
        applicationIconPackage: String,
        accessorName: String,
        outputSourceDirectory: File,
        vectorsDirectory: File,
        type: VectorType = VectorType.SVG,
        iconNameTransformer: IconNameTransformer = { it, _ -> it.toKotlinPropertyName() },
        allAssetsPropertyName: String = "AllAssets",
        generatePreview: Boolean = true,
    ): List<FileInfo> {
        fun nameRelative(vectorFile: File) = vectorFile.relativeTo(vectorsDirectory).path

        val drawableDir = drawableTempDirectory()

        val groupStack = Stack<GeneratedGroup>()

        val f = vectorsDirectory.walkTopDown()
            .maxDepth(10)
            .map { file ->
                val dirIcons = file.listFiles()
                    ?.filter { it.isDirectory.not() }
                    //?.filter { it.extension.equals(type.extension, ignoreCase = true) }
                    .orEmpty()

                val previousGroup = groupStack.peekOrNull()

                // if there is no previous group, this is the root dir, and the group name should be the accessorName
                val groupName = if (previousGroup == null) accessorName else file.name.toKotlinPropertyName()
                val groupPackage = previousGroup?.let { group ->
                    "${group.groupPackage}.${
                        group.groupName.second.lowercase(
                            Locale.getDefault()
                        )
                    }"
                } ?: applicationIconPackage
                val iconsPackage = "$groupPackage.${groupName.lowercase(Locale.getDefault())}"

                val (groupFileSpec, groupClassName) = IconGroupGenerator(
                    groupPackage,
                    groupName
                ).createFileSpec(previousGroup?.groupClass)

                if (dirIcons.isNotEmpty()) {
                    val drawables: List<Pair<File, File>> = dirIcons.mapNotNull {
                        when (VectorType.entries.find { v -> v.extension == it.extension }) {
                            VectorType.SVG -> {
                                val iconName = nameRelative(it).withoutExtension

                                val parsedFile = File(drawableDir, "${iconName}.xml")
                                parsedFile.parentFile.mkdirs()

                                Svg2Vector.parseSvgToXml(it, parsedFile.outputStream())

                                it to parsedFile
                            }

                            VectorType.DRAWABLE -> it to it
                            else -> null
                        }
                    }
                    /*val drawables: List<Pair<File, File>> = when (type) {
                        VectorType.SVG -> dirIcons.map {
                            val iconName = nameRelative(it).withoutExtension

                            val parsedFile = File(drawableDir, "${iconName}.xml")
                            parsedFile.parentFile.mkdirs()

                            Svg2Vector.parseSvgToXml(it, parsedFile.outputStream())

                            it to parsedFile
                        }.toList()
                        VectorType.DRAWABLE -> dirIcons.toList().map { it to it }
                    }*/

                    val icons: Map<VectorFile, Icon> = drawables.associate { (vectorFile, drawableFile) ->
                        vectorFile to Icon(
                            iconNameTransformer(
                                drawableFile.nameWithoutExtension.trim(),
                                groupName
                            ),
                            drawableFile.name,
                            drawableFile.readText()
                        )
                    }

                    val writer = IconWriter(
                        icons.values,
                        groupClassName,
                        "",
                        generatePreview
                    )

                    val memberNames = writer.generateToString(outputSourceDirectory) { true }

                    /*icons.mapValues { entry ->
                        memberNames.first { it.simpleName == entry.value.kotlinName }
                    }*/
                    memberNames
                } else {
                    //emptyMap<VectorFile, MemberName>()
                    emptyList()
                }

                /* val result = GeneratedGroup(
                     groupPackage,
                     file to groupName,
                     generatedIconsMemberNames,
                     groupClassName,
                     groupFileSpec,
                     childGroups = emptyList()
                 )

                 if(previousGroup != null) {
                     groupStack.pop()
                     groupStack.push(previousGroup.copy(childGroups = previousGroup.childGroups + result))
                 }

                 groupStack.push(result)*/
            }
        /* .map {
             val group = if(groupStack.size > 1)
                 groupStack.pop()
             else
                 groupStack.peek()

             val allAssetsGenerator = AllIconAccessorGenerator(
                 group.generatedIconsMemberNames.values,
                 group.groupClass,
                 allAssetsPropertyName,
                 group.childGroups
             )

             for (propertySpec in allAssetsGenerator.createPropertySpec(group.groupFileSpec)) {
                 group.groupFileSpec.addProperty(propertySpec)
             }

             group.groupFileSpec.build()
         }
         .toList() */// consume, to onEnter and onLeave be triggered

        return f.flatten().toList()//groupStack.pop().asParsingResult()
    }

    fun parseToString(
        applicationIconPackage: String,
        accessorName: String,
        outputSourceDirectory: File,
        fileList: List<File>,
        type: VectorType = VectorType.SVG,
        iconNameTransformer: IconNameTransformer = { it, _ -> it.toKotlinPropertyName() },
        allAssetsPropertyName: String = "AllAssets",
        generatePreview: Boolean = true,
    ): List<FileInfo> {
        //fun nameRelative(vectorFile: File) = vectorFile.relativeTo(vectorsDirectory).path

        val drawableDir = drawableTempDirectory()

        val groupStack = Stack<GeneratedGroup>()

        val f = fileList
            .map { file ->
                val dirIcons = file.listFiles()
                    ?.filter { it.isDirectory.not() }
                    ?.filter { it.extension.equals(type.extension, ignoreCase = true) }
                    .orEmpty()

                val previousGroup = groupStack.peekOrNull()

                // if there is no previous group, this is the root dir, and the group name should be the accessorName
                val groupName = if (previousGroup == null) accessorName else file.name.toKotlinPropertyName()
                val groupPackage = previousGroup?.let { group ->
                    "${group.groupPackage}.${
                        group.groupName.second.lowercase(
                            Locale.getDefault()
                        )
                    }"
                } ?: applicationIconPackage
                val iconsPackage = "$groupPackage.${groupName.lowercase(Locale.getDefault())}"

                val (groupFileSpec, groupClassName) = IconGroupGenerator(
                    groupPackage,
                    groupName
                ).createFileSpec(previousGroup?.groupClass)

                if (dirIcons.isNotEmpty()) {
                    val drawables: List<Pair<File, File>> = when (type) {
                        VectorType.SVG -> dirIcons.map {
                            val iconName = it.nameWithoutExtension//nameRelative(it).withoutExtension

                            val parsedFile = File(drawableDir, "${iconName}.xml")
                            parsedFile.parentFile.mkdirs()

                            Svg2Vector.parseSvgToXml(it, parsedFile.outputStream())

                            it to parsedFile
                        }.toList()

                        VectorType.DRAWABLE -> dirIcons.toList().map { it to it }
                    }

                    val icons: Map<VectorFile, Icon> = drawables.associate { (vectorFile, drawableFile) ->
                        vectorFile to Icon(
                            iconNameTransformer(
                                drawableFile.nameWithoutExtension.trim(),
                                groupName
                            ),
                            drawableFile.name,
                            drawableFile.readText()
                        )
                    }

                    val writer = IconWriter(
                        icons.values,
                        groupClassName,
                        "",
                        generatePreview
                    )

                    val memberNames = writer.generateToString(outputSourceDirectory) { true }

                    /*icons.mapValues { entry ->
                        memberNames.first { it.simpleName == entry.value.kotlinName }
                    }*/
                    memberNames
                } else {
                    //emptyMap<VectorFile, MemberName>()
                    emptyList()
                }

                /* val result = GeneratedGroup(
                     groupPackage,
                     file to groupName,
                     generatedIconsMemberNames,
                     groupClassName,
                     groupFileSpec,
                     childGroups = emptyList()
                 )

                 if(previousGroup != null) {
                     groupStack.pop()
                     groupStack.push(previousGroup.copy(childGroups = previousGroup.childGroups + result))
                 }

                 groupStack.push(result)*/
            }
        /* .map {
             val group = if(groupStack.size > 1)
                 groupStack.pop()
             else
                 groupStack.peek()

             val allAssetsGenerator = AllIconAccessorGenerator(
                 group.generatedIconsMemberNames.values,
                 group.groupClass,
                 allAssetsPropertyName,
                 group.childGroups
             )

             for (propertySpec in allAssetsGenerator.createPropertySpec(group.groupFileSpec)) {
                 group.groupFileSpec.addProperty(propertySpec)
             }

             group.groupFileSpec.build()
         }
         .toList() */// consume, to onEnter and onLeave be triggered

        return f.flatten().toList()//groupStack.pop().asParsingResult()
    }

    private fun drawableTempDirectory() = createTempDir(suffix = "svg2compose/")

    private val String.withoutExtension get() = substringBeforeLast(".")
}

typealias GroupFolder = File
typealias VectorFile = File

data class GeneratedGroup(
    val groupPackage: String,
    val groupName: Pair<GroupFolder, String>,
    val generatedIconsMemberNames: Map<VectorFile, MemberName>,
    val groupClass: ClassName,
    val groupFileSpec: FileSpec.Builder,
    val childGroups: List<GeneratedGroup>,
)

data class ParsingResult(
    val groupName: Pair<GroupFolder, String>,
    val generatedIconsMemberNames: Map<VectorFile, MemberName>,
    val generatedGroups: List<ParsingResult>,
)

private fun GeneratedGroup.asParsingResult(): ParsingResult = ParsingResult(
    groupName = groupName,
    generatedIconsMemberNames = generatedIconsMemberNames,
    generatedGroups = childGroups.map { it.asParsingResult() },
)

fun <T> Stack<T>.peekOrNull(): T? = runCatching { peek() }.getOrNull()

enum class VectorType(val extension: String) {
    SVG("svg"), DRAWABLE("xml")
}