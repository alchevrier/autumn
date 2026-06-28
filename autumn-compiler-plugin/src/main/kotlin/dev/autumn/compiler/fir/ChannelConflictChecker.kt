package dev.autumn.compiler.fir

import org.jetbrains.kotlin.diagnostics.DiagnosticReporter
import org.jetbrains.kotlin.diagnostics.reportOn
import org.jetbrains.kotlin.fir.analysis.checkers.context.CheckerContext
import org.jetbrains.kotlin.fir.analysis.checkers.declaration.FirPropertyChecker
import org.jetbrains.kotlin.fir.declarations.FirProperty
import org.jetbrains.kotlin.fir.declarations.hasAnnotation
import org.jetbrains.kotlin.fir.declarations.getAnnotationByClassId
import org.jetbrains.kotlin.fir.analysis.checkers.MppCheckerKind
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.fir.expressions.FirLiteralExpression

object ChannelConflictChecker : FirPropertyChecker(MppCheckerKind.Common) {

    private val COLD_CHANNEL_ID = ClassId.topLevel(FqName("dev.autumn.annotations.ColdChannel"))
    private val REGISTER_CHANNEL_ID = ClassId.topLevel(FqName("dev.autumn.annotations.RegisterChannel"))
    private val NETWORK_CHANNEL_ID = ClassId.topLevel(FqName("dev.autumn.annotations.BoundaryChannel"))

    // Normally this would be parsed from autumn.yaml or build.gradle.kts
    private const val MAX_ISOLATED_HOT_CORES = 4

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

        if (isFast) {
            val netAnnot = declaration.getAnnotationByClassId(NETWORK_CHANNEL_ID, context.session)
            val regAnnot = declaration.getAnnotationByClassId(REGISTER_CHANNEL_ID, context.session)
            val annot = netAnnot ?: regAnnot

            if (annot != null) {
                // Argument 2 is `sharded: Int`
                val mapping = annot.argumentMapping.mapping.values.toList()
                if (mapping.size > 2) {
                    val shardedArg = mapping[2] as? FirLiteralExpression
                    val shardedCount = (shardedArg?.value as? Int) ?: 1
                    
                    if (shardedCount > MAX_ISOLATED_HOT_CORES) {
                        // For the purpose of the demo, we report an error on the capacity diagnostic
                        reporter.reportOn(
                            declaration.source,
                            AutumnErrors.INVALID_REGISTER_CHANNEL_CAPACITY,
                            "Cannot allocate $shardedCount SPSC shards. The hardware topology only has $MAX_ISOLATED_HOT_CORES isolated cores.",
                            context
                        )
                    }
                }
            }
        }
    }
}
