#!/bin/bash
sed -i 's/putValueArgument(0, expression.getValueArgument(0))/\/\/ No arguments for next() or nextIndex()/' autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/PipelinedSoATransformer.kt
