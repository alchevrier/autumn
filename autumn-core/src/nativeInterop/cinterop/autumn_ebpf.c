#include <linux/bpf.h>
#include <bpf/bpf_helpers.h>
#include <linux/if_ether.h>
#include <linux/ip.h>
#include <linux/udp.h>
#include <arpa/inet.h>

// BPF Map to hold AF_XDP socket file descriptors
struct {
    __uint(type, BPF_MAP_TYPE_XSKMAP);
    __uint(max_entries, 64);
    __type(key, int);
    __type(value, int);
} xsks_map SEC(".maps");

SEC("xdp")
int filter_autumn_channel(struct xdp_md *ctx) {
    void *data_end = (void *)(long)ctx->data_end;
    void *data = (void *)(long)ctx->data;

    struct ethhdr *eth = data;
    if ((void *)(eth + 1) > data_end)
        return XDP_PASS;

    if (eth->h_proto != __constant_htons(ETH_P_IP))
        return XDP_PASS;

    struct iphdr *ip = (void *)(eth + 1);
    if ((void *)(ip + 1) > data_end)
        return XDP_PASS;

    if (ip->protocol != IPPROTO_UDP)
        return XDP_PASS;

    struct udphdr *udp = (void *)ip + (ip->ihl * 4);
    if ((void *)(udp + 1) > data_end)
        return XDP_PASS;

    // Example: Target specific UDP Port (e.g., 5000)
    if (udp->dest != __constant_htons(5000))
        return XDP_PASS;

    // Strip Ethernet, IP and UDP headers via bpf_xdp_adjust_head
    int header_size = (void *)(udp + 1) - data;
    if (bpf_xdp_adjust_head(ctx, header_size)) {
        return XDP_DROP;
    }

    // Redirect to AF_XDP socket associated with this RX queue
    return bpf_redirect_map(&xsks_map, ctx->rx_queue_index, XDP_PASS);
}

char _license[] SEC("license") = "GPL";