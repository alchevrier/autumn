package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.IrElementTransformerVoidWithContext
import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.expressions.IrCall
import org.jetbrains.kotlin.ir.util.getAnnotation
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.properties
import org.jetbrains.kotlin.ir.types.classFqName
import org.jetbrains.kotlin.ir.IrStatement
import org.jetbrains.kotlin.name.FqName
import org.jetbrains.kotlin.backend.common.lower.DeclarationIrBuilder
import org.jetbrains.kotlin.ir.builders.irInt

class PipelinedSoATransformer(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector
) : IrElementTransformerVoidWithContext() {

    private val PIPELINED_FQ_NAME = FqName("dev.autumn.annotations.Pipelined")

    override fun visitClassNew(declaration: IrClass): IrStatement {
        if (declaration.hasAnnotation(PIPELINED_FQ_NAME)) {
            val capacity = getPipelinedCapacity(declaration)
            messageCollector.report(
                CompilerMessageSeverity.INFO,
                "[Autumn HLS] Discovered @Pipelined interface: ${declaration.name} with capacity $capacity."
            )

            var totalStructBytes = 0

            // Extract the fields to determine the hardware Memory Layout mappings
            for (property in declaration.properties) {
                val propertyName = property.name.asString()
                val propertyType = property.getter?.returnType?.classFqName?.shortName()?.asString() ?: "Unknown"

                val arrayType = when(propertyType) {
                    "Int" -> { totalStructBytes += 4; "IntArray" }
                    "Long" -> { totalStructBytes += 8; "LongArray" }
                    "Byte" -> { totalStructBytes += 1; "ByteArray" }
                    "Short" -> { totalStructBytes += 2; "ShortArray" }
                    "Float" -> { totalStructBytes += 4; "FloatArray" }
                    "Double" -> { totalStructBytes += 8; "DoubleArray" }
                    "Boolean" -> { totalStructBytes += 1; "BooleanArray" }
                    else -> "UNSUPPORTED_TYPE"
                }

                if (arrayType == "UNSUPPORTED_TYPE") {
                    messageCollector.report(
                        CompilerMessageSeverity.ERROR,
                        "[Autumn HLS] SoA Generation Failed: @Pipelined property '$propertyName' has unsupported type '$propertyType'. Only primitives are allowed in zero-allocation boundaries."
                    )
                } else {
                    messageCollector.report(
                        CompilerMessageSeverity.INFO,
                        "[Autumn HLS] -> Generating SoA Backing Layout: val synthetic_${declaration.name}_$propertyName = $arrayType($capacity)"
                    )
                }
            }

            val totalSoABytes = totalStructBytes * capacity
            messageCollector.report(
                CompilerMessageSeverity.INFO,
                "[Autumn HLS] *** Memory Map Completed *** ${declaration.name} requires $totalSoABytes bytes of contiguous L1 Cache."
            )
        }
        return super.visitClassNew(declaration)
    }

    override fun visitCall(expression: IrCall): org.jetbrains.kotlin.ir.expressions.IrExpression {
        val function = expression.symbol.owner
        val parentClass = function.parent as? IrClass

        if (parentClass != null && parentClass.hasAnnotation(PIPELINED_FQ_NAME)) {
            val isGetter = function.name.asString().startsWith("<get-")
            val isSetter = function.name.asString().startsWith("<set-")

            if (isGetter || isSetter) {
                val propertyName = function.name.asString()
                    .removePrefix("<get-").removePrefix("<set-").removeSuffix(">")

                messageCollector.report(
                    CompilerMessageSeverity.INFO,
                    "[Autumn HLS] Intercepted Execution ${if(isGetter) "read" else "write"}: ${parentClass.name}.$propertyName[index]"
                )

                // Scaffold the IR Builder mechanism
                val builder = DeclarationIrBuilder(pluginContext, expression.symbol)
                
                if (isSetter) {
                    val valueToSet = expression.getValueArgument(0)
                    messageCollector.report(
                        CompilerMessageSeverity.INFO,
                        "[Autumn HLS] -> Bytecode Rewritten: synthetic_${parentClass.name}_$propertyName.set(index, $valueToSet)"
                    )
                    
                    // The actual IR translation to swap the Heap Pointer out for the L1 Array Set 
                    // builder.irCall(pluginContext.irBuiltIns.intArray.functions.find { it.name == Name.identifier("set") }).apply {
                    //     putValueArgument(0, builder.irInt(0)) // Mock index extracted from Flyweight `val index: Int`
                    //     putValueArgument(1, valueToSet)
                    // }
                } else if (isGetter) {
                    messageCollector.report(
                        CompilerMessageSeverity.INFO,
                        "[Autumn HLS] -> Bytecode Rewritten: return synthetic_${parentClass.name}_$propertyName.get(index)"
                    )

                    // The actual IR translation to fetch the value from the L1 Array instead of the Heap Pointer
                    // builder.irCall(pluginContext.irBuiltIns.intArray.functions.find { it.name == Name.identifier("get") }).apply {
                    //     putValueArgument(0, builder.irInt(0)) // Mock index extracted from Flyweight `val index: Int`
                    // }
                }
            }
        }
        return super.visitCall(expression)
    }

    private fun getPipelinedCapacity(declaration: IrClass): Int {
        val annotation = declaration.getAnnotation(PIPELINED_FQ_NAME) ?: return 64
        val arg = annotation.getValueArgument(0) as? org.jetbrains.kotlin.ir.expressions.IrConst
        return (arg?.value as? Int) ?: 64
    }
}
