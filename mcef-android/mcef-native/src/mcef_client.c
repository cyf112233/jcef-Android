#define _POSIX_C_SOURCE 200809L

#include "mcef_proto.h"

#include <arpa/inet.h>
#include <errno.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <pthread.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <sys/socket.h>
#include <time.h>
#include <unistd.h>

struct mcef_session {
    int              fd;
    volatile int     open;
    pthread_t        rx;
    pthread_mutex_t  tx;
    int              next_id;
    void            *user;        /* 上层句柄，回调时原样带回 Java */
    uint8_t         *frame;       /* 复用的整帧缓冲 */
    size_t           frame_cap;
};

/* ---------- 小工具：写缓冲 ---------- */

static void buf_need(mcef_buf *b, size_t n) {
    if (b->len + n <= b->cap) return;
    size_t cap = b->cap ? b->cap : 256;
    while (cap < b->len + n) cap <<= 1;
    uint8_t *p = realloc(b->data, cap);
    if (!p) abort();
    b->data = p;
    b->cap = cap;
}

void mcef_buf_init(mcef_buf *b) { b->data = NULL; b->len = 0; b->cap = 0; }
void mcef_buf_free(mcef_buf *b) { free(b->data); mcef_buf_init(b); }

void mcef_buf_u8(mcef_buf *b, uint8_t v) { buf_need(b, 1); b->data[b->len++] = v; }

void mcef_buf_u16(mcef_buf *b, uint16_t v) {
    buf_need(b, 2);
    b->data[b->len++] = (uint8_t)(v);
    b->data[b->len++] = (uint8_t)(v >> 8);
}

void mcef_buf_u32(mcef_buf *b, uint32_t v) {
    buf_need(b, 4);
    for (int i = 0; i < 4; i++) b->data[b->len++] = (uint8_t)(v >> (8 * i));
}

void mcef_buf_i32(mcef_buf *b, int32_t v) { mcef_buf_u32(b, (uint32_t)v); }

void mcef_buf_i64(mcef_buf *b, int64_t v) {
    buf_need(b, 8);
    for (int i = 0; i < 8; i++) b->data[b->len++] = (uint8_t)((uint64_t)v >> (8 * i));
}

void mcef_buf_f32(mcef_buf *b, float v) {
    uint32_t u;
    memcpy(&u, &v, 4);
    mcef_buf_u32(b, u);
}

void mcef_buf_f64(mcef_buf *b, double v) {
    uint64_t u;
    memcpy(&u, &v, 8);
    mcef_buf_i64(b, (int64_t)u);
}

void mcef_buf_str(mcef_buf *b, const char *s) {
    if (!s) s = "";
    size_t n = strlen(s);
    mcef_buf_u32(b, (uint32_t)n);
    buf_need(b, n);
    memcpy(b->data + b->len, s, n);
    b->len += n;
}

/* ---------- 小工具：读游标 ---------- */

static int rd_avail(mcef_reader *r, size_t n) { return r->pos + n <= r->len; }

uint8_t mcef_rd_u8(mcef_reader *r) {
    if (!rd_avail(r, 1)) return 0;
    return r->data[r->pos++];
}

uint16_t mcef_rd_u16(mcef_reader *r) {
    if (!rd_avail(r, 2)) return 0;
    uint16_t v = (uint16_t)(r->data[r->pos] | (r->data[r->pos + 1] << 8));
    r->pos += 2;
    return v;
}

uint32_t mcef_rd_u32(mcef_reader *r) {
    if (!rd_avail(r, 4)) return 0;
    uint32_t v = 0;
    for (int i = 0; i < 4; i++) v |= (uint32_t)r->data[r->pos + i] << (8 * i);
    r->pos += 4;
    return v;
}

int32_t mcef_rd_i32(mcef_reader *r) { return (int32_t)mcef_rd_u32(r); }

