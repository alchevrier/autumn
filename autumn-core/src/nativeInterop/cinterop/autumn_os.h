#ifndef AUTUMN_OS_H
#define AUTUMN_OS_H

#include <stdint.h>

// Hardware Clock & Pinning Helpers
uint64_t autumn_rdtsc(void);
void autumn_pause(void);
int autumn_pin_to_core(int core_id);

#endif // AUTUMN_OS_H
