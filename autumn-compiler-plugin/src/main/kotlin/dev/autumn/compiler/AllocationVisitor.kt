package dev.autumn.compiler

import org.jetbrains.kotlin.backend.common.extensions.IrPluginContext
import org.jetbrains.kotlin.cli.common.messages.CompilerMessageSeverity
import org.jetbrains.kotlin.cli.common.messages.MessageCollector
import org.jetbrains.kotlin.ir.IrElement
import org.jetbrains.kotlin.ir.declarations.IrClass
import org.jetbrains.kotlin.ir.declarations.IrFunction
import org.jetbrains.kotlin.ir.expressions.IrConstructorCall
import org.jetbrains.kotlin.ir.util.hasAnnotation
import org.jetbrains.kotlin.ir.util.parentAsClass
import org.jetbrains.kotlin.ir.visitors.IrElementVisitorVoid
import org.jetbrains.kotlin.ir.visitors.acceptChildrenVoid
import org.jetbrains.kotlin.ir.visitors.acceptVoid
import org.jetbrains.kotlin.name.FqName

class AllocationVisitor(
    private val pluginContext: IrPluginContext,
    private val messageCollector: MessageCollector
) : IrElementVisitorVoid {

    companion object {
        val LONG_LIVED_FQ_NAME = FqName("dev.autumn.annotations.LongLived")
    }

    // Keep track of the current enclosing function/class to check annotations.
    private val declarationStack = mutableListOf<IrElement>()

    override fun visitElement(element: IrElement) {
        element.acceptChildrenVoid(this)
    }

    override fun visitClass(declaration: IrClass) {
        declarationStack.add(declaration)
        super.visitClass(declaration)
        declarationStack.removeLast()
    }

    override fun visitFunction(declaration: IrFunction) {
        declarationStack.add(declaration)
        super.visitFunction(declaration)
        declarationStack.removeLast()
    }

    override fun visitConstructorCall(expression: IrConstructorCall) {
        super.visitConstructorCall(expression)

        // 1. Is this constructor call allowed because we're in a LongLived context?
        val isAllowed = declarationStack.any { element ->
            when (element) {
                is IrClass -> element.hasAnnotation(LONG_LIVED_FQ_NAME)
                is IrFunction -> element.hasAnnotation(LONG_LIVED_FQ_NAME)
                else -> false
            }
        }

        if (!isAllowed) {
            // Note: In real life we'd filter out basic arrays/strings/primitive wrappers if needed.
            // We'd also check if the type being instantiated is not a primitive.
            val type = expression.type
            messageCollector.report(
                CompilerMessageSeverity.WARNING,
                "Heap allocation detected in strict zero-allocation scope: ${type}. Annotate with @LongLived if this is a safe long-lived allocation."
                // Location reporting requires `MessageUtil` and Source offsets. 
            )
        }
    }
}
