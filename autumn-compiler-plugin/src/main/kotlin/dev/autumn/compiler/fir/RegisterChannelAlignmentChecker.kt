package dev.autumn.compiler.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.getStringArgument
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

object RegisterChannelAlignmentChecker : FirPropertyChecker(MppCheckerKind.Common) {

    private val REGISTER_CHANNEL_ID = ClassId.topLevel(FqName("dev.autumn.annotations.RegisterChannel"))

    override fun check(
        declaration: FirProperty,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        val registerAnnotation = declaration.getAnnotationByClassId(REGISTER_CHANNEL_ID, context.session) ?: return

        // Extract the declared size dynamically from the FIR AST Tree
        val argumentExpr = registerAnnotation.argumentMapping.mapping.values.firstOrNull() as? FirLiteralExpression

        val size = (argumentExpr?.value as? Number)?.toInt() ?: 1024

        // Validate Power of 2 (Hardware Modulo limitation)
        if (size <= 0 || (size and (size - 1)) != 0) {
            reporter.reportOn(
                registerAnnotation.source ?: declaration.source,
                AutumnErrors.INVALID_REGISTER_CHANNEL_CAPACITY,
                size.toString(), // Tell them what we found
                context
            )
        }
    }
}
