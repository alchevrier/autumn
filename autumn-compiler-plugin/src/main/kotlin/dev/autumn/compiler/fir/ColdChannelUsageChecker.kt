package dev.autumn.compiler.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirPropertyAccessExpressionChecker
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirPropertyAccessExpression
import org.jetbrains.kotlin.fir.references.toResolvedPropertySymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

object ColdChannelUsageChecker : FirPropertyAccessExpressionChecker(MppCheckerKind.Common) {

    private val COLD_CHANNEL_ID = ClassId.topLevel(FqName("dev.autumn.annotations.ColdChannel"))
    private val HOT_PATH_ID = ClassId.topLevel(FqName("dev.autumn.annotations.HotPath"))

    override fun check(
        expression: FirPropertyAccessExpression,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        val propertySymbol = expression.calleeReference.toResolvedPropertySymbol() ?: return

        // 1. Are we accessing a @ColdChannel?
        val isCold = propertySymbol.hasAnnotation(COLD_CHANNEL_ID, context.session)
        if (!isCold) return

        // 2. Are we inside a @HotPath execution context?
        val inHotPath = context.containingDeclarations.any { declaration ->
            declaration.hasAnnotation(HOT_PATH_ID, context.session)
        }

        // 3. If so, fail the build to protect the CPU cache
        if (inHotPath) {
            reporter.reportOn(
                expression.source,
                AutumnErrors.COLD_CHANNEL_IN_HOT_PATH,
                context
            )
        }
    }
}
