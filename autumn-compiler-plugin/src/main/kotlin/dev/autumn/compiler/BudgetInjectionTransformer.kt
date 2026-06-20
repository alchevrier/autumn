package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrValueParameter
import org.jetbrains.kotlin.ir.declarations.IrVariable
import org.jetbrains.kotlin.ir.expressions.IrExpression
import org.jetbrains.kotlin.ir.expressions.IrGetValue
import org.jetbrains.kotlin.ir.expressions.impl.IrConstImpl
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.expressions.IrConst
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.ir.util.file
import java.io.File

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
        val INJECT_BUDGET_FQ_NAME = FqName("dev.autumn.annotations.InjectBudget")
    }

    override fun visitGetValue(expression: IrGetValue): IrExpression {
        val symbol = expression.symbol
        val declaration = symbol.owner

        if ((declaration is IrValueParameter || declaration is IrVariable) && declaration.hasAnnotation(NETWORK_BUDGET_FQ_NAME)) {
            val annotation = declaration.getAnnotation(NETWORK_BUDGET_FQ_NAME)
            val maxInFlightOrNull = annotation?.getValueArgument(0)

            if (maxInFlightOrNull is IrConst) {
                val budgetValue = maxInFlightOrNull.value as? Int
                if (budgetValue != null) {
                    messageCollector.report(
                        CompilerMessageSeverity.INFO,
                        "Autumn K2 Plugin: Statically injecting Circuit-Based budget limit ($budgetValue slots) for network concurrency."
                    )
                    return IrConstImpl.int(
                        startOffset = expression.startOffset,
                        endOffset = expression.endOffset,
                        type = expression.type,
                        value = budgetValue
                    )
                }
            }
        }

        if ((declaration is IrValueParameter || declaration is IrVariable) && declaration.hasAnnotation(INJECT_BUDGET_FQ_NAME)) {
            val annotation = declaration.getAnnotation(INJECT_BUDGET_FQ_NAME)
            val schemaPathOrNull = annotation?.getValueArgument(4) as? IrConst // fromSchema
            val scopeOrNull = annotation?.getValueArgument(0) as? IrConst

            if (schemaPathOrNull != null) {
                val schemaFileName = schemaPathOrNull.value as? String
                if (!schemaFileName.isNullOrBlank()) {
                    var calculatedBudget = 0
                    
                    // Simple simulated JSON schema compiler parsing (in real K2 we map via kotlinx.serialization)
                    try {
                        val file = File(declaration.file.fileEntry.name).parentFile.resolve(schemaFileName)
                        if (file.exists()) {
                            val content = file.readText()
                            if (content.contains("staticComponents") && content.contains("itemsPerPage")) {
                                val staticMatch = "\"staticComponents\":\\s*(\\d+)".toRegex().find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                val listMatch = "\"paginatedLists\":\\s*(\\d+)".toRegex().find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                val itemsMatch = "\"itemsPerPage\":\\s*(\\d+)".toRegex().find(content)?.groupValues?.get(1)?.toIntOrNull() ?: 0
                                
                                val bucketNodes = staticMatch + (listMatch * itemsMatch)
                                
                                calculatedBudget = if ((scopeOrNull?.value as? String) == "StringPointers") {
                                    bucketNodes * 4 // 4 pointers per node
                                } else {
                                    bucketNodes
                                }
                            }
                        }
                    } catch (e: Exception) {
                        messageCollector.report(CompilerMessageSeverity.WARNING, "Autumn JSON Schema parsing failed: ${e.message}")
                    }

                    if (calculatedBudget > 0) {
                        messageCollector.report(
                            CompilerMessageSeverity.INFO,
                            "Autumn K2 Plugin: Evaluated '$schemaFileName' and resolved 0-alloc sizing budget limit to $calculatedBudget statically."
                        )
                        return IrConstImpl.int(
                            startOffset = expression.startOffset,
                            endOffset = expression.endOffset,
                            type = expression.type,
                            value = calculatedBudget
                        )
                    }
                }
            } else {
                // Precomputed calculation without schema
                val singleCount = (annotation?.getValueArgument(1) as? IrConst)?.value as? Int ?: 0
                val colCount = (annotation?.getValueArgument(2) as? IrConst)?.value as? Int ?: 0
                val paged = (annotation?.getValueArgument(3) as? IrConst)?.value as? Int ?: 0
                
                var calculatedBudget = singleCount + (colCount * paged)
                val isStrings = ((annotation?.getValueArgument(0) as? IrConst)?.value as? String) == "StringPointers"
                if (isStrings && calculatedBudget > 0) calculatedBudget *= 4
                
                if (calculatedBudget > 0) {
                    messageCollector.report(
                        CompilerMessageSeverity.INFO,
                        "Autumn K2 Plugin: Fallback AST matrix computation -> resolved 0-alloc limit to $calculatedBudget statically."
                    )
                    return IrConstImpl.int(
                        startOffset = expression.startOffset,
                        endOffset = expression.endOffset,
                        type = expression.type,
                        value = calculatedBudget
                    )
                }
            }
        }

        return super.visitGetValue(expression)
    }
}
