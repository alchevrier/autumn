package dev.autumn.compiler.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

object ChannelConflictChecker : FirPropertyChecker(MppCheckerKind.Common) {

    private val COLD_CHANNEL_ID = ClassId.topLevel(FqName("dev.autumn.annotations.ColdChannel"))
    private val REGISTER_CHANNEL_ID = ClassId.topLevel(FqName("dev.autumn.annotations.RegisterChannel"))
    private val NETWORK_CHANNEL_ID = ClassId.topLevel(FqName("dev.autumn.annotations.NetworkChannel"))

    override fun check(
        declaration: FirProperty,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        val isCold = declaration.hasAnnotation(COLD_CHANNEL_ID, context.session)
        val isFast = declaration.hasAnnotation(REGISTER_CHANNEL_ID, context.session) ||
                     declaration.hasAnnotation(NETWORK_CHANNEL_ID, context.session)

        if (isCold && isFast) {
            reporter.reportOn(
                declaration.source,
                AutumnErrors.CONFLICTING_CHANNEL_TYPES,
                context
            )
        }
    }
}
