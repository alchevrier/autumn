package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.FqName

/**
 * Sweeps the IR tree looking for references to properties or parameters annotated
 * with @NetworkConcurrencyBudget. When such a reference is evaluated, it statically 
 * replaces the read expression with the exact literal integer from the annotation.
 */
class BudgetInjectionTransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector
) : IrElementTransformerVoidWithContext() {

    companion object {
        val NETWORK_BUDGET_FQ_NAME = FqName("dev.autumn.annotations.NetworkConcurrencyBudget")
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        val symbol = expression.symbol
        val declaration = symbol.owner

        if (declaration is IrValueParameter && declaration.hasAnnotation(NETWORK_BUDGET_FQ_NAME)) {
            val annotation = declaration.getAnnotation(NETWORK_BUDGET_FQ_NAME)
            
            // Extract the 'maxInFlightRequests' argument from the annotation
            // It is the first argument in the annotation signature.
            val maxInFlightOrNull = annotation?.getValueArgument(0)

            if (maxInFlightOrNull is IrConst) {
                val budgetValue = maxInFlightOrNull.value as? Int

                if (budgetValue != null) {
                    messageCollector.report(
                        CompilerMessageSeverity.INFO,
                        "Autumn K2 Plugin: Statically injecting Circuit-Based budget limit ($budgetValue slots) for network concurrency."
                    )
                    
                    // Rewrite the AST to yield the literal integer instead of reading 
                    // the parameter variable at runtime.
                    return IrConstImpl.int(
                        startOffset = expression.startOffset,
                        endOffset = expression.endOffset,
                        type = expression.type,
                        value = budgetValue
                    )
                }
            }
        }

        return super.visitGetValue(expression)
    }
}
