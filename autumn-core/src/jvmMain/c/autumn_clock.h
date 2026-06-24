#ifndef AUTUMN_CLOCK_H
#define AUTUMN_CLOCK_H

#include <stdint.h>

/**
 * Returns the Time Stamp Counter (TSC).
 * In Time-Triggered Architectures, precise measurement of CPU cycles
 * is required to map high-level code directly back to clock schedules.
 */
uint64_t autumn_rdtsc(void);

/**
 * Invokes native Linux `sched_setaffinity` to physically pin the calling 
 * thread to the strictly isolated CPU port/core.
 * Returns 0 on success, or -1 on failure.
 */
int autumn_pin_to_core(int core_id);

#endif // AUTUMN_CLOCK_H
