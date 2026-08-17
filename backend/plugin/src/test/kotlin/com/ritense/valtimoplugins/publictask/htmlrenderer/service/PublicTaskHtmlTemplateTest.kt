/*
 * Copyright 2026 Ritense BV, the Netherlands.
 *
 * Licensed under EUPL, Version 1.2 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://joinup.ec.europa.eu/collection/eupl/eupl-text-eupl-12
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" basis,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.ritense.valtimoplugins.publictask.htmlrenderer.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.ritense.valtimoplugins.publictask.BaseTest
import com.ritense.valtimoplugins.publictask.htmlrenderer.config.FreemarkerConfig
import freemarker.core.HTMLOutputFormat
import freemarker.template.Template
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.StringWriter

internal class PublicTaskHtmlTemplateTest : BaseTest() {
    private val objectMapper = ObjectMapper()

    private val htmlRenderService = HtmlRenderService(FreemarkerConfig())

    @Test
    fun `a form value that closes the script element cannot break out of it`() {
        val formIoForm =
            """
            {
              "components": [
                { "key": "naam", "defaultValue": "</script><img src=x onerror=alert(1)>" }
              ]
            }
            """.trimIndent()

        val html = render(formIoForm)

        // No '<' survives in the data block, so nothing in it can close the element or open an HTML comment.
        assertThat(jsonDataBlockOf(html)).doesNotContain("<")
        assertThat(html).doesNotContain("<img")
        assertThat(html).contains("\\u003C/script>\\u003Cimg src=x onerror=alert(1)>")
    }

    @Test
    fun `a valid form definition is still readable as json after escaping`() {
        val formIoForm =
            objectMapper
                .createObjectNode()
                .put("quotes", """He said "hi" and \ left""")
                .put("unicode", "Ruben ë ç 😀")
                .put("newlines", "line one\nline two\ttabbed")
                .put("slashes", "https://example.org/a/b?c=d&e=f")
                .put("markup", "</script> <!-- <b>bold</b>")
                .toPrettyString()

        val html = render(formIoForm)

        assertThat(objectMapper.readTree(jsonDataBlockOf(html)))
            .isEqualTo(objectMapper.readTree(formIoForm))
    }

    @Test
    fun `the submit url is escaped for the javascript string literal it is placed in`() {
        val html = render(publicTaskUrl = """https://valtimo.example.org/api/v1/public-task/1' + alert(1) + '""")

        assertThat(html).doesNotContain("""' + alert(1) + '""")
        assertThat(html).contains("""\' + alert(1) + \'""")
    }

    @Test
    fun `the freemarker configuration escapes interpolations by default`() {
        assertThat(FreemarkerConfig().outputFormat).isEqualTo(HTMLOutputFormat.INSTANCE)

        // Guards the setting behaviourally as well: an interpolation without an explicit encoder must be escaped.
        val rendered =
            StringWriter()
                .apply {
                    Template("auto-escaping-check", "\${value}", FreemarkerConfig())
                        .process(mapOf("value" to "</script>"), this)
                }.toString()

        assertThat(rendered).doesNotContain("</script>")
    }

    private fun render(
        formIoForm: String = "{}",
        publicTaskUrl: String = "https://valtimo.example.org/api/v1/public-task/$PUBLIC_TASK_ID",
    ): String =
        htmlRenderService.generatePublicTaskHtml(
            fileName = "public_task_html",
            variables =
                mapOf(
                    "form_io_form" to formIoForm,
                    "public_task_url" to publicTaskUrl,
                ),
        )

    private fun jsonDataBlockOf(html: String): String =
        requireNotNull(JSON_DATA_BLOCK.find(html)) {
            "The rendered page does not contain a <script type=\"application/json\"> data block:\n$html"
        }.groupValues[1]

    companion object {
        private const val PUBLIC_TASK_ID = "3f2a1c4e-0b7d-4a19-9c5e-8d6f0a1b2c3d"

        private val JSON_DATA_BLOCK =
            Regex(
                """<script id="form-io-form" type="application/json">(.*?)</script>""",
                RegexOption.DOT_MATCHES_ALL,
            )
    }
}
