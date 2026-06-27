import re

with open('autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/PipelinedSoATransformer.kt', 'r') as f:
    text = f.read()

import re
text = re.sub(r'                    messageCollector\.report\(CompilerMessageSeverity\.WARNING, "DEBUG SETTER OWNER[^\n]+', '', text)
text = re.sub(r'                messageCollector\.report\(CompilerMessageSeverity\.WARNING, "DEBUG TYPE:[^\n]+', '', text)

with open('autumn-compiler-plugin/src/main/kotlin/dev/autumn/compiler/PipelinedSoATransformer.kt', 'w') as f:
    f.write(text)