int64_t mcef_rd_i64(mcef_reader *r) {
    if (!rd_avail(r, 8)) return 0;
    uint64_t v = 0;
    for (int i = 0; i < 8; i++) v |= (uint64_t)r->data[r->pos + i] << (8 * i);
    r->pos += 8;
    return (int64_t)v;
}

float mcef_rd_f32(mcef_reader *r) {
    uint32_t u = mcef_rd_u32(r);
    float f;
    memcpy(&f, &u, 4);
    return f;
}

char *mcef_rd_str(mcef_reader *r) {
    uint32_t n = mcef_rd_u32(r);
    if (!rd_avail(r, n)) return NULL;
    char *s = malloc((size_t)n + 1);
    if (!s) return NULL;
    memcpy(s, r->data + r->pos, n);
    s[n] = 0;
    r->pos += n;
    return s;
}

/* ---------- 连接 ---------- */

static int write_all(int fd, const void *buf, size_t n) {
    const uint8_t *p = buf;
    while (n) {
        ssize_t w = send(fd, p, n, MSG_NOSIGNAL);
        if (w <= 0) {
            if (errno == EINTR) continue;
            return -1;
        }
        p += w;
        n -= (size_t)w;
    }
    return 0;
}

static int read_all(int fd, void *buf, size_t n) {
    uint8_t *p = buf;
    while (n) {
        ssize_t r = recv(fd, p, n, 0);
        if (r <= 0) {
            if (r < 0 && errno == EINTR) continue;
            return -1;
        }
        p += r;
        n -= (size_t)r;
    }
    return 0;
}

int mcef_send(mcef_session *s, uint16_t type, uint16_t flags, uint32_t id, const mcef_buf *payload) {
    if (!s || !s->open) return MCEF_ERR_CLOSED;
    uint32_t len = payload ? (uint32_t)payload->len : 0;
    uint8_t hdr[12];
    hdr[0] = (uint8_t)len; hdr[1] = (uint8_t)(len >> 8);
    hdr[2] = (uint8_t)(len >> 16); hdr[3] = (uint8_t)(len >> 24);
    hdr[4] = (uint8_t)type; hdr[5] = (uint8_t)(type >> 8);
    hdr[6] = (uint8_t)flags; hdr[7] = (uint8_t)(flags >> 8);
    hdr[8] = (uint8_t)id; hdr[9] = (uint8_t)(id >> 8);
    hdr[10] = (uint8_t)(id >> 16); hdr[11] = (uint8_t)(id >> 24);

    pthread_mutex_lock(&s->tx);
    int rc = write_all(s->fd, hdr, sizeof hdr);
    if (rc == 0 && len) rc = write_all(s->fd, payload->data, len);
    pthread_mutex_unlock(&s->tx);

    if (rc != 0) {
        s->open = 0;
        return MCEF_ERR_CLOSED;
    }
    return MCEF_OK;
}

int mcef_next_id(mcef_session *s) { return __atomic_fetch_add(&s->next_id, 1, __ATOMIC_RELAXED); }

void  mcef_set_user(mcef_session *s, void *u) { s->user = u; }
void *mcef_get_user(mcef_session *s) { return s->user; }

/*
 * 整帧复用缓冲。Cefrium 的 onPaint 是整帧回调，没有 dirty rect，
 * 每帧 3~4MB；不复用的话 rx 线程会疯狂 malloc/free。
 * 返回的指针在下次调用前有效，供 NewDirectByteBuffer 零拷贝包给 Java。
 */
uint8_t *mcef_frame_scratch(mcef_session *s, size_t need) {
    if (need > s->frame_cap) {
        size_t cap = s->frame_cap ? s->frame_cap : 1 << 20;
        while (cap < need) cap <<= 1;
        uint8_t *p = realloc(s->frame, cap);
        if (!p) return NULL;
        s->frame = p;
        s->frame_cap = cap;
    }
    return s->frame;
}
int mcef_is_open(mcef_session *s) { return s && s->open; }

