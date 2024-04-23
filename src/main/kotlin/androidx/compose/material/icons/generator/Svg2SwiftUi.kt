package androidx.compose.material.icons.generator

import androidx.compose.material.icons.generator.vector.Fill
import androidx.compose.material.icons.generator.vector.Vector
import androidx.compose.material.icons.generator.vector.VectorNode
import com.android.ide.common.vectordrawable.Svg2Vector
import io.outfoxx.swiftpoet.*
import java.io.File
import java.util.*

object Svg2SwiftUi {
    /**
     * This is for direct file to string conversions.
     *
     * Parses a list of files and generates Kotlin code representations of icons.
     *
     * @param accessorName the name of the Kotlin property used to access the generated icons
     * @param fileList the list of files to parse and generate icons for
     * @param iconNameTransformer transforms the icon name to a valid Kotlin property name (default implementation converts snake_case to upper camel case)
     * @param generatePreview whether to generate preview code for the icons (default is true)
     * @return the list of generated FileInfo objects, containing the generated FileSpec and the name of the icon
     */
    fun parseToString(
        accessorName: String,
        fileList: List<File>,
        iconNameTransformer: IconNameTransformer = { it, _ -> it.toKotlinPropertyName() },
        generatePreview: Boolean = true,
    ): List<FileSpec> {

        val drawableDir = drawableTempDirectory()

        return fileList
            .mapNotNull {
                when (VectorType.entries.find { v -> v.extension == it.extension }) {
                    VectorType.SVG -> {
                        val iconName = it.nameWithoutExtension

                        val parsedFile = File(drawableDir, "${iconName}.xml")
                        parsedFile.parentFile.mkdirs()

                        Svg2Vector.parseSvgToXml(it, parsedFile.outputStream())

                        it to parsedFile
                    }

                    VectorType.DRAWABLE -> it to it
                    else -> null
                }
            }
            .associate { (vectorFile, drawableFile) ->
                vectorFile to Icon(
                    iconNameTransformer(
                        drawableFile.nameWithoutExtension.trim(),
                        accessorName
                    ),
                    drawableFile.name,
                    drawableFile.readText()
                )
            }
            .let { icons ->
                generateToString(icons)
            }
    }

    fun generateToString(
        icons: Map<File, Icon>,
        iconNamePredicate: (String) -> Boolean = { true },
    ): List<FileSpec> {

        return icons.filter { icon ->
            iconNamePredicate(icon.value.kotlinName)
        }.map { icon ->
            val iconName = icon.value.kotlinName

            val vector = IconParser(icon.value).parse()

            val (fileSpec, _) = VectorAssetGenerator2(
                iconName,
                "groupPackage",
                vector,
                true
            ).createFileSpec()
            fileSpec
        }
    }

    private fun drawableTempDirectory() = createTempDir(suffix = "svg2compose/")

    private val String.withoutExtension get() = substringBeforeLast(".")
}

private fun GeneratedGroup.asParsingResult(): ParsingResult = ParsingResult(
    groupName = groupName,
    generatedIconsMemberNames = generatedIconsMemberNames,
    generatedGroups = childGroups.map { it.asParsingResult() },
)

