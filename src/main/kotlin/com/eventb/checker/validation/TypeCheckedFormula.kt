package com.eventb.checker.validation

import org.eventb.core.ast.Assignment
import org.eventb.core.ast.Formula

data class TypeCheckedFormula(
    val formula: Formula<*>,
    val formulaText: String,
    val filePath: String,
    val elementLabel: String,
    val kind: FormulaKind,
)

data class TypeCheckResult(val errors: List<ValidationError>, val checkedFormulas: List<TypeCheckedFormula>)

fun List<TypeCheckedFormula>.referencedIdentifierNames(): Set<String> = flatMap { it.formula.freeIdentifiers.map { id -> id.name } }.toSet()

fun List<TypeCheckedFormula>.extractAssignedIdentifiers(): Set<String> = filter { it.kind == FormulaKind.ASSIGNMENT }
    .flatMap { (it.formula as Assignment).assignedIdentifiers.map { id -> id.name } }
    .toSet()
