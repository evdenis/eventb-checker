package com.eventb.checker.xml

import com.eventb.checker.TestXmlHelper.parseDoc
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class MachineXmlParserTest {

    private val parser = MachineXmlParser()

    @Test
    fun `parses a simple machine with variables and invariants`() {
        val xml = """
            <org.eventb.core.machineFile name="SimpleMachine">
                <org.eventb.core.variable org.eventb.core.identifier="x" org.eventb.core.label="x"/>
                <org.eventb.core.invariant org.eventb.core.label="inv1"
                    org.eventb.core.predicate="x ∈ ℕ" org.eventb.core.theorem="false"/>
            </org.eventb.core.machineFile>
        """.trimIndent()

        val result = parser.parse(parseDoc(xml), "SimpleMachine.bum")

        assertThat(result.errors).isEmpty()
        assertThat(result.machine.name).isEqualTo("SimpleMachine")
        assertThat(result.machine.variables).hasSize(1)
        assertThat(result.machine.variables[0].identifier).isEqualTo("x")
        assertThat(result.machine.invariants).hasSize(1)
        assertThat(result.machine.invariants[0].predicate).isEqualTo("x ∈ ℕ")
        assertThat(result.machine.invariants[0].theorem).isFalse()
    }

    @Test
    fun `parses events with guards and actions`() {
        val xml = """
            <org.eventb.core.machineFile name="EventMachine">
                <org.eventb.core.event org.eventb.core.label="increment"
                    org.eventb.core.convergence="0" org.eventb.core.extended="false">
                    <org.eventb.core.parameter org.eventb.core.identifier="p"/>
                    <org.eventb.core.guard org.eventb.core.label="grd1"
                        org.eventb.core.predicate="x &lt; 10"/>
                    <org.eventb.core.action org.eventb.core.label="act1"
                        org.eventb.core.assignment="x ≔ x + 1"/>
                </org.eventb.core.event>
            </org.eventb.core.machineFile>
        """.trimIndent()

        val result = parser.parse(parseDoc(xml), "EventMachine.bum")

        assertThat(result.errors).isEmpty()
        assertThat(result.machine.events).hasSize(1)
        val event = result.machine.events[0]
        assertThat(event.label).isEqualTo("increment")
        assertThat(event.parameters).hasSize(1)
        assertThat(event.guards).hasSize(1)
        assertThat(event.guards[0].predicate).isEqualTo("x < 10")
        assertThat(event.actions).hasSize(1)
        assertThat(event.actions[0].assignment).isEqualTo("x ≔ x + 1")
    }

    @Test
    fun `parses sees and refines`() {
        val xml = """
            <org.eventb.core.machineFile name="RefMachine">
                <org.eventb.core.seesContext org.eventb.core.target="MyContext"/>
                <org.eventb.core.refinesMachine org.eventb.core.target="BaseMachine"/>
            </org.eventb.core.machineFile>
        """.trimIndent()

        val result = parser.parse(parseDoc(xml), "RefMachine.bum")

        assertThat(result.errors).isEmpty()
        assertThat(result.machine.seesContexts).containsExactly("MyContext")
        assertThat(result.machine.refinesMachine).isEqualTo("BaseMachine")
    }

    @Test
    fun `parses variant`() {
        val xml = """
            <org.eventb.core.machineFile name="VarMachine">
                <org.eventb.core.variant org.eventb.core.label="vrn1"
                    org.eventb.core.expression="card(s)"/>
            </org.eventb.core.machineFile>
        """.trimIndent()

        val result = parser.parse(parseDoc(xml), "VarMachine.bum")

        assertThat(result.errors).isEmpty()
        assertThat(result.machine.variant).isNotNull
        assertThat(result.machine.variant!!.expression).isEqualTo("card(s)")
    }

    @Test
    fun `reports error for wrong root element`() {
        val xml = """<wrongRoot name="Bad"/>"""

        val result = parser.parse(parseDoc(xml), "Bad.bum")

        assertThat(result.errors).anyMatch { it.message.contains("Expected root element") }
    }

    @Test
    fun `reports warning for missing required attribute`() {
        val xml = """
            <org.eventb.core.machineFile name="Missing">
                <org.eventb.core.variable/>
            </org.eventb.core.machineFile>
        """.trimIndent()

        val result = parser.parse(parseDoc(xml), "Missing.bum")

        assertThat(result.errors).anyMatch { it.message.contains("missing required attribute") }
    }

    @Test
    fun `parses event with witnesses and refinesEvent`() {
        val xml = """
            <org.eventb.core.machineFile name="WitnessMachine">
                <org.eventb.core.event org.eventb.core.label="refEvt"
                    org.eventb.core.convergence="0" org.eventb.core.extended="false">
                    <org.eventb.core.refinesEvent org.eventb.core.target="baseEvt"/>
                    <org.eventb.core.witness org.eventb.core.label="wit1"
                        org.eventb.core.predicate="y = x + 1"/>
                </org.eventb.core.event>
            </org.eventb.core.machineFile>
        """.trimIndent()

        val result = parser.parse(parseDoc(xml), "WitnessMachine.bum")

        assertThat(result.errors).isEmpty()
        val event = result.machine.events[0]
        assertThat(event.refinesEvents).containsExactly("baseEvt")
        assertThat(event.witnesses).hasSize(1)
        assertThat(event.witnesses[0].predicate).isEqualTo("y = x + 1")
    }
}
