#!/bin/bash
sed -i 's/if (functionName == "next" && parentClass?.name?.asString() == "AutumnChannel") {/if (functionName == "next" \&\& (parentClass?.name?.asString() == "AutumnChannel" || parentClass?.name?.asString() == "Channel")) {/' autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/PipelinedSoATransformer.kt
