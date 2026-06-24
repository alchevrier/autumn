package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrStatementOrigin
import org.jetbrains.kotlin.ir.expressions.impl.IrBlockBodyImpl
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irCall
import org.jetbrains.kotlin.ir.builders.irInt
import org.jetbrains.kotlin.ir.builders.irGetObject

class MemoryBankInitializationTransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector,
    private val totalAllocatedBytes: Int
) : IrElementTransformerVoidWithContext() {

    override fun visitFunctionNew(declaration: IrFunction): org.jetbrains.kotlin.ir.IrStatement {
        // Find the main() entry point to natively inject Memory Bank Initialization
        if (declaration.name.asString() == "main" && totalAllocatedBytes > 0) {
            
            messageCollector.report(
                CompilerMessageSeverity.INFO,
                "[Autumn HLS] Discovered Entry Point 'main'. Injecting Global Hardware Initialization -> AutumnMemoryBank.allocate($totalAllocatedBytes)"
            )

            val memoryBankClass = pluginContext.referenceClass(org.jetbrains.kotlin.name.ClassId.topLevel(FqName("dev.autumn.memory.AutumnMemoryBank")))
            val allocateFunc = memoryBankClass?.owner?.declarations?.filterIsInstance<IrFunction>()?.find { it.name.asString() == "allocate" }

            if (allocateFunc != null && memoryBankClass != null) {
                val builder = DeclarationIrBuilder(pluginContext, declaration.symbol)
                val allocateCall = builder.irCall(allocateFunc.symbol).apply {
                    dispatchReceiver = builder.irGetObject(memoryBankClass)
                    putValueArgument(0, builder.irInt(totalAllocatedBytes))
                }

                val currentBody = declaration.body as? IrBlockBodyImpl
                if (currentBody != null) {
                    // Prepend the memory allocation exactly at the start of app execution!
                    currentBody.statements.add(0, allocateCall)
                }
            }
        }
        return super.visitFunctionNew(declaration)
    }
}
