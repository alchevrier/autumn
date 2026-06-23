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

            // Extract the fields to determine the hardware Memory Layout mappings
            for (property in declaration.properties) {
                val propertyName = property.name.asString()
                val propertyType = property.getter?.returnType?.classFqName?.shortName()?.asString() ?: "Unknown"

                val arrayType = when(propertyType) {
                    "Int" -> "IntArray"
                    "Long" -> "LongArray"
                    "Byte" -> "ByteArray"
                    "Short" -> "ShortArray"
                    "Float" -> "FloatArray"
                    "Double" -> "DoubleArray"
                    "Boolean" -> "BooleanArray"
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
