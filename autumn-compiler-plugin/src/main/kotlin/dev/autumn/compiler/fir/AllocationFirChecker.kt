package dev.autumn.compiler.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.expression.FirFunctionCallChecker
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.expressions.FirFunctionCall
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.fir.types.classId
import org.jetbrains.kotlin.fir.types.coneType
import org.jetbrains.kotlin.fir.references.toResolvedConstructorSymbol
import org.jetbrains.kotlin.name.ClassId
import dev.autumn.compiler.AllocationExclusions
import org.jetbrains.kotlin.name.FqName

object AllocationFirChecker : FirFunctionCallChecker(MppCheckerKind.Common) {

    private val LONG_LIVED_CLASS_ID = ClassId.topLevel(FqName("dev.autumn.annotations.LongLived"))

    override fun check(expression: FirFunctionCall, context: CheckerContext, reporter: DiagnosticReporter) {
        val constructorSymbol = expression.calleeReference.toResolvedConstructorSymbol() ?: return
        
        // 1. Is this constructor call allowed because we're in a LongLived context?
        val isAllowed = context.containingDeclarations.any { declaration ->
            declaration.hasAnnotation(LONG_LIVED_CLASS_ID, context.session)
        }

        if (!isAllowed) {
            val classId = constructorSymbol.resolvedReturnTypeRef.coneType.classId
            val fqName = classId?.asSingleFqName()?.asString() ?: ""

            // Allow basic types (String, primitives) and Exceptions
            // In FIR, primitive arrays or standard primitives on constructors might present as kotlin.IntArray etc.
            val isNativePrimitive = fqName.startsWith("kotlin.") && fqName.endsWith("Array")
            
            // Simplified safe type check for FIR phase mirroring the IR phase logic using shared exclusion logic.
            val isSafeType = isNativePrimitive || AllocationExclusions.isSafeType(fqName)

            if (!isSafeType) {
                reporter.reportOn(
                    expression.source,
                    AutumnErrors.HEAP_ALLOCATION_IN_STRICT_SCOPE,
                    context
                )
            }
        }
    }
}
