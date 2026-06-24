#ifndef AUTUMN_XDP_H
#define AUTUMN_XDP_H

#include "xdp_compat.h"
#include <sys/mman.h>
#include <sys/socket.h>
#include <stdint.h>
#include <stdlib.h>

// Lock-free ring structures that we map directly to Kotlin CPointer<UIntVar>
struct autumn_ring_prod {
    uint32_t *producer;
    uint32_t *consumer;
    void *desc; // array of __u64 for FILL, or struct xdp_desc for TX
};

struct autumn_ring_cons {
    uint32_t *producer;
    uint32_t *consumer;
    void *desc; // array of __u64 for COMP, or struct xdp_desc for RX
};

// UMEM configuration
struct autumn_umem {
    void *buffer;
    size_t size;
    struct autumn_ring_prod fill_ring;
    struct autumn_ring_cons comp_ring;
};

// XDP Socket configuration
struct autumn_xsk {
    int fd;
    struct autumn_umem *umem;
    struct autumn_ring_prod tx_ring;
    struct autumn_ring_cons rx_ring;
};

// Function prototypes
struct autumn_umem *autumn_configure_umem(void *buffer, size_t size);
struct autumn_xsk *autumn_configure_xsk(struct autumn_umem *umem, const char *ifname, int queue_id);

#endif // AUTUMN_XDP_H