class VectorAssetGenerator2(
    private val iconName: String,
    private val iconGroupPackage: String,
    private val vector: Vector,
    private val generatePreview: Boolean,
) {
    /**
     * @return a [FileSpec] representing a Kotlin source file containing the property for this
     * programmatic [vector] representation.
     *
     * The package name and hence file location of the generated file is:
     * [PackageNames.MaterialIconsPackage] + [IconTheme.themePackageName].
     */
    fun createFileSpec(): VectorAssetGenerationResult2 {
        // Use a unique property name for the private backing property. This is because (as of
        // Kotlin 1.4) each property with the same name will be considered as a possible candidate
        // for resolution, regardless of the access modifier, so by using unique names we reduce
        // the size from ~6000 to 1, and speed up compilation time for these icons.
        val backingPropertyName = "." + iconName.decapitalize(Locale.ROOT)
        val backingProperty = backingPropertySpec2(name = backingPropertyName, DeclaredTypeName.typeName(".Shape"))

        //TODO: Still working on this!

        val generation = FileSpec.builder(
            fileName = iconName
        )
            .addType(
                TypeSpec.structBuilder(
                    iconName
                        .replace(" ", "")
                        .replace("-", "")
                )
                    .addSuperType(DeclaredTypeName.qualifiedTypeName(".Shape"))
                    .addFunction(
                        FunctionSpec.builder("path")
                            .returns(DeclaredTypeName.typeName(".Path"))
                            .addParameter(
                                ParameterSpec.builder(
                                    argumentLabel = "rect",
                                    parameterName = "rect",
                                    type = DeclaredTypeName.qualifiedTypeName(".CGRect"),
                                    Modifier.INOUT
                                ).build()
                            )
                            .addCode(
                                CodeBlock.builder().apply {
                                    addStatement("var path = Path()")
                                    addStatement("let width = rect.size.width")
                                    addStatement("let height = rect.size.height")
                                    vector.nodes.forEach { node -> addRecursively(node) }
                                    addStatement("return path")
                                }
                                    .build()
                            )
                            .build()
                    )
                    .build()
            )
            /*.addProperty(
                PropertySpec.builder(name = iconName, type = DeclaredTypeName.typeName(".Shape"))
                    //.receiver(groupClassName)
                    .getter(iconGetter(backingProperty))
                    .build()
            )
            .addProperty(backingProperty)*/
            //.apply { if (generatePreview) addFunction(iconPreview(MemberName("", iconName))) }
            .build()

        return VectorAssetGenerationResult2(generation, iconName)
    }

    /**
     * @return the body of the getter for the icon property. This getter returns the backing
     * property if it is not null, otherwise creates the icon and 'caches' it in the backing
     * property, and then returns the backing property.
     */
    private fun iconGetter(backingProperty: PropertySpec): FunctionSpec {
        val parameterList = with(vector) {
            listOfNotNull(
                "name = \"${iconName}\"",
                "defaultWidth = ${width.withMemberIfNotNull}",
                "defaultHeight = ${height.withMemberIfNotNull}",
                "viewportWidth = ${viewportWidth}f",
                "viewportHeight = ${viewportHeight}f"
            )
        }

        val parameters = parameterList.joinToString(prefix = "(", postfix = ")")

        val members: Array<Any> = listOfNotNull(
            MemberNames.ImageVectorBuilder,
            vector.width.memberName,
            vector.height.memberName
        ).toTypedArray()

        return FunctionSpec.getterBuilder()
            .withBackingProperty2(backingProperty) {
                addCode(
                    CodeBlock.builder().apply {
                        beginControlFlow(
                            "asdf",
                            "%N = 1",
                            backingProperty,
                            //*members
                        )
                        vector.nodes.forEach { node -> addRecursively(node) }
                        endControlFlow("asdf")
                        addStatement(".build()")
                    }
                        .build()
                )
            }
            .build()
    }

    /**
     * @param iconName Name that will be used to call the Icon inside the preview.
     *
     * Example:
     * ```kotlin
     *   @Preview
     *   @Composable
     *   private fun Preview(): Unit {
     *      Box(modifier = Modifier.padding(12.dp)) {
     *          Image(imageVector = Icon.Foo, contentDescription = "")
     *      }
     *   }
     * ```
     */
    /*private fun iconPreview(iconName: MemberName): FunctionSpec {
        val previewAnnotation = AnnotationSpec.builder(ClassNames.Preview).build()
        val composableAnnotation = AnnotationSpec.builder(ClassNames.Composable).build()
        val box = MemberName(PackageNames.LayoutPackage.packageName, "Box")
        val modifier = MemberName(PackageNames.UiPackage.packageName, "Modifier")
        val padding = MemberName(PackageNames.LayoutPackage.packageName, "padding")
        val paddingValue = MemberNames.Dp
        val composeImage = MemberName(PackageNames.FoundationPackage.packageName, "Image")

        return FunctionSpec.builder("Preview")
            .addModifiers(Modifier.PRIVATE)
            .addAnnotation(previewAnnotation)
            .addAnnotation(composableAnnotation)
            .addCode(
                CodeBlock.builder()
                    .beginControlFlow("%M(modifier = %M.%M(12.%M))", box, modifier, padding, paddingValue)
                    .addStatement("%M(imageVector = %M, contentDescription = \"\")", composeImage, iconName)
                    .endControlFlow()
                    .build()
            )
            .build()
    }*/
}

/**
 * Recursively adds function calls to construct the given [vectorNode] and its children.
 */
private fun CodeBlock.Builder.addRecursively(vectorNode: VectorNode) {
    when (vectorNode) {
        // TODO: b/147418351 - add clip-paths once they are supported
        is VectorNode.Group -> {
            vectorNode.paths.forEach { path ->
                addRecursively(path)
            }
        }

        is VectorNode.Path -> {
            addPath(vectorNode) {
                vectorNode.nodes.forEach { pathNode ->
                    addStatement("path" + pathNode.asSwiftFunctionCall())
                }
            }
        }
    }
}

/**
 * Adds a function call to create the given [path], with [pathBody] containing the commands for
 * the path.
 */
