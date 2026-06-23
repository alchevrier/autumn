package dev.autumn.compiler.fir

import org.jetbrains.kotlin.fir.FirSession
import org.jetbrains.kotlin.fir.declarations.FirClassLikeDeclaration
import org.jetbrains.kotlin.fir.extensions.FirDeclarationGenerationExtension
import org.jetbrains.kotlin.fir.symbols.impl.FirClassLikeSymbol
import org.jetbrains.kotlin.name.ClassId
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.fir.symbols.impl.FirClassSymbol
import org.jetbrains.kotlin.fir.extensions.DeclarationGenerationContext
import org.jetbrains.kotlin.fir.symbols.impl.FirPropertySymbol

/**
 * Generates the Zero-Allocation Flyweight `@JvmInline value class` for any interface
 * annotated with `@Pipelined`.
 * 
 * E.g., for `interface OrderEvent`, this generates:
 * ```
 * @JvmInline
 * value class OrderEventFlyweight(val index: Int) : OrderEvent {
 *     override var quantity: Int // FIR defers to IR for implementation
 *     override var side: Byte    // FIR defers to IR for implementation
 * }
 * ```
 */
class FlyweightDeclarationGenerator(session: FirSession) : FirDeclarationGenerationExtension(session) {

    override fun generateNestedClassLikeDeclaration(
        owner: FirClassSymbol<*>,
        name: Name,
        context: DeclarationGenerationContext.Nested
    ): FirClassLikeSymbol<*>? {
        // Here we would use FirRegularClassBuilder to construct the Flyweight value class
        // and its `val index: Int` primary constructor. 
        return super.generateNestedClassLikeDeclaration(owner, name, context)
    }

    override fun generateProperties(
        callableId: org.jetbrains.kotlin.name.CallableId,
        context: DeclarationGenerationContext.Member?
    ): List<FirPropertySymbol> {
        // Here we generate the overridden properties matching the `@Pipelined` interface.
        return emptyList()
    }
}
