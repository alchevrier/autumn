#include "autumn_xdp.h"
#include "xdp_compat.h"
#include <stdlib.h>
#include <stdio.h>
#include <string.h>
#include <unistd.h>
#include <net/if.h>
#include <sys/socket.h>
#include <sys/mman.h>

#define AUTUMN_FRAME_SIZE 2048
#define AUTUMN_RING_SIZE 2048

struct autumn_umem *autumn_configure_umem(void *buffer, size_t size) {
    struct autumn_umem *umem = calloc(1, sizeof(struct autumn_umem));
    if (!umem) return NULL;
    
    umem->buffer = buffer;
    umem->size = size;
    return umem;
}

struct autumn_xsk *autumn_configure_xsk(struct autumn_umem *umem, const char *ifname, int queue_id) {
    struct autumn_xsk *xsk = calloc(1, sizeof(struct autumn_xsk));
    if (!xsk) return NULL;
    
    xsk->umem = umem;
    xsk->fd = socket(AF_XDP, SOCK_RAW, 0);
    if (xsk->fd < 0) {
        perror("socket(AF_XDP)");
        free(xsk);
        return NULL;
    }

    // 1. Register the UMEM memory map to the Kernel
    struct xdp_umem_reg mr = {
        .addr = (unsigned long)umem->buffer,
        .len = umem->size,
        .chunk_size = AUTUMN_FRAME_SIZE,
        .headroom = 0,
        .flags = 0
    };

    if (setsockopt(xsk->fd, SOL_XDP, XDP_UMEM_REG, &mr, sizeof(mr)) < 0) {
        perror("setsockopt(XDP_UMEM_REG)");
        close(xsk->fd); free(xsk); return NULL;
    }

    // 2. Set the Lock-Free Ring Sizes (must be power of 2)
    int fill_size = AUTUMN_RING_SIZE;
    setsockopt(xsk->fd, SOL_XDP, XDP_UMEM_FILL_RING, &fill_size, sizeof(int));
    int comp_size = AUTUMN_RING_SIZE;
    setsockopt(xsk->fd, SOL_XDP, XDP_UMEM_COMPLETION_RING, &comp_size, sizeof(int));
    int rx_size = AUTUMN_RING_SIZE;
    setsockopt(xsk->fd, SOL_XDP, XDP_RX_RING, &rx_size, sizeof(int));
    int tx_size = AUTUMN_RING_SIZE;
    setsockopt(xsk->fd, SOL_XDP, XDP_TX_RING, &tx_size, sizeof(int));

    // 3. Obtain Memory Mapped Offsets to the physical ring pointers
    struct xdp_mmap_offsets off;
    socklen_t optlen = sizeof(off);
    if (getsockopt(xsk->fd, SOL_XDP, XDP_MMAP_OFFSETS, &off, &optlen) < 0) {
        perror("getsockopt(XDP_MMAP_OFFSETS)");
        close(xsk->fd); free(xsk); return NULL;
    }

    // 4. Memory-map the rings directly into our struct pointers
    void *map = mmap(NULL, off.fr.desc + fill_size * sizeof(__u64), 
                     PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE, xsk->fd, XDP_UMEM_PGOFF_FILL_RING);
    umem->fill_ring.producer = map + off.fr.producer;
    umem->fill_ring.consumer = map + off.fr.consumer;
    umem->fill_ring.desc     = map + off.fr.desc;

    map = mmap(NULL, off.cr.desc + comp_size * sizeof(__u64), 
                     PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE, xsk->fd, XDP_UMEM_PGOFF_COMPLETION_RING);
    umem->comp_ring.producer = map + off.cr.producer;
    umem->comp_ring.consumer = map + off.cr.consumer;
    umem->comp_ring.desc     = map + off.cr.desc;

    map = mmap(NULL, off.rx.desc + rx_size * sizeof(struct xdp_desc), 
                     PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE, xsk->fd, XDP_PGOFF_RX_RING);
    xsk->rx_ring.producer = map + off.rx.producer;
    xsk->rx_ring.consumer = map + off.rx.consumer;
    xsk->rx_ring.desc     = map + off.rx.desc;

    map = mmap(NULL, off.tx.desc + tx_size * sizeof(struct xdp_desc), 
                     PROT_READ | PROT_WRITE, MAP_SHARED | MAP_POPULATE, xsk->fd, XDP_PGOFF_TX_RING);
    xsk->tx_ring.producer = map + off.tx.producer;
    xsk->tx_ring.consumer = map + off.tx.consumer;
    xsk->tx_ring.desc     = map + off.tx.desc;

    // 5. Finally bind the socket to the exact physical NIC interface and Queue ID
    struct sockaddr_xdp sxdp = {
        .sxdp_family = PF_XDP,
        .sxdp_ifindex = if_nametoindex(ifname),
        .sxdp_queue_id = queue_id,
        .sxdp_flags = XDP_ZEROCOPY // Native Zero-Copy strictly enforced
    };
    
    if (bind(xsk->fd, (struct sockaddr *)&sxdp, sizeof(sxdp)) < 0) {
        perror("bind(AF_XDP)");
        close(xsk->fd); free(xsk); return NULL;
    }

    return xsk;
}