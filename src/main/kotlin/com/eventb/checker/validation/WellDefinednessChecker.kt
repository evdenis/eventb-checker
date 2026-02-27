package com.eventb.checker.validation

import org.eventb.core.ast.Formula

class WellDefinednessChecker {

    fun check(checkedFormulas: List<TypeCheckedFormula>): List<ValidationError> {
        val findings = mutableListOf<ValidationError>()

        for (tcf in checkedFormulas) {
            val wdPredicate = tcf.formula.wdPredicate ?: continue

            if (wdPredicate.tag == Formula.BTRUE) continue

            findings.add(
                ValidationError(
                    filePath = tcf.filePath,
                    severity = ValidationSeverity.INFO,
                    message = "Well-definedness condition: $wdPredicate",
                    element = tcf.elementLabel,
                    formula = tcf.formulaText,
                    ruleId = ValidationRules.WELL_DEFINEDNESS.id,
                ),
            )
        }

        return findings
    }
}
