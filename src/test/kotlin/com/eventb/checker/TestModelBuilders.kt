package com.eventb.checker

import com.eventb.checker.model.*

object TestModelBuilders {

    fun project(
        machines: List<Machine> = emptyList(),
        contexts: List<Context> = emptyList()
    ) = EventBProject(
        name = "test",
        machines = machines,
        contexts = contexts,
        otherFiles = emptyList()
    )

    fun machine(
        name: String,
        seesContexts: List<String> = emptyList(),
        refinesMachine: String? = null,
        variables: List<Variable> = emptyList(),
        invariants: List<Invariant> = emptyList(),
        variant: Variant? = null,
        events: List<Event> = emptyList()
    ) = Machine(
        name = name,
        filePath = "$name.bum",
        seesContexts = seesContexts,
        refinesMachine = refinesMachine,
        variables = variables,
        invariants = invariants,
        variant = variant,
        events = events
    )

    fun context(
        name: String,
        extendsContexts: List<String> = emptyList(),
        carrierSets: List<CarrierSet> = emptyList(),
        constants: List<Constant> = emptyList(),
        axioms: List<Axiom> = emptyList()
    ) = Context(
        name = name,
        filePath = "$name.buc",
        extendsContexts = extendsContexts,
        carrierSets = carrierSets,
        constants = constants,
        axioms = axioms
    )

    fun event(
        label: String,
        parameters: List<Parameter> = emptyList(),
        guards: List<Guard> = emptyList(),
        actions: List<Action> = emptyList(),
        witnesses: List<Witness> = emptyList()
    ) = Event(
        label = label,
        convergence = 0,
        extended = false,
        refinesEvents = emptyList(),
        parameters = parameters,
        guards = guards,
        actions = actions,
        witnesses = witnesses
    )
}
