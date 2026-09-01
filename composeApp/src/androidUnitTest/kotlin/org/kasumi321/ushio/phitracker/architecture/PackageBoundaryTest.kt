package org.kasumi321.ushio.phitracker.architecture

import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.createTempDirectory
import kotlin.io.path.createDirectories
import kotlin.io.path.writeText
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class PackageBoundaryTest {
    @Test
    fun forbiddenImportReportsExactDiagnostic() = withFixture(
        "commonMain/kotlin/org/kasumi321/ushio/phitracker/domain/model/Bad.kt" to
            "package org.kasumi321.ushio.phitracker.domain.model\nimport org.kasumi321.ushio.phitracker.data.api.Secret\n"
    ) { root ->
        assertEquals(
            listOf("commonMain/kotlin/org/kasumi321/ushio/phitracker/domain/model/Bad.kt:2 [domain-no-data] org.kasumi321.ushio.phitracker.data.api.Secret"),
            PackageBoundaryChecker.scan(root).diagnostics
        )
    }

    @Test
    fun forbiddenProjectFqnReportsExactDiagnostic() = withFixture(
        "commonMain/kotlin/org/kasumi321/ushio/phitracker/data/Bad.kt" to
            "package org.kasumi321.ushio.phitracker.data\nval bad = org.kasumi321.ushio.phitracker.ui.Screen\n"
    ) { root ->
        assertEquals(
            listOf("commonMain/kotlin/org/kasumi321/ushio/phitracker/data/Bad.kt:2 [data-no-ui] org.kasumi321.ushio.phitracker.ui.Screen"),
            PackageBoundaryChecker.scan(root).diagnostics
        )
    }

    @Test
    fun viewModelCannotReferenceSamePackagePeerWithoutImport() = withFixture(
        "commonMain/kotlin/org/kasumi321/ushio/phitracker/ui/ShellViewModel.kt" to
            "package org.kasumi321.ushio.phitracker.ui\nclass ShellViewModel { val peer: PeerViewModel? = null }\n",
        "commonMain/kotlin/org/kasumi321/ushio/phitracker/ui/PeerViewModel.kt" to
            "package org.kasumi321.ushio.phitracker.ui\nclass PeerViewModel\n"
    ) { root ->
        assertEquals(
            listOf("commonMain/kotlin/org/kasumi321/ushio/phitracker/ui/ShellViewModel.kt:2 [viewmodel-no-peer-viewmodel] PeerViewModel"),
            PackageBoundaryChecker.scan(root).diagnostics
        )
    }

    @Test
    fun missingProjectDirFailsWithExactRequirement() {
        val error = assertFailsWith<IllegalArgumentException> {
            PackageBoundaryChecker.requireProjectDir(null)
        }

        assertEquals("Required system property phitracker.projectDir is missing", error.message)
    }

    @Test
    fun missingProductionRootFailsWithExactRequirement() {
        val root = createTempDirectory("package-boundary-missing-root")
        try {
            root.resolve("commonMain/kotlin/Placeholder.kt").apply {
                parent.createDirectories()
                writeText("class Placeholder")
            }

            val error = assertFailsWith<IllegalArgumentException> {
                PackageBoundaryChecker.scan(root)
            }

            assertEquals("Required production Kotlin root is missing: androidMain/kotlin", error.message)
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun emptyProductionRootFailsWithExactRequirement() {
        val root = createTempDirectory("package-boundary-empty-root")
        try {
            listOf("commonMain", "iosMain").forEach { sourceSet ->
                root.resolve("$sourceSet/kotlin/Placeholder.kt").apply {
                    parent.createDirectories()
                    writeText("package fixture.$sourceSet\nclass FixturePlaceholder")
                }
            }
            root.resolve("androidMain/kotlin").createDirectories()

            val error = assertFailsWith<IllegalArgumentException> {
                PackageBoundaryChecker.scan(root)
            }

            assertEquals(
                "Required production Kotlin root contains no Kotlin files: androidMain/kotlin",
                error.message
            )
        } finally {
            root.toFile().deleteRecursively()
        }
    }

    @Test
    fun malformedSourceLineStillReportsForbiddenFqn() = withFixture(
        "commonMain/kotlin/org/kasumi321/ushio/phitracker/data/Malformed.kt" to
            "package org.kasumi321.ushio.phitracker.data\n??? org.kasumi321.ushio.phitracker.ui.Screen\n"
    ) { root ->
        assertEquals(
            listOf("commonMain/kotlin/org/kasumi321/ushio/phitracker/data/Malformed.kt:2 [data-no-ui] org.kasumi321.ushio.phitracker.ui.Screen"),
            PackageBoundaryChecker.scan(root).diagnostics
        )
    }

    @Test
    fun tipsProviderRemainsPermittedFromUi() = withFixture(
        "commonMain/kotlin/org/kasumi321/ushio/phitracker/ui/Tips.kt" to
            "package org.kasumi321.ushio.phitracker.ui\nimport org.kasumi321.ushio.phitracker.data.TipsProvider\n"
    ) { root ->
        assertTrue(PackageBoundaryChecker.scan(root).diagnostics.isEmpty())
    }

    @Test
    fun productionSourcesRespectBoundariesAndAllRootsAreScanned() {
        val projectDir = PackageBoundaryChecker.requireProjectDir(System.getProperty("phitracker.projectDir"))
        val result = PackageBoundaryChecker.scan(projectDir.resolve("composeApp/src"))

        println("PackageBoundaryTest production scan counts: ${result.sourceCounts}")
        assertTrue(result.sourceCounts.values.all { it > 0 }, "Every production source root must contain Kotlin files: ${result.sourceCounts}")
        assertEquals(emptyList(), result.diagnostics)
    }

    private fun withFixture(vararg files: Pair<String, String>, assertion: (Path) -> Unit) {
        val root = createTempDirectory("package-boundary-fixture")
        try {
            listOf("commonMain", "androidMain", "iosMain").forEach { sourceSet ->
                root.resolve("$sourceSet/kotlin/FixturePlaceholder.kt").apply {
                    parent.createDirectories()
                    writeText("package fixture.$sourceSet\nclass FixturePlaceholder")
                }
            }
            files.forEach { (relativePath, source) ->
                root.resolve(relativePath).apply {
                    parent.createDirectories()
                    writeText(source)
                }
            }
            assertion(root)
        } finally {
            root.toFile().deleteRecursively()
        }
    }
}

private data class ScanResult(
    val diagnostics: List<String>,
    val sourceCounts: Map<String, Int>
)

private object PackageBoundaryChecker {
    private const val PROJECT_PACKAGE = "org.kasumi321.ushio.phitracker"
    private val sourceSets = listOf("commonMain", "androidMain", "iosMain")
    private val declaredViewModelPattern = Regex("\\b(?:data\\s+|sealed\\s+|abstract\\s+|open\\s+)?class\\s+([A-Za-z_][A-Za-z0-9_]*ViewModel)\\b")
    private val projectReferencePattern = Regex("org\\.kasumi321\\.ushio\\.phitracker(?:\\.[A-Za-z_][A-Za-z0-9_]*|\\.\\*)+")

    fun requireProjectDir(value: String?): Path =
        Path.of(requireNotNull(value?.takeIf { it.isNotBlank() }) {
            "Required system property phitracker.projectDir is missing"
        })

    fun scan(sourceRoot: Path): ScanResult {
        val sourcesBySet = sourceSets.associateWith { sourceSet ->
            val relativeRoot = "$sourceSet/kotlin"
            val kotlinRoot = sourceRoot.resolve(relativeRoot)
            require(Files.isDirectory(kotlinRoot)) {
                "Required production Kotlin root is missing: $relativeRoot"
            }
            Files.walk(kotlinRoot).use { paths ->
                paths.filter { Files.isRegularFile(it) && it.fileName.toString().endsWith(".kt") }
                    .sorted()
                    .toList()
            }.also { files ->
                require(files.isNotEmpty()) {
                    "Required production Kotlin root contains no Kotlin files: $relativeRoot"
                }
            }
        }
        val allSources = sourcesBySet.values.flatten()
        val declaredViewModels = allSources.flatMap { source ->
            Files.readAllLines(source).flatMap { line ->
                declaredViewModelPattern.findAll(line).map { it.groupValues[1] }.toList()
            }
        }.toSet()
        val diagnostics = buildList {
            allSources.forEach { source ->
                val relativePath = sourceRoot.relativize(source).toString().replace('\\', '/')
                val selfViewModels = Files.readAllLines(source).flatMap { line ->
                    declaredViewModelPattern.findAll(line).map { it.groupValues[1] }.toList()
                }.toSet()
                Files.readAllLines(source).forEachIndexed { index, line ->
                    projectReferencePattern.findAll(line).map { it.value }.toSet().forEach { reference ->
                        layerViolation(relativePath, reference)?.let { rule ->
                            add("$relativePath:${index + 1} [$rule] $reference")
                        }
                    }
                    if (selfViewModels.isNotEmpty()) {
                        (declaredViewModels - selfViewModels).sorted().forEach { viewModel ->
                            if (Regex("\\b${Regex.escape(viewModel)}\\b").containsMatchIn(line)) {
                                add("$relativePath:${index + 1} [viewmodel-no-peer-viewmodel] $viewModel")
                            }
                        }
                    }
                }
            }
        }
        return ScanResult(
            diagnostics = diagnostics,
            sourceCounts = sourcesBySet.mapValues { it.value.size }
        )
    }

    private fun layerViolation(relativePath: String, reference: String): String? = when {
        "/domain/" in relativePath && reference.startsWith("$PROJECT_PACKAGE.data.") -> "domain-no-data"
        "/domain/" in relativePath && reference.startsWith("$PROJECT_PACKAGE.ui.") -> "domain-no-ui"
        "/data/" in relativePath && reference.startsWith("$PROJECT_PACKAGE.ui.") -> "data-no-ui"
        "/ui/" in relativePath && listOf("database", "api", "parser", "repository").any {
            reference.startsWith("$PROJECT_PACKAGE.data.$it.")
        } -> "ui-no-data-infrastructure"
        else -> null
    }
}