private fun CodeBlock.Builder.addPath(
    path: VectorNode.Path,
    pathBody: CodeBlock.Builder.() -> Unit,
) {
    val hasStrokeColor = path.strokeColorHex != null

    val parameterList = with(path) {
        listOfNotNull(
            "fill = ${getPathFill(path)}",
            "stroke = ${if (hasStrokeColor) "%M(%M(0x$strokeColorHex))" else "null"}",
            "fillAlpha = ${fillAlpha}f".takeIf { fillAlpha != 1f },
            "strokeAlpha = ${strokeAlpha}f".takeIf { strokeAlpha != 1f },
            "strokeLineWidth = ${strokeLineWidth.withMemberIfNotNull}",
            "strokeLineCap = %M",
            "strokeLineJoin = %M",
            "strokeLineMiter = ${strokeLineMiter}f",
            "pathFillType = %M"
        )
    }

    val parameters = parameterList.joinToString(prefix = "(", postfix = ")")

    val members: Array<Any> = listOfNotNull(
        MemberNames.Path,
        MemberNames.SolidColor.takeIf { hasStrokeColor },
        MemberNames.Color.takeIf { hasStrokeColor },
        path.strokeLineWidth.memberName,
        path.strokeLineCap.memberName,
        path.strokeLineJoin.memberName,
        path.fillType.memberName
    ).toMutableList().apply {
        var fillIndex = 1
        when (path.fill) {
            is Fill.Color -> {
                add(fillIndex, MemberNames.SolidColor)
                add(++fillIndex, MemberNames.Color)
            }

            is Fill.LinearGradient -> {
                add(fillIndex, MemberNames.LinearGradient)
                path.fill.colorStops.forEach { _ ->
                    add(++fillIndex, MemberNames.Color)
                }
                add(++fillIndex, MemberNames.Offset)
                add(++fillIndex, MemberNames.Offset)
            }

            is Fill.RadialGradient -> {
                add(fillIndex, MemberNames.RadialGradient)
                path.fill.colorStops.forEach { _ ->
                    add(++fillIndex, MemberNames.Color)
                }
                add(++fillIndex, MemberNames.Offset)
            }

            null -> {}
        }
    }.toTypedArray()

    pathBody()
}

private fun getPathFill(
    path: VectorNode.Path,
) = when (path.fill) {
    is Fill.Color -> "%M(%M(0x${path.fill.colorHex}))"
    is Fill.LinearGradient -> {
        with(path.fill) {
            "%M(" +
                    "${getGradientStops(path.fill.colorStops).toString().removeSurrounding("[", "]")}, " +
                    "start = %M(${startX}f,${startY}f), " +
                    "end = %M(${endX}f,${endY}f))"
        }
    }

    is Fill.RadialGradient -> {
        with(path.fill) {
            "%M(${getGradientStops(path.fill.colorStops).toString().removeSurrounding("[", "]")}, " +
                    "center = %M(${centerX}f,${centerY}f), " +
                    "radius = ${gradientRadius}f)"
        }
    }

    else -> "null"
}

private fun getGradientStops(
    stops: List<Pair<Float, String>>,
) = stops.map { stop ->
    "${stop.first}f to %M(0x${stop.second})"
}

private fun CodeBlock.Builder.addLinearGradient(
    gradient: Fill.LinearGradient,
    pathBody: CodeBlock.Builder.() -> Unit,
) {
    //"0.0f to Color.Red"
    val parameterList = with(gradient) {
        listOfNotNull(
            "start = %M(${gradient.startX},${gradient.startY})",
            "end = %M(${gradient.endX},${gradient.endY})"
        )
    }

    val parameters = parameterList.joinToString(prefix = "(", postfix = ")")

    val members: Array<Any> = listOfNotNull(
        MemberNames.LinearGradient,
        MemberNames.Offset,
        MemberNames.Offset
    ).toTypedArray()


}

private val GraphicUnit.withMemberIfNotNull: String get() = "${value}${if (memberName != null) ".%M" else "f"}"

fun PackageNames.typeName(vararg classNames: String) = DeclaredTypeName.typeName(this.packageName)

data class VectorAssetGenerationResult2(
    val sourceGeneration: FileSpec, val accessProperty: String,
)

internal fun backingPropertySpec2(name: String, type: TypeName): PropertySpec {
    val nullableVectorAsset = type.makeOptional()//copy(nullable = true)
    return PropertySpec.builder(name = name, type = nullableVectorAsset)
        .mutable(true)
        .addModifiers(Modifier.PRIVATE)
        .initializer("null")
        .build()
}

internal inline fun FunctionSpec.Builder.withBackingProperty2(
    backingProperty: PropertySpec,
    block: FunctionSpec.Builder.() -> Unit,
): FunctionSpec.Builder = apply {
    addCode(
        CodeBlock.builder()
            .beginControlFlow("asdf", controlFlowCode = "if (%N != null)", args = arrayOf(backingProperty))
            .addStatement("return %N!!", backingProperty)
            .endControlFlow("asdf")
            .build()
    )
        .apply(block)
        .addStatement("return %N!!", backingProperty)
}