static void *rx_main(void *arg) {
    mcef_session *s = arg;
    for (;;) {
        uint8_t hdr[12];
        if (read_all(s->fd, hdr, sizeof hdr) != 0) break;

        uint32_t len = (uint32_t)hdr[0] | ((uint32_t)hdr[1] << 8) |
                       ((uint32_t)hdr[2] << 16) | ((uint32_t)hdr[3] << 24);
        uint16_t type = (uint16_t)(hdr[4] | (hdr[5] << 8));
        uint32_t id = (uint32_t)hdr[8] | ((uint32_t)hdr[9] << 8) |
                      ((uint32_t)hdr[10] << 16) | ((uint32_t)hdr[11] << 24);

        /* 1MB 是控制帧的上限；画面帧会更大，单独放开到 64MB */
        if (len > (64u << 20)) break;

        uint8_t *payload = NULL;
        if (len) {
            payload = malloc(len);
            if (!payload) break;
            if (read_all(s->fd, payload, len) != 0) {
                free(payload);
                break;
            }
        }

        mcef_reader r = { payload, len, 0 };
        int32_t err = MCEF_OK;
        mcef_dispatch(s, type, id, &r, &err);
        free(payload);

        if ((hdr[6] & 1) && mcef_is_open(s)) {
            mcef_buf ack;
            mcef_buf_init(&ack);
            mcef_buf_u16(&ack, (uint16_t)err);
            mcef_send(s, EV_ACK, 0, id, &ack);
            mcef_buf_free(&ack);
        }
    }

    s->open = 0;
    shutdown(s->fd, SHUT_RDWR);
    mcef_dispatch(s, EV_DISCONNECTED, 0, &(mcef_reader){ NULL, 0, 0 }, &(int32_t){ 0 });
    return NULL;
}

mcef_session *mcef_connect(void) {
    int fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
    if (fd < 0) return NULL;

    struct sockaddr_in addr;
    memset(&addr, 0, sizeof addr);
    addr.sin_family = AF_INET;
    addr.sin_port = htons(MCEF_PORT);
    addr.sin_addr.s_addr = htonl(INADDR_LOOPBACK);

    /* host 可能还在冷启动，最多等 3 秒 */
    int ok = -1;
    for (int i = 0; i < 30; i++) {
        if (connect(fd, (struct sockaddr *)&addr, sizeof addr) == 0) { ok = 0; break; }
        struct timespec ts = { 0, 100 * 1000 * 1000 };
        nanosleep(&ts, NULL);
        fd = socket(AF_INET, SOCK_STREAM | SOCK_CLOEXEC, 0);
        if (fd < 0) return NULL;
    }
    if (ok != 0) { close(fd); return NULL; }

    int one = 1;
    setsockopt(fd, IPPROTO_TCP, TCP_NODELAY, &one, sizeof one);

    uint8_t hello[8] = { 'M', 'C', 'E', 'F', MCEF_VERSION & 0xff, 0, 0, 0 };
    uint8_t reply[8];
    if (write_all(fd, hello, 8) != 0 || read_all(fd, reply, 8) != 0 ||
        memcmp(reply, "MCEF", 4) != 0 || reply[6] != 0) {
        close(fd);
        return NULL;
    }

    mcef_session *s = calloc(1, sizeof *s);
    if (!s) { close(fd); return NULL; }
    s->fd = fd;
    s->open = 1;
    s->next_id = 1;
    pthread_mutex_init(&s->tx, NULL);
    if (pthread_create(&s->rx, NULL, rx_main, s) != 0) {
        close(fd);
        free(s);
        return NULL;
    }
    return s;
}

void mcef_close(mcef_session *s) {
    if (!s) return;
    s->open = 0;
    shutdown(s->fd, SHUT_RDWR);
    close(s->fd);
    pthread_join(s->rx, NULL);
    pthread_mutex_destroy(&s->tx);
    free(s->frame);
    free(s);
}
