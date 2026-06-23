#!/bin/bash
cat << 'KOTLIN' > test.kt
import org.jetbrains.kotlin.fir.expressions.*
fun test(ann: FirAnnotationCall) {
    println(ann.argumentList.arguments)
}
KOTLIN
