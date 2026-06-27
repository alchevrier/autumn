import re

with open('autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/TopologySynthesisTransformer.kt', 'r') as f:
    text = f.read()

# Make a backup
with open('autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/TopologySynthesisTransformer.kt.bak', 'w') as f:
    f.write(text)

