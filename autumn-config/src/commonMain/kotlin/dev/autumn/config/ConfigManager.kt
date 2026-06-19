package dev.autumn.config

import dev.autumn.annotations.LongLived
import dev.autumn.buckets.ByteArrayBucketPool
import dev.autumn.buckets.SbeDecoder

// Following ADR-0002 Resource definition:
// type, path, action are modeled as indices/pointers rather than full strings on the hotpath.
class ResourceDecoder : SbeDecoder() {
    private val OFFSET_TYPE_ID = 0
    private val OFFSET_PATH_REF = 4
    private val OFFSET_ACTION_ID = 8

    var typeId: Int
        get() = readInt(OFFSET_TYPE_ID)
        set(value) = writeInt(OFFSET_TYPE_ID, value)

    var pathRefId: Int
        get() = readInt(OFFSET_PATH_REF)
        set(value) = writeInt(OFFSET_PATH_REF, value)

    var actionId: Int
        get() = readInt(OFFSET_ACTION_ID)
        set(value) = writeInt(OFFSET_ACTION_ID, value)
}

@LongLived
class ResourceBucketPool(capacity: Int) : ByteArrayBucketPool<ResourceDecoder>(
    capacity = capacity,
    recordSizeInBytes = 12,
    flyweight = ResourceDecoder()
)

@LongLived
class ConfigManager(
    val maxResources: Int
) {
    // Zero-allocation resource storage
    val resources = ResourceBucketPool(maxResources)
    
    // Instead of holding strings in the heap, we would theoretically have a String -> Int id registry
    // But for the config resolution loop, we'll expose a fast int-based api

    fun clear() {
        resources.clear()
    }

    /**
     * "Parses" config values into the fixed bucket structure.
     * In a real implementation this would unmarshal binary chunked JSON directly into the pool.
     */
    fun defineResource(typeId: Int, pathRefId: Int, actionId: Int) {
        resources.append().apply {
            this.typeId = typeId
            this.pathRefId = pathRefId
            this.actionId = actionId
        }
    }

    fun getResourceAction(resourceIndex: Int): Int {
        return resources[resourceIndex].actionId
    }
}
