package dev.autumn.compiler.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirSimpleFunctionChecker
import org.jetbrains.kotlin.fir.declarations.FirSimpleFunction
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName

object ThreadCacheBudgetChecker : FirSimpleFunctionChecker(MppCheckerKind.Common) {

    private val THREAD_CACHE_BUDGET_ID = ClassId.topLevel(FqName("dev.autumn.annotations.ThreadCacheBudget"))

    override fun check(
        declaration: FirSimpleFunction,
        context: CheckerContext,
        reporter: DiagnosticReporter
    ) {
        val budgetAnnotation = declaration.getAnnotationByClassId(THREAD_CACHE_BUDGET_ID, context.session)
        if (budgetAnnotation != null) {
            // budget annotation exists
        }
    }
}
