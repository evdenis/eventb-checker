package com.eventb.checker.validation

import com.eventb.checker.model.EventBProject

class CrossReferenceValidator {

    fun validate(project: EventBProject): List<ValidationError> {
        val errors = mutableListOf<ValidationError>()

        val machineNames = project.machines.map { it.name }.toSet()
        val contextNames = project.contexts.map { it.name }.toSet()

        for (machine in project.machines) {
            for (seesTarget in machine.seesContexts) {
                if (seesTarget !in contextNames) {
                    errors.add(
                        ValidationError(
                            filePath = machine.filePath,
                            severity = ValidationSeverity.ERROR,
                            message = "SEES target context '$seesTarget' not found in project",
                            element = machine.name
                        )
                    )
                }
            }

            machine.refinesMachine?.let { refinesTarget ->
                if (refinesTarget !in machineNames) {
                    errors.add(
                        ValidationError(
                            filePath = machine.filePath,
                            severity = ValidationSeverity.ERROR,
                            message = "REFINES target machine '$refinesTarget' not found in project",
                            element = machine.name
                        )
                    )
                }
            }
        }

        for (ctx in project.contexts) {
            for (extendsTarget in ctx.extendsContexts) {
                if (extendsTarget !in contextNames) {
                    errors.add(
                        ValidationError(
                            filePath = ctx.filePath,
                            severity = ValidationSeverity.ERROR,
                            message = "EXTENDS target context '$extendsTarget' not found in project",
                            element = ctx.name
                        )
                    )
                }
            }
        }

        return errors
    }
}
