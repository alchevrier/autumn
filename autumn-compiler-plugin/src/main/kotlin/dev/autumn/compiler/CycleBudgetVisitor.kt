package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.*
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.IrElement

class CycleBudgetVisitor(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector
) : IrElementVisitorVoid {

    private val CYCLE_BUDGET_FQ_NAME = FqName("dev.autumn.annotations.CycleBudget")

    override fun visitElement(element: IrElement) {
        element.acceptChildren(this, null)
    }

    override fun visitFunction(declaration: IrFunction) {
        if (declaration.hasAnnotation(CYCLE_BUDGET_FQ_NAME)) {
            val annot = declaration.getAnnotation(CYCLE_BUDGET_FQ_NAME)
            val limitArg = annot?.getValueArgument(0) as? IrConst
            val budgetLimit = (limitArg?.value as? Int) ?: 200

            var estimatedCycles = 0
            
            declaration.body?.accept(object : IrElementVisitorVoid {
                override fun visitElement(element: IrElement) {
                    estimatedCycles += when (element) {
                        is IrCall -> 10       
                        is IrGetValue -> 1    
                        is IrSetField -> 2    
                        is IrBranch -> 3      
                        is IrReturn -> 2      
                        is IrTypeOperatorCall -> 1 
                        else -> 1             
                    }
                    element.acceptChildren(this, null)
                }
            }, null)

            val severity = if (estimatedCycles > budgetLimit) CompilerMessageSeverity.ERROR else CompilerMessageSeverity.INFO
            val state = if (estimatedCycles > budgetLimit) "VIOLATION" else "VERIFIED"

            messageCollector.report(
                severity,
                "[Autumn HLS] Cycle Budget $state: '${declaration.name.asString()}' estimated at $estimatedCycles cycles (Limit: $budgetLimit)."
            )
        }
        
    }
}
