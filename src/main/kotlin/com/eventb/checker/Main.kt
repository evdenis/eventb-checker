package com.eventb.checker

import com.eventb.checker.report.JsonReportFormatter
import com.eventb.checker.report.TextReportFormatter
import com.eventb.checker.validation.ProjectValidator
import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.flag
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import kotlin.system.exitProcess

class CheckCommand :
    CliktCommand(
        name = "eventb-checker",
        help = "Validate an Event-B model (.zip archive, directory, or .eventb file)",
    ) {
    private val modelPath by argument(help = "Path to a .zip archive, directory, or .eventb file")
    private val verbose by option("--verbose", "-v", help = "Show formula text in error output").flag()
    private val format by option("--format", "-f", help = "Output format")
        .choice("text", "json").default("text")
    private val proofs by option("--proofs", "-p", help = "Check proof status from .bpr/.bpo/.bps files").flag()

    override fun run() {
        val validator = ProjectValidator(checkProofs = proofs)
        val result = try {
            validator.validate(modelPath)
        } catch (e: IllegalArgumentException) {
            echo("Error: ${e.message}", err = true)
            exitProcess(2)
        } catch (e: Exception) {
            echo("Unexpected error: ${e.message}", err = true)
            exitProcess(2)
        }

        val formatter = when (format) {
            "json" -> JsonReportFormatter()
            else -> TextReportFormatter(verbose)
        }
        echo(formatter.format(result))

        if (!result.isValid) {
            exitProcess(1)
        }
    }
}

fun main(args: Array<String>) = CheckCommand().main(args)
