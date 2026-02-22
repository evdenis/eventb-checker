package com.eventb.checker.validation

import com.eventb.checker.model.*
import org.eventb.core.ast.Formula
import org.eventb.core.ast.FormulaFactory
import org.eventb.core.ast.IParseResult
import org.eventb.core.ast.ITypeEnvironmentBuilder

data class TypeCheckedFormula(
    val formula: Formula<*>,
    val formulaText: String,
    val filePath: String,
    val elementLabel: String,
    val kind: FormulaKind
)

data class TypeCheckResult(
    val errors: List<ValidationError>,
    val checkedFormulas: List<TypeCheckedFormula>
)

class TypeChecker(private val ff: FormulaFactory = FormulaFactory.getDefault()) {

    private val parsePredicate: (String) -> IParseResult = { ff.parsePredicate(it, null) }
    private val extractPredicate: (IParseResult) -> Formula<*> = { it.parsedPredicate }
    private val parseAssignment: (String) -> IParseResult = { ff.parseAssignment(it, null) }
    private val extractAssignment: (IParseResult) -> Formula<*> = { it.parsedAssignment }
    private val parseExpression: (String) -> IParseResult = { ff.parseExpression(it, null) }
    private val extractExpression: (IParseResult) -> Formula<*> = { it.parsedExpression }

    fun checkProject(project: EventBProject): List<ValidationError> {
        return checkProjectFull(project).errors
    }

    fun checkProjectFull(project: EventBProject): TypeCheckResult {
        val errors = mutableListOf<ValidationError>()
        val checkedFormulas = mutableListOf<TypeCheckedFormula>()

        val contextsByName = project.contexts.associateBy { it.name }
        val contextEnvs = mutableMapOf<String, ITypeEnvironmentBuilder>()

        for (ctx in project.contexts) {
            if (ctx.name !in contextEnvs) {
                buildContextEnv(ctx, contextsByName, contextEnvs, errors, checkedFormulas, mutableSetOf())
            }
        }

        val machinesByName = project.machines.associateBy { it.name }
        val machineEnvs = mutableMapOf<String, ITypeEnvironmentBuilder>()

        for (machine in project.machines) {
            if (machine.name !in machineEnvs) {
                checkMachine(machine, contextEnvs, machinesByName, machineEnvs, errors, checkedFormulas, mutableSetOf())
            }
        }

        return TypeCheckResult(errors, checkedFormulas)
    }

    private fun buildContextEnv(
        ctx: Context,
        contextsByName: Map<String, Context>,
        contextEnvs: MutableMap<String, ITypeEnvironmentBuilder>,
        errors: MutableList<ValidationError>,
        checkedFormulas: MutableList<TypeCheckedFormula>,
        visiting: MutableSet<String>
    ): ITypeEnvironmentBuilder {
        contextEnvs[ctx.name]?.let { return it }

        if (!visiting.add(ctx.name)) {
            return ff.makeTypeEnvironment()
        }

        val env = ff.makeTypeEnvironment()

        for (extName in ctx.extendsContexts) {
            val extCtx = contextsByName[extName] ?: continue
            val extEnv = buildContextEnv(extCtx, contextsByName, contextEnvs, errors, checkedFormulas, visiting)
            env.addAll(extEnv)
        }

        for (set in ctx.carrierSets) {
            env.addGivenSet(set.identifier)
        }

        for (axiom in ctx.axioms) {
            typeCheckFormula(axiom.predicate, env, ctx.filePath, axiom.label, errors, checkedFormulas,
                FormulaKind.PREDICATE, parsePredicate, extractPredicate)
        }

        contextEnvs[ctx.name] = env
        visiting.remove(ctx.name)
        return env
    }

    private fun checkMachine(
        machine: Machine,
        contextEnvs: Map<String, ITypeEnvironmentBuilder>,
        machinesByName: Map<String, Machine>,
        machineEnvs: MutableMap<String, ITypeEnvironmentBuilder>,
        errors: MutableList<ValidationError>,
        checkedFormulas: MutableList<TypeCheckedFormula>,
        visiting: MutableSet<String>
    ) {
        if (machine.name in machineEnvs) return
        if (!visiting.add(machine.name)) return

        val env = ff.makeTypeEnvironment()

        for (ctxName in machine.seesContexts) {
            contextEnvs[ctxName]?.let { env.addAll(it) }
        }

        machine.refinesMachine?.let { refName ->
            if (refName !in machineEnvs) {
                val refMachine = machinesByName[refName]
                if (refMachine != null) {
                    checkMachine(refMachine, contextEnvs, machinesByName, machineEnvs, errors, checkedFormulas, visiting)
                }
            }
            machineEnvs[refName]?.let { env.addAll(it) }
        }

        for (inv in machine.invariants) {
            typeCheckFormula(inv.predicate, env, machine.filePath, inv.label, errors, checkedFormulas,
                FormulaKind.PREDICATE, parsePredicate, extractPredicate)
        }

        machine.variant?.let { variant ->
            typeCheckFormula(variant.expression, env, machine.filePath, variant.label, errors, checkedFormulas,
                FormulaKind.EXPRESSION, parseExpression, extractExpression)
        }

        machineEnvs[machine.name] = env
        visiting.remove(machine.name)

        for (event in machine.events) {
            checkEvent(event, env, machine.filePath, errors, checkedFormulas)
        }
    }

    private fun checkEvent(
        event: Event,
        machineEnv: ITypeEnvironmentBuilder,
        filePath: String,
        errors: MutableList<ValidationError>,
        checkedFormulas: MutableList<TypeCheckedFormula>
    ) {
        val eventEnv = ff.makeTypeEnvironment()
        eventEnv.addAll(machineEnv)

        for (guard in event.guards) {
            typeCheckFormula(guard.predicate, eventEnv, filePath, "${event.label}/${guard.label}", errors, checkedFormulas,
                FormulaKind.PREDICATE, parsePredicate, extractPredicate)
        }

        for (action in event.actions) {
            typeCheckFormula(action.assignment, eventEnv, filePath, "${event.label}/${action.label}", errors, checkedFormulas,
                FormulaKind.ASSIGNMENT, parseAssignment, extractAssignment)
        }

        for (witness in event.witnesses) {
            typeCheckFormula(witness.predicate, eventEnv, filePath, "${event.label}/${witness.label}", errors, checkedFormulas,
                FormulaKind.PREDICATE, parsePredicate, extractPredicate)
        }
    }

    private fun typeCheckFormula(
        formula: String,
        env: ITypeEnvironmentBuilder,
        filePath: String,
        elementLabel: String,
        errors: MutableList<ValidationError>,
        checkedFormulas: MutableList<TypeCheckedFormula>,
        kind: FormulaKind,
        parse: (String) -> IParseResult,
        extract: (IParseResult) -> Formula<*>
    ) {
        val parseResult = parse(formula)
        if (parseResult.hasProblem()) return

        val parsed = extract(parseResult)
        val tcResult = parsed.typeCheck(env)

        if (tcResult.isSuccess) {
            env.addAll(tcResult.inferredEnvironment)
            checkedFormulas.add(TypeCheckedFormula(parsed, formula, filePath, elementLabel, kind))
        } else {
            for (problem in tcResult.problems) {
                errors.add(
                    ValidationError(
                        filePath = filePath,
                        severity = ValidationSeverity.WARNING,
                        message = "Type error: $problem",
                        element = elementLabel,
                        formula = formula
                    )
                )
            }
        }
    }
}
