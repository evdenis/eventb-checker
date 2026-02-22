package com.eventb.checker.report

import com.eventb.checker.validation.ValidationResult

interface ReportFormatter {
    fun format(result: ValidationResult): String
}
