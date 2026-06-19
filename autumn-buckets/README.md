# autumn-buckets

Bucket abstraction for Autumn. This module defines how state references documents and images without binding the app to a specific storage provider.

## Pointer-Free Datasets

All bucket domain data internally falls perfectly in line with the Circuit-Based paradigm:
Everything is modeled flatly via pre-allocated arrays accessed by integer index and byte offset. There are no object graphs, no pointer chains, and no GC-visible references between data items. 

Memory locations inside the buckets are resolved implicitly using the boundaries mapped inside `autumn-config`, eliminating dynamic runtime allocation completely payload to payload.
