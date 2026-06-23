import org.jetbrains.kotlin.fir.expressions.FirAnnotation
import org.jetbrains.kotlin.fir.expressions.arguments

fun test(ann: FirAnnotation) {
    println(ann.argumentMapping)
}
