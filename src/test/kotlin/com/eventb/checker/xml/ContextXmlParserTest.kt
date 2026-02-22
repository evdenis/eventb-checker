package com.eventb.checker.xml

import com.eventb.checker.TestXmlHelper.parseDoc
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class ContextXmlParserTest {

    private val parser = ContextXmlParser()

    @Test
    fun `parses a simple context with carrier sets and constants`() {
        val xml = """
            <org.eventb.core.contextFile name="SimpleContext">
                <org.eventb.core.carrierSet org.eventb.core.identifier="COLOR"/>
                <org.eventb.core.constant org.eventb.core.identifier="red"/>
                <org.eventb.core.axiom org.eventb.core.label="axm1"
                    org.eventb.core.predicate="red ∈ COLOR" org.eventb.core.theorem="false"/>
            </org.eventb.core.contextFile>
        """.trimIndent()

        val result = parser.parse(parseDoc(xml), "SimpleContext.buc")

        assertThat(result.errors).isEmpty()
        assertThat(result.context.name).isEqualTo("SimpleContext")
        assertThat(result.context.carrierSets).hasSize(1)
        assertThat(result.context.carrierSets[0].identifier).isEqualTo("COLOR")
        assertThat(result.context.constants).hasSize(1)
        assertThat(result.context.constants[0].identifier).isEqualTo("red")
        assertThat(result.context.axioms).hasSize(1)
        assertThat(result.context.axioms[0].predicate).isEqualTo("red ∈ COLOR")
    }

    @Test
    fun `parses extends context`() {
        val xml = """
            <org.eventb.core.contextFile name="ExtContext">
                <org.eventb.core.extendsContext org.eventb.core.target="BaseContext"/>
            </org.eventb.core.contextFile>
        """.trimIndent()

        val result = parser.parse(parseDoc(xml), "ExtContext.buc")

        assertThat(result.errors).isEmpty()
        assertThat(result.context.extendsContexts).containsExactly("BaseContext")
    }

    @Test
    fun `parses theorem axioms`() {
        val xml = """
            <org.eventb.core.contextFile name="TheoremCtx">
                <org.eventb.core.axiom org.eventb.core.label="thm1"
                    org.eventb.core.predicate="1 + 1 = 2" org.eventb.core.theorem="true"/>
            </org.eventb.core.contextFile>
        """.trimIndent()

        val result = parser.parse(parseDoc(xml), "TheoremCtx.buc")

        assertThat(result.errors).isEmpty()
        assertThat(result.context.axioms[0].theorem).isTrue()
    }

    @Test
    fun `reports error for wrong root element`() {
        val xml = """<wrongRoot name="Bad"/>"""

        val result = parser.parse(parseDoc(xml), "Bad.buc")

        assertThat(result.errors).anyMatch { it.message.contains("Expected root element") }
    }

    @Test
    fun `parses empty context`() {
        val xml = """<org.eventb.core.contextFile name="Empty"/>"""

        val result = parser.parse(parseDoc(xml), "Empty.buc")

        assertThat(result.errors).isEmpty()
        assertThat(result.context.carrierSets).isEmpty()
        assertThat(result.context.constants).isEmpty()
        assertThat(result.context.axioms).isEmpty()
    }

    @Test
    fun `derives context name from file path when name attr missing`() {
        val xml = """<org.eventb.core.contextFile/>"""

        val result = parser.parse(parseDoc(xml), "project/Derived.buc")

        assertThat(result.context.name).isEqualTo("Derived")
    }
}
