import re

with open('autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/TopologySynthesisTransformer.kt', 'r') as f:
    text = f.read()

# To cleanly rewrite the generation block...
