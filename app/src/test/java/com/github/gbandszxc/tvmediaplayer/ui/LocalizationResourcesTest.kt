package com.github.gbandszxc.tvmediaplayer.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

class LocalizationResourcesTest {

    @Test
    fun `english resources mirror default string resource names`() {
        val defaultStrings = File("src/main/res/values/strings.xml")
        val englishStrings = File("src/main/res/values-en/strings.xml")

        assertTrue("values-en/strings.xml should exist", englishStrings.exists())

        assertEquals(
            readStringNames(defaultStrings),
            readStringNames(englishStrings),
        )
    }

    @Test
    fun `layout text does not hardcode visible Chinese copy`() {
        val layoutDir = File("src/main/res/layout")
        val hardcodedChineseText = layoutDir
            .walkTopDown()
            .filter { it.isFile && it.extension == "xml" }
            .flatMap { file ->
                Regex("""android:text="[^"@]*[\p{IsHan}][^"]*"""")
                    .findAll(file.readText())
                    .map { "${file.name}: ${it.value}" }
            }
            .toList()

        assertTrue(
            "Chinese layout text should use @string resources: $hardcodedChineseText",
            hardcodedChineseText.isEmpty(),
        )
    }

    @Test
    fun `global scale description resources do not render double percent signs`() {
        listOf(
            File("src/main/res/values/strings.xml"),
            File("src/main/res/values-en/strings.xml"),
        ).forEach { file ->
            val document = DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(file)
            val nodes = document.getElementsByTagName("string")
            val node = (0 until nodes.length)
                .map { nodes.item(it) }
                .first { it.attributes.getNamedItem("name").nodeValue == "settings_global_scale_desc" }

            assertTrue(
                "${file.path} should not contain escaped double percent signs",
                !node.textContent.contains("%%"),
            )
        }
    }

    private fun readStringNames(file: File): List<String> {
        val document = DocumentBuilderFactory.newInstance()
            .newDocumentBuilder()
            .parse(file)
        val nodes = document.getElementsByTagName("string")
        return (0 until nodes.length)
            .map { nodes.item(it).attributes.getNamedItem("name").nodeValue }
            .sorted()
    }
}
