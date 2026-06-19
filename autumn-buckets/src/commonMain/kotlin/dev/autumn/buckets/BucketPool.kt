package dev.autumn.buckets

/**
 * A zero-allocation memory pool for a specific entity type.
 * Ensures strict compliance with architectural constraints by pre-allocating memory at startup.
 */
interface BucketPool<T> {
    /** The maximum number of items this bucket can hold (its strict budget). */
    val capacity: Int
    
    /** Current number of active items in the pool. */
    val size: Int

    /** 
     * Retrieves a zero-allocation view of the item at [index].
     * To prevent allocations, [T] should be a value class wrapping the index or a shared mutable flyweight.
     */
    operator fun get(index: Int): T

    /**
     * Appends a new item to the pool, incrementing the size, and returns the flyweight
     * positioned at the newly allocated slot so fields can be populated.
     */
    fun append(): T

    /** 
     * Resets the pool logic, marking all elements as free.
     */
    fun clear()
}
