/*
 * Copyright (C) NGINX, Inc.
 *
 * Vendored from NGINX Unit (https://unit.nginx.org/).
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Standalone implementation of the nxt_unit_* API backed by an in-process
 * HTTP/1 server. Same API as libunit so snunit's Scala API works unchanged.
 * Listen address/port: passed to nxt_unit_init(init, host, port).
 */

#define _POSIX_C_SOURCE 200809L

#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <stddef.h>
#include <stdint.h>
#include <stdarg.h>
#include <errno.h>
#include <fcntl.h>
#include <unistd.h>
#include <sys/socket.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <arpa/inet.h>
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
#include <pthread.h>
#endif
#include <strings.h>

/* Prefer epoll (Linux) and kqueue (BSD/macOS) over poll for efficiency. */
#if defined(__linux__)
#define EMB_USE_EPOLL  1
#include <sys/epoll.h>
#elif defined(__APPLE__) || defined(__FreeBSD__) || defined(__OpenBSD__) || defined(__NetBSD__) || defined(__DragonFly__)
#define EMB_USE_KQUEUE 1
#include <sys/event.h>
/* One write event per EV_ENABLE; avoids spin when combining apply+wait in one kevent() (unit uses EV_DISPATCH on macOS). */
#ifdef EV_DISPATCH
#define EMB_KQ_WRITE_ONESHOT  EV_DISPATCH
#else
#define EMB_KQ_WRITE_ONESHOT  EV_ONESHOT
#endif
#else
#define EMB_USE_POLL   1
#include <poll.h>
#endif

#include "nxt_unit.h"
#include "nxt_unit_websocket.h"

/* Minimal SHA-1 for WebSocket accept key (RFC 6455). Public domain. */
#define SHA1_ROTL32(x, n) (((x) << (n)) | ((x) >> (32 - (n))))
static void sha1_block(uint32_t *h, const unsigned char *block) {
    uint32_t w[80], a, b, c, d, e, t, f, k;
    size_t j;
    for (j = 0; j < 16; j++)
        w[j] = (uint32_t) block[j*4] << 24 | (uint32_t) block[j*4+1] << 16 | (uint32_t) block[j*4+2] << 8 | block[j*4+3];
    for (j = 16; j < 80; j++)
        w[j] = SHA1_ROTL32(w[j-3] ^ w[j-8] ^ w[j-14] ^ w[j-16], 1);
    a = h[0]; b = h[1]; c = h[2]; d = h[3]; e = h[4];
    for (j = 0; j < 80; j++) {
        if (j < 20) { f = (b & c) | ((~b) & d); k = 0x5A827999U; }
        else if (j < 40) { f = b ^ c ^ d; k = 0x6ED9EBA1U; }
        else if (j < 60) { f = (b & c) | (b & d) | (c & d); k = 0x8F1BBCDCU; }
        else { f = b ^ c ^ d; k = 0xCA62C1D6U; }
        t = SHA1_ROTL32(a, 5) + f + e + k + w[j];
        e = d; d = c; c = SHA1_ROTL32(b, 30); b = a; a = t;
    }
    h[0] += a; h[1] += b; h[2] += c; h[3] += d; h[4] += e;
}
static void sha1_hash(const unsigned char *data, size_t len, unsigned char *out) {
    uint32_t h[5] = { 0x67452301U, 0xEFCDAB89U, 0x98BADCFEU, 0x10325476U, 0xC3D2E1F0U };
    unsigned char block[128];
    size_t i, rem;
    uint64_t bitlen = (uint64_t) len * 8;
    for (i = 0; i + 64 <= len; i += 64)
        sha1_block(h, data + i);
    rem = len - i;
    memcpy(block, data + i, rem);
    block[rem] = 0x80;
    memset(block + rem + 1, 0, 64 - rem - 1);
    if (rem >= 56) {
        sha1_block(h, block);
        memset(block, 0, 56);
    }
    block[56] = (unsigned char)(bitlen >> 56); block[57] = (unsigned char)(bitlen >> 48);
    block[58] = (unsigned char)(bitlen >> 40); block[59] = (unsigned char)(bitlen >> 32);
    block[60] = (unsigned char)(bitlen >> 24); block[61] = (unsigned char)(bitlen >> 16);
    block[62] = (unsigned char)(bitlen >> 8);  block[63] = (unsigned char)bitlen;
    sha1_block(h, block);
    out[0] = (unsigned char)(h[0] >> 24); out[1] = (unsigned char)(h[0] >> 16); out[2] = (unsigned char)(h[0] >> 8); out[3] = (unsigned char)h[0];
    out[4] = (unsigned char)(h[1] >> 24); out[5] = (unsigned char)(h[1] >> 16); out[6] = (unsigned char)(h[1] >> 8); out[7] = (unsigned char)h[1];
    out[8] = (unsigned char)(h[2] >> 24); out[9] = (unsigned char)(h[2] >> 16); out[10] = (unsigned char)(h[2] >> 8); out[11] = (unsigned char)h[2];
    out[12] = (unsigned char)(h[3] >> 24); out[13] = (unsigned char)(h[3] >> 16); out[14] = (unsigned char)(h[3] >> 8); out[15] = (unsigned char)h[3];
    out[16] = (unsigned char)(h[4] >> 24); out[17] = (unsigned char)(h[4] >> 16); out[18] = (unsigned char)(h[4] >> 8); out[19] = (unsigned char)h[4];
}

static const char b64[] = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789+/";
static void base64_encode(const unsigned char *in, size_t inlen, char *out, size_t outcap) {
    size_t i, j = 0;
    for (i = 0; i + 3 <= inlen && j + 4 <= outcap; i += 3, j += 4) {
        out[j] = b64[in[i] >> 2];
        out[j+1] = b64[((in[i] & 3) << 4) | (in[i+1] >> 4)];
        out[j+2] = b64[((in[i+1] & 15) << 2) | (in[i+2] >> 6)];
        out[j+3] = b64[in[i+2] & 63];
    }
    if (i < inlen && j + 4 <= outcap) {
        out[j] = b64[in[i] >> 2];
        if (inlen - i == 1) { out[j+1] = b64[(in[i] & 3) << 4]; out[j+2] = '='; out[j+3] = '='; }
        else { out[j+1] = b64[((in[i] & 3) << 4) | (in[i+1] >> 4)]; out[j+2] = b64[(in[i+1] & 15) << 2]; out[j+3] = '='; }
    }
    out[j < outcap ? j : outcap - 1] = '\0';
}

/* --- Embed context --- */
typedef struct {
    nxt_unit_t      unit;
    nxt_unit_ctx_t  ctx;
    nxt_unit_init_t *init;
    int             listen_fd;
    int             port;
    char            listen_addr[64];
    int             quit;
    int             ev_fd;  /* epoll fd (Linux) or kqueue fd (BSD/macOS); -1 when not running */
#if EMB_USE_KQUEUE
    struct kevent   *kq_changes;   /* batched changes; applied in main loop kevent() */
    int             kq_nchanges;
    int             kq_mchanges;
#endif
} embed_ctx_t;

/* Per-connection state */
#define RECV_BUF_SIZE  65536
#define SEND_BUF_SIZE  65536
/* Cap read/write syscalls per wakeup to rebalance kevent vs read/write and improve fairness. */
#define EMB_DRAIN_MAX_READ  4
#define EMB_DRAIN_MAX_WRITE 4
#define REQ_POOL_SIZE  32768
/* Reserve space for response (response struct + fields + header name/value strings). Request parsing must not use this. */
#define RESPONSE_POOL_RESERVE  8192
#define REQ_POOL_MAX  (REQ_POOL_SIZE - RESPONSE_POOL_RESERVE)

typedef struct conn conn_t;
struct conn {
    conn_t      *next;
    conn_t     **prev;   /* & of the pointer that points to this node; O(1) unlink (from nxt_unit_mmap_buf); NULL when removed */
    int         fd;
    char        recv_buf[RECV_BUF_SIZE];
    size_t      recv_len;
    size_t      recv_parsed;
    size_t      headers_end;  /* End of headers when request was parsed (before body reads) */
    char        send_buf[SEND_BUF_SIZE];
    size_t      send_len;
    int         request_ready;
    int         response_sent;
    embed_ctx_t *emb;
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
    /* Sync when handler runs on another thread (e.g. BlockingHandler): main thread waits until handler calls response_send or request_done. */
    pthread_mutex_t         dispatch_mutex;
    pthread_cond_t          dispatch_cond;
    int                     handler_done;
#endif
    /* Request/response structs point into req_pool */
    nxt_unit_request_info_t *req_info;
    nxt_unit_request_t      *request;
    nxt_unit_response_t      *response;
    nxt_unit_buf_t           request_buf;
    nxt_unit_buf_t           response_buf;
    nxt_unit_buf_t           content_buf;
    char                     req_pool[REQ_POOL_SIZE];
    size_t                   req_pool_used;
    int                      is_websocket;
    /* WebSocket frame state (when is_websocket) */
    nxt_websocket_header_t   ws_header;
    nxt_unit_websocket_frame_t ws_frame;
    size_t                   ws_payload_off;   /* offset in recv_buf */
    size_t                   ws_payload_len;
    size_t                   ws_frame_size;    /* total frame bytes for websocket_done */
    uint8_t                  ws_mask[4];
};

static embed_ctx_t *global_emb;

static conn_t *conn_new(int fd, embed_ctx_t *emb);
static void conn_free(conn_t *c);
/* O(1) insert/unlink adapted from nxt_unit_mmap_buf_insert/nxt_unit_mmap_buf_unlink (nxt_unit.c) */
static inline void conn_insert(conn_t **head, conn_t *c) {
    c->next = *head;
    if (c->next != NULL) c->next->prev = &c->next;
    *head = c;
    c->prev = head;
}
static inline void conn_unlink(conn_t *c) {
    conn_t **p = c->prev;
    if (c->next != NULL) c->next->prev = p;
    if (p != NULL) *p = c->next;
}
static int conn_parse_request(conn_t *c);
static int conn_dispatch_request(conn_t *c);
static int conn_send_response(conn_t *c);
static int conn_parse_websocket_frames(embed_ctx_t *emb, conn_t *c);
static uint16_t field_hash(const char *name, size_t len);

#if EMB_USE_EPOLL
static int embed_ev_add_listen(embed_ctx_t *emb);
static int embed_ev_add_conn(embed_ctx_t *emb, conn_t *c);
static void embed_ev_remove_conn(embed_ctx_t *emb, conn_t *c);
static void embed_ev_want_write(embed_ctx_t *emb, conn_t *c, int want);
static void embed_ev_rearm_conn(embed_ctx_t *emb, conn_t *c);
#elif EMB_USE_KQUEUE
/* Append one change to kq_changes; flush if full (like unit's nxt_kqueue_get_kevent). */
static struct kevent *embed_kq_change(embed_ctx_t *emb);
static void embed_kq_flush(embed_ctx_t *emb);
static int embed_ev_add_listen(embed_ctx_t *emb);
static int embed_ev_add_conn(embed_ctx_t *emb, conn_t *c);
static void embed_ev_remove_conn(embed_ctx_t *emb, conn_t *c);
static void embed_ev_want_write(embed_ctx_t *emb, conn_t *c, int want);
#endif

#if EMB_USE_EPOLL
/* Listen fd: level-triggered (like kqueue listen fd without EV_CLEAR). */
static int embed_ev_add_listen(embed_ctx_t *emb) {
    struct epoll_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.events = EPOLLIN;
    ev.data.ptr = NULL;  /* listen fd identified by ptr == NULL */
    return epoll_ctl(emb->ev_fd, EPOLL_CTL_ADD, emb->listen_fd, &ev);
}
/* Connection fd: edge-triggered + oneshot (align with kqueue EV_CLEAR + EMB_KQ_WRITE_ONESHOT). */
static int embed_ev_add_conn(embed_ctx_t *emb, conn_t *c) {
    struct epoll_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.events = EPOLLIN | EPOLLET | EPOLLONESHOT;
    if (c->send_len > 0) ev.events |= EPOLLOUT;
    ev.data.ptr = c;
    return epoll_ctl(emb->ev_fd, EPOLL_CTL_ADD, c->fd, &ev);
}
static void embed_ev_remove_conn(embed_ctx_t *emb, conn_t *c) {
    (void) emb;
    epoll_ctl(emb->ev_fd, EPOLL_CTL_DEL, c->fd, NULL);
}
static void embed_ev_want_write(embed_ctx_t *emb, conn_t *c, int want) {
    struct epoll_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.events = EPOLLIN | EPOLLET | EPOLLONESHOT | (want ? EPOLLOUT : 0);
    ev.data.ptr = c;
    epoll_ctl(emb->ev_fd, EPOLL_CTL_MOD, c->fd, &ev);
}
/* Re-arm after processing (EPOLLONESHOT disables fd after one event; match kqueue one-event-per-enable). */
static void embed_ev_rearm_conn(embed_ctx_t *emb, conn_t *c) {
    struct epoll_event ev;
    memset(&ev, 0, sizeof(ev));
    ev.events = EPOLLIN | EPOLLET | EPOLLONESHOT;
    if (c->send_len > 0) ev.events |= EPOLLOUT;
    ev.data.ptr = c;
    epoll_ctl(emb->ev_fd, EPOLL_CTL_MOD, c->fd, &ev);
}
#elif EMB_USE_KQUEUE
static struct kevent *embed_kq_change(embed_ctx_t *emb) {
    struct kevent *kev;
    if (emb->kq_nchanges >= emb->kq_mchanges) {
        (void) kevent(emb->ev_fd, emb->kq_changes, emb->kq_nchanges, NULL, 0, NULL);
        emb->kq_nchanges = 0;
    }
    kev = &emb->kq_changes[emb->kq_nchanges];
    emb->kq_nchanges++;
    return kev;
}
static int embed_ev_add_listen(embed_ctx_t *emb) {
    struct kevent *kev = embed_kq_change(emb);
    EV_SET(kev, emb->listen_fd, EVFILT_READ, EV_ADD | EV_ENABLE, 0, 0, NULL);
    return 0;
}
/* EV_CLEAR: reset after retrieval. EMB_KQ_WRITE_ONESHOT: one write event per enable so single kevent(apply+wait) doesn't spin. */
static int embed_ev_add_conn(embed_ctx_t *emb, conn_t *c) {
    struct kevent *kev;
    kev = embed_kq_change(emb);
    EV_SET(kev, c->fd, EVFILT_READ, EV_ADD | EV_ENABLE | EV_CLEAR, 0, 0, c);
    kev = embed_kq_change(emb);
    EV_SET(kev, c->fd, EVFILT_WRITE, EV_ADD | (c->send_len > 0 ? EV_ENABLE : EV_DISABLE) | EV_CLEAR | EMB_KQ_WRITE_ONESHOT, 0, 0, c);
    return 0;
}
static void embed_ev_remove_conn(embed_ctx_t *emb, conn_t *c) {
    struct kevent *kev;
    kev = embed_kq_change(emb);
    EV_SET(kev, c->fd, EVFILT_READ, EV_DELETE, 0, 0, NULL);
    kev = embed_kq_change(emb);
    EV_SET(kev, c->fd, EVFILT_WRITE, EV_DELETE, 0, 0, NULL);
}
/* Apply pending kq_changes before closing fd; otherwise kevent() gets EBADF. */
static void embed_kq_flush(embed_ctx_t *emb) {
    if (emb->kq_nchanges > 0) {
        (void) kevent(emb->ev_fd, emb->kq_changes, emb->kq_nchanges, NULL, 0, NULL);
        emb->kq_nchanges = 0;
    }
}
static void embed_ev_want_write(embed_ctx_t *emb, conn_t *c, int want) {
    struct kevent *kev = embed_kq_change(emb);
    if (want)
        EV_SET(kev, c->fd, EVFILT_WRITE, EV_ADD | EV_ENABLE | EV_CLEAR | EMB_KQ_WRITE_ONESHOT, 0, 0, c);
    else
        EV_SET(kev, c->fd, EVFILT_WRITE, EV_DISABLE, 0, 0, c);
}
#endif

/* --- nxt_unit_init --- */
nxt_unit_ctx_t *nxt_unit_init(nxt_unit_init_t *init, const char *host, int port) {
    embed_ctx_t *emb;
    int fd, opt = 1;
    struct sockaddr_in sa;

    if (nxt_slow_path(init == NULL)) {
        fprintf(stderr, "nxt_unit_embed: init is NULL\n");
        fflush(stderr);
        return NULL;
    }
    if (nxt_slow_path(host == NULL || host[0] == '\0')) {
        fprintf(stderr, "nxt_unit_embed: host is NULL or empty\n");
        fflush(stderr);
        return NULL;
    }

    emb = (embed_ctx_t *) calloc(1, sizeof(embed_ctx_t));
    if (nxt_slow_path(emb == NULL)) {
        fprintf(stderr, "nxt_unit_embed: calloc failed\n");
        fflush(stderr);
        return NULL;
    }
    emb->listen_fd = -1;
    emb->ev_fd = -1;

    emb->unit.data = init->data;
    emb->ctx.data = init->ctx_data;
    emb->ctx.unit = &emb->unit;
    emb->init = init;

    strncpy(emb->listen_addr, host, sizeof(emb->listen_addr) - 1);
    emb->listen_addr[sizeof(emb->listen_addr) - 1] = '\0';
    emb->port = (port > 0) ? port : 8080;

    fd = socket(AF_INET, SOCK_STREAM, 0);
    if (nxt_slow_path(fd < 0)) {
        fprintf(stderr, "nxt_unit_embed: socket() failed: %s\n", strerror(errno));
        fflush(stderr);
        free(emb);
        return NULL;
    }
    setsockopt(fd, SOL_SOCKET, SO_REUSEADDR, &opt, sizeof(opt));
    memset(&sa, 0, sizeof(sa));
    sa.sin_family = AF_INET;
    sa.sin_port = htons((uint16_t) emb->port);
    if (strcmp(emb->listen_addr, "0.0.0.0") == 0)
        sa.sin_addr.s_addr = INADDR_ANY;
    else
        inet_pton(AF_INET, emb->listen_addr, &sa.sin_addr);
    if (nxt_slow_path(bind(fd, (struct sockaddr *) &sa, sizeof(sa)) != 0)) {
        fprintf(stderr, "nxt_unit_embed: bind(%s:%d) failed: %s\n",
                emb->listen_addr, emb->port, strerror(errno));
        fflush(stderr);
        close(fd);
        free(emb);
        return NULL;
    }
    if (nxt_slow_path(listen(fd, 128) != 0)) {
        fprintf(stderr, "nxt_unit_embed: listen() failed: %s\n", strerror(errno));
        fflush(stderr);
        close(fd);
        free(emb);
        return NULL;
    }
    if (nxt_slow_path(fcntl(fd, F_SETFL, O_NONBLOCK) < 0)) {
        fprintf(stderr, "nxt_unit_embed: fcntl(O_NONBLOCK) failed: %s\n", strerror(errno));
        fflush(stderr);
        close(fd);
        free(emb);
        return NULL;
    }
    emb->listen_fd = fd;
    global_emb = emb;
    return &emb->ctx;
}

/* --- nxt_unit_run --- */
/* Returns 1 if conn was removed (caller must not advance p), 0 otherwise.
 * Removed conns are appended to *pending_free for deferred free (avoids use-after-free when
 * epoll/kqueue delivers multiple events for the same fd in one batch). */
static int run_loop_process_conn(embed_ctx_t *emb, conn_t *c, int can_read, int can_write, int err_or_hup, conn_t **pending_free) {
    int n;
    (void) emb;
    if (nxt_slow_path(err_or_hup)) {
        conn_unlink(c);
        c->prev = NULL;
        c->next = *pending_free;
        *pending_free = c;
#if EMB_USE_EPOLL || EMB_USE_KQUEUE
        embed_ev_remove_conn(emb, c);
#if EMB_USE_KQUEUE
        embed_kq_flush(emb);
#endif
#endif
        return 1;
    }
    /* Drain send buffer: up to EMB_DRAIN_MAX_WRITE write() per wakeup. */
    if (can_write && c->send_len > 0) {
        int nw = 0;
        for (;;) {
            if (nw >= EMB_DRAIN_MAX_WRITE)
                break;
            n = (int) write(c->fd, c->send_buf, c->send_len);
            nw++;
            if (n > 0) {
                memmove(c->send_buf, c->send_buf + (size_t) n, c->send_len - (size_t) n);
                c->send_len -= (size_t) n;
                if (c->send_len == 0) {
#if EMB_USE_EPOLL || EMB_USE_KQUEUE
                    embed_ev_want_write(emb, c, 0);
#endif
                    break;
                }
                continue;
            }
            if (n < 0 && (errno == EAGAIN || errno == EWOULDBLOCK))
                break;
            conn_unlink(c);
            c->prev = NULL;
            c->next = *pending_free;
            *pending_free = c;
#if EMB_USE_EPOLL || EMB_USE_KQUEUE
            embed_ev_remove_conn(emb, c);
#if EMB_USE_KQUEUE
            embed_kq_flush(emb);
#endif
#endif
            return 1;
        }
    }
    /* Drain recv: up to EMB_DRAIN_MAX_READ read() per wakeup. */
    if (can_read) {
        int nr = 0;
        for (;;) {
            if (nr >= EMB_DRAIN_MAX_READ)
                break;
            {
            size_t space = RECV_BUF_SIZE - c->recv_len;
            if (space == 0)
                break;
            n = (int) read(c->fd, c->recv_buf + c->recv_len, space);
            nr++;
            if (n > 0) {
                c->recv_len += (size_t) n;
                continue;
            }
            if (n == 0) {
                conn_unlink(c);
                c->prev = NULL;
                c->next = *pending_free;
                *pending_free = c;
#if EMB_USE_EPOLL || EMB_USE_KQUEUE
                embed_ev_remove_conn(emb, c);
#if EMB_USE_KQUEUE
                embed_kq_flush(emb);
#endif
#endif
                return 1;
            }
            if (errno == EAGAIN || errno == EWOULDBLOCK)
                break;
            conn_unlink(c);
            c->prev = NULL;
            c->next = *pending_free;
            *pending_free = c;
#if EMB_USE_EPOLL || EMB_USE_KQUEUE
            embed_ev_remove_conn(emb, c);
#if EMB_USE_KQUEUE
            embed_kq_flush(emb);
#endif
#endif
            return 1;
            }
        }
        if (c->recv_len == 0) {
            conn_unlink(c);
            c->prev = NULL;
            c->next = *pending_free;
            *pending_free = c;
#if EMB_USE_EPOLL || EMB_USE_KQUEUE
            embed_ev_remove_conn(emb, c);
#if EMB_USE_KQUEUE
            embed_kq_flush(emb);
#endif
#endif
            return 1;
        }
        if (c->is_websocket) {
            if (conn_parse_websocket_frames(emb, c) != 0) {
                conn_unlink(c);
                c->prev = NULL;
                c->next = *pending_free;
                *pending_free = c;
#if EMB_USE_EPOLL || EMB_USE_KQUEUE
                embed_ev_remove_conn(emb, c);
#if EMB_USE_KQUEUE
                embed_kq_flush(emb);
#endif
#endif
                return 1;
            }
#if EMB_USE_EPOLL || EMB_USE_KQUEUE
            if (c->send_len > 0) embed_ev_want_write(emb, c, 1);
#endif
        } else {
            if (conn_parse_request(c) != 0) {
                conn_unlink(c);
                c->prev = NULL;
                c->next = *pending_free;
                *pending_free = c;
#if EMB_USE_EPOLL || EMB_USE_KQUEUE
                embed_ev_remove_conn(emb, c);
#if EMB_USE_KQUEUE
                embed_kq_flush(emb);
#endif
#endif
                return 1;
            }
            /* If we have req_info but not yet request_ready, we may be waiting for the full body (Content-Length) */
            if (c->req_info != NULL && !c->request_ready && c->request != NULL
                && c->recv_len >= c->recv_parsed + (size_t) c->request->content_length) {
                c->request_buf.end = c->recv_buf + c->recv_len;  /* so app can read full body */
                c->request_ready = 1;
            }
            if (c->request_ready) {
                c->response_sent = 0;  /* allow send for this request (keep-alive: next request on same conn) */
                conn_dispatch_request(c);
                conn_send_response(c);
#if EMB_USE_EPOLL || EMB_USE_KQUEUE
                if (c->send_len > 0) embed_ev_want_write(emb, c, 1);
#endif
                c->request_ready = 0;
                c->response_sent = 1;
                /* Calculate consumed: end of headers + body length.
                 * recv_parsed may have been advanced by nxt_unit_request_read during handler,
                 * but we need to consume the entire request including the body, even if the app didn't read it. */
                size_t consumed;
                if (c->request != NULL && c->request->content_length > 0) {
                    /* Consume full request: headers_end (stored when parsed) + body length */
                    consumed = c->headers_end + (size_t) c->request->content_length;
                    if (consumed > c->recv_len) consumed = c->recv_len;
                    /* Also ensure we consume at least what was read (if body was partially read) */
                    if (consumed < c->recv_parsed) consumed = c->recv_parsed;
                } else {
                    /* No body, consumed is just what was parsed (end of headers) */
                    consumed = c->recv_parsed;
                }
                if (consumed > 0 && consumed < c->recv_len) {
                    memmove(c->recv_buf, c->recv_buf + consumed, c->recv_len - consumed);
                    c->recv_len -= consumed;
                } else if (consumed >= c->recv_len) {
                    c->recv_len = 0;
                }
                c->recv_parsed = 0;
                c->headers_end = 0;
                c->req_pool_used = 0;
                if (!c->is_websocket) {
                    c->req_info = NULL;
                    c->request = NULL;
                    c->response = NULL;
                }
            }
        }
    }
    return 0;
}

int nxt_unit_run(nxt_unit_ctx_t *ctx) {
    embed_ctx_t *emb;
    conn_t *conns = NULL, *c, **p;
    int n, new_fd, opt = 1;
    struct sockaddr_in peer;
    socklen_t peer_len;

    if (nxt_slow_path(ctx == NULL)) return NXT_UNIT_ERROR;
    emb = (embed_ctx_t *) ((char *) ctx - offsetof(embed_ctx_t, ctx));
    emb->quit = 0;

#if EMB_USE_EPOLL
    {
#define EMB_EPOLL_MAXEV 256
        struct epoll_event events[EMB_EPOLL_MAXEV];
        int nevents, i;
        conn_t *pending_free;

        emb->ev_fd = epoll_create(1);
        if (nxt_slow_path(emb->ev_fd < 0)) return NXT_UNIT_ERROR;
        if (nxt_slow_path(embed_ev_add_listen(emb) != 0)) {
            close(emb->ev_fd);
            emb->ev_fd = -1;
            return NXT_UNIT_ERROR;
        }
        while (!emb->quit) {
            /* -1: block until an event. No spinning (0) or long wait (1000ms). */
            nevents = epoll_wait(emb->ev_fd, events, EMB_EPOLL_MAXEV, -1);
            if (nxt_slow_path(nevents < 0)) { if (errno == EINTR) continue; break; }
            if (nxt_slow_path(nevents == 0)) continue;

            pending_free = NULL;
            for (i = 0; i < nevents; i++) {
                int rev = events[i].events;
                if (nxt_slow_path(events[i].data.ptr == NULL)) {
                    /* listen fd */
                    peer_len = sizeof(peer);
                    new_fd = accept(emb->listen_fd, (struct sockaddr *) &peer, &peer_len);
                    if (nxt_fast_path(new_fd >= 0)) {
                        fcntl(new_fd, F_SETFL, O_NONBLOCK);
                        setsockopt(new_fd, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt));
                        c = conn_new(new_fd, emb);
                        if (nxt_fast_path(c != NULL)) {
                            conn_insert(&conns, c);
                            if (nxt_slow_path(embed_ev_add_conn(emb, c) != 0)) {
                                conn_unlink(c);
                                conn_free(c);
                            }
                        } else
                            close(new_fd);
                    }
                    continue;
                }
                c = (conn_t *) events[i].data.ptr;
                if (nxt_slow_path(c->prev == NULL)) continue;  /* already removed this batch */
                if (nxt_slow_path(!run_loop_process_conn(emb, c,
                    (rev & EPOLLIN) != 0,
                    (rev & EPOLLOUT) != 0,
                    (rev & (EPOLLERR | EPOLLHUP)) != 0, &pending_free)))
                    embed_ev_rearm_conn(emb, c);
            }
            while (pending_free != NULL) {
                c = pending_free;
                pending_free = c->next;
                conn_free(c);
            }
        }
        close(emb->ev_fd);
        emb->ev_fd = -1;
    }
#elif EMB_USE_KQUEUE
    {
#define EMB_KQUEUE_MAXEV    256
#define EMB_KQUEUE_MAXCHANGES 512
        struct kevent events[EMB_KQUEUE_MAXEV];
        int nevents, i;
        conn_t *pending_free;

        emb->ev_fd = kqueue();
        if (nxt_slow_path(emb->ev_fd < 0)) return NXT_UNIT_ERROR;
        emb->kq_mchanges = EMB_KQUEUE_MAXCHANGES;
        emb->kq_changes = (struct kevent *) malloc((size_t) emb->kq_mchanges * sizeof(struct kevent));
        if (nxt_slow_path(emb->kq_changes == NULL)) {
            close(emb->ev_fd);
            emb->ev_fd = -1;
            return NXT_UNIT_ERROR;
        }
        emb->kq_nchanges = 0;
        if (nxt_slow_path(embed_ev_add_listen(emb) != 0)) {
            free(emb->kq_changes);
            close(emb->ev_fd);
            emb->ev_fd = -1;
            return NXT_UNIT_ERROR;
        }
        /* Single kevent(apply+wait) for low latency. EV_DISPATCH/EV_ONESHOT on write gives one event per enable so we don't spin. */
        while (!emb->quit) {
            nevents = kevent(emb->ev_fd, emb->kq_changes, emb->kq_nchanges, events, EMB_KQUEUE_MAXEV, NULL);
            emb->kq_nchanges = 0;
            if (nxt_slow_path(nevents < 0)) { if (errno == EINTR) continue; break; }
            if (nxt_slow_path(nevents == 0)) continue;

            pending_free = NULL;
            for (i = 0; i < nevents; i++) {
                int filter = events[i].filter;
                int flags = events[i].flags;
                int err_or_hup = (flags & EV_ERROR) != 0 || (flags & EV_EOF) != 0;
                if (nxt_slow_path(events[i].udata == NULL)) {
                    /* listen fd */
                    if (nxt_fast_path(filter == EVFILT_READ && !err_or_hup)) {
                        peer_len = sizeof(peer);
                        new_fd = accept(emb->listen_fd, (struct sockaddr *) &peer, &peer_len);
                        if (nxt_fast_path(new_fd >= 0)) {
                            fcntl(new_fd, F_SETFL, O_NONBLOCK);
                            setsockopt(new_fd, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt));
                            c = conn_new(new_fd, emb);
                            if (nxt_fast_path(c != NULL)) {
                                conn_insert(&conns, c);
                                if (nxt_slow_path(embed_ev_add_conn(emb, c) != 0)) {
                                    conn_unlink(c);
                                    conn_free(c);
                                }
                            } else
                                close(new_fd);
                        }
                    }
                    continue;
                }
                c = (conn_t *) events[i].udata;
                if (nxt_slow_path(c->prev == NULL)) continue;  /* already removed this batch */
                if (nxt_fast_path(filter == EVFILT_READ))
                    run_loop_process_conn(emb, c, 1, 0, err_or_hup, &pending_free);
                else if (filter == EVFILT_WRITE)
                    run_loop_process_conn(emb, c, 0, 1, err_or_hup, &pending_free);
            }
            while (pending_free != NULL) {
                c = pending_free;
                pending_free = c->next;
                conn_free(c);
            }
        }
        free(emb->kq_changes);
        emb->kq_changes = NULL;
        close(emb->ev_fd);
        emb->ev_fd = -1;
    }
#else
    {
        struct pollfd *pfds;
        int nfds, cap, i;
        conn_t *pending_free;

        cap = 64;
        pfds = (struct pollfd *) malloc((size_t) cap * sizeof(struct pollfd));
        if (nxt_slow_path(pfds == NULL)) return NXT_UNIT_ERROR;

        while (!emb->quit) {
            nfds = 0;
            pfds[nfds].fd = emb->listen_fd;
            pfds[nfds].events = POLLIN;
            nfds++;
            for (c = conns; c != NULL; c = c->next) {
                if (nxt_slow_path(nfds >= cap)) {
                    cap *= 2;
                    struct pollfd *np = (struct pollfd *) realloc(pfds, (size_t) cap * sizeof(struct pollfd));
                    if (nxt_slow_path(np == NULL)) { free(pfds); return NXT_UNIT_ERROR; }
                    pfds = np;
                }
                pfds[nfds].fd = c->fd;
                pfds[nfds].events = POLLIN;
                if (c->send_len > 0) pfds[nfds].events |= POLLOUT;
                nfds++;
            }

            /* -1: block until an event. No spinning (0) or long wait (1000ms). */
            n = poll(pfds, (nfds_t) nfds, -1);
            if (nxt_slow_path(n < 0)) { if (errno == EINTR) continue; break; }
            if (nxt_slow_path(n == 0)) continue;

            if (nxt_fast_path(pfds[0].revents & POLLIN)) {
                peer_len = sizeof(peer);
                new_fd = accept(emb->listen_fd, (struct sockaddr *) &peer, &peer_len);
                if (nxt_fast_path(new_fd >= 0)) {
                    fcntl(new_fd, F_SETFL, O_NONBLOCK);
                    setsockopt(new_fd, IPPROTO_TCP, TCP_NODELAY, &opt, sizeof(opt));
                    c = conn_new(new_fd, emb);
                    if (nxt_fast_path(c != NULL)) {
                        conn_insert(&conns, c);
                    } else
                        close(new_fd);
                }
            }

            pending_free = NULL;
            p = &conns;
            while (*p != NULL) {
                c = *p;
                for (i = 1; i < nfds && pfds[i].fd != c->fd; i++) ;
                if (nxt_slow_path(i >= nfds)) { p = &c->next; continue; }
                if (nxt_slow_path(!run_loop_process_conn(emb, c,
                    (pfds[i].revents & POLLIN) != 0,
                    (pfds[i].revents & POLLOUT) != 0,
                    (pfds[i].revents & (POLLERR | POLLHUP)) != 0, &pending_free)))
                    p = &c->next;
            }
            while (pending_free != NULL) {
                c = pending_free;
                pending_free = c->next;
                conn_free(c);
            }
        }
        free(pfds);
    }
#endif

    for (c = conns; c != NULL; ) {
        conn_t *next = c->next;
#if EMB_USE_EPOLL || EMB_USE_KQUEUE
        if (emb->ev_fd >= 0) embed_ev_remove_conn(emb, c);
#endif
        conn_free(c);
        c = next;
    }
    return NXT_UNIT_OK;
}

int nxt_unit_run_ctx(nxt_unit_ctx_t *ctx) {
    return nxt_unit_run(ctx);
}
int nxt_unit_run_shared(nxt_unit_ctx_t *ctx) {
    return nxt_unit_run(ctx);
}
nxt_unit_request_info_t *nxt_unit_dequeue_request(nxt_unit_ctx_t *ctx) {
    (void) ctx;
    return NULL;
}
int nxt_unit_run_once(nxt_unit_ctx_t *ctx) {
    (void) ctx;
    return NXT_UNIT_AGAIN;
}
int nxt_unit_process_port_msg(nxt_unit_ctx_t *ctx, nxt_unit_port_t *port) {
    (void) ctx;
    (void) port;
    return NXT_UNIT_OK;
}

void nxt_unit_done(nxt_unit_ctx_t *ctx) {
    embed_ctx_t *emb;
    if (nxt_slow_path(ctx == NULL)) return;
    emb = (embed_ctx_t *) ((char *) ctx - offsetof(embed_ctx_t, ctx));
    if (nxt_fast_path(emb->listen_fd >= 0)) {
        close(emb->listen_fd);
        emb->listen_fd = -1;
    }
    global_emb = NULL;
    free(emb);
}

nxt_unit_ctx_t *nxt_unit_ctx_alloc(nxt_unit_ctx_t *parent, void *data) {
    (void) parent;
    (void) data;
    return NULL;
}
void nxt_unit_port_id_init(nxt_unit_port_id_t *port_id, pid_t pid, uint16_t id) {
    port_id->pid = pid;
    port_id->hash = 0;
    port_id->id = id;
}
uint16_t nxt_unit_field_hash(const char *name, size_t name_length) {
    return (uint16_t) field_hash(name, name_length);
}
void nxt_unit_split_host(char *host_start, uint32_t host_length,
    char **name, uint32_t *name_length, char **port, uint32_t *port_length) {
    (void) host_start;
    (void) host_length;
    (void) name;
    (void) name_length;
    (void) port;
    (void) port_length;
}
void nxt_unit_request_group_dup_fields(nxt_unit_request_info_t *req) {
    (void) req;
}

/* --- Response API --- */
/* Recover conn from req: (1) conn* is stored immediately before req_info in the pool (works when
 * handler runs on a worker thread); (2) current_dispatch_conn is set during dispatch (works when
 * handler runs on the same thread). req->data is not used (ABI/layout may differ on Scala Native). */
static conn_t *current_dispatch_conn;

static conn_t *req_to_conn(nxt_unit_request_info_t *req) {
    conn_t *c;
    if (nxt_slow_path(req == NULL)) return NULL;
    c = *(conn_t **)((char *) req - sizeof(conn_t *));
    if (nxt_fast_path(c != NULL && c->req_info == req)) return c;
    return current_dispatch_conn;
}

#define RESPONSE_MIN_FIELDS  32

/* Pool allocations for 8-byte-aligned structs (request_info, request, response on Linux). */
static inline void pool_align_8(conn_t *c) {
    uintptr_t p = (uintptr_t)(c->req_pool + c->req_pool_used);
    c->req_pool_used += (size_t)((8 - (p % 8)) % 8);
}

int nxt_unit_response_init(nxt_unit_request_info_t *req,
    uint16_t status, uint32_t max_fields_count, uint32_t max_fields_size) {
    conn_t *c = req_to_conn(req);
    uint32_t alloc_count;
    if (nxt_slow_path(c == NULL || c->response != NULL)) return NXT_UNIT_ERROR;
    alloc_count = max_fields_count < RESPONSE_MIN_FIELDS ? RESPONSE_MIN_FIELDS : max_fields_count;
    pool_align_8(c);
    c->response = (nxt_unit_response_t *) (c->req_pool + c->req_pool_used);
    c->req_pool_used += sizeof(nxt_unit_response_t) + (size_t) alloc_count * sizeof(nxt_unit_field_t);
    if (nxt_slow_path(c->req_pool_used > REQ_POOL_SIZE)) return NXT_UNIT_ERROR;
    c->response->content_length = 0;
    c->response->fields_count = 0;
    c->response->piggyback_content_length = 0;
    c->response->status = status;
    req->response = c->response;
    req->response_buf = &c->response_buf;
    req->response_buf->start = c->send_buf + 512;
    req->response_buf->free = req->response_buf->start;
    req->response_buf->end = c->send_buf + SEND_BUF_SIZE;
    req->response_max_fields = alloc_count;
    return NXT_UNIT_OK;
}
int nxt_unit_response_realloc(nxt_unit_request_info_t *req,
    uint32_t max_fields_count, uint32_t max_fields_size) {
    (void) req;
    (void) max_fields_count;
    (void) max_fields_size;
    return NXT_UNIT_ERROR;
}
int nxt_unit_response_is_init(nxt_unit_request_info_t *req) {
    return req_to_conn(req) != NULL && req_to_conn(req)->response != NULL ? 1 : 0;
}
int nxt_unit_response_add_field(nxt_unit_request_info_t *req,
    const char *name, uint8_t name_length,
    const char *value, uint32_t value_length) {
    conn_t *c = req_to_conn(req);
    nxt_unit_field_t *f;
    char *dst;
    if (nxt_slow_path(c == NULL || c->response == NULL)) return NXT_UNIT_ERROR;
    if (nxt_slow_path(c->response->fields_count >= req->response_max_fields)) return NXT_UNIT_ERROR;
    if (nxt_slow_path(c->req_pool_used + (size_t) name_length + (size_t) value_length + 32 > REQ_POOL_SIZE)) return NXT_UNIT_ERROR;
    f = &c->response->fields[c->response->fields_count];
    dst = c->req_pool + c->req_pool_used;
    f->hash = (uint16_t) field_hash(name, (size_t) name_length);
    f->skip = 0;
    f->hopbyhop = 0;
    f->name_length = name_length;
    f->value_length = value_length;
    f->name.offset = (uint32_t) ((uintptr_t) dst - (uintptr_t) &f->name);
    memcpy(dst, name, (size_t) name_length);
    dst[name_length] = '\0';
    c->req_pool_used += (size_t) name_length + 1;
    f->value.offset = (uint32_t) ((uintptr_t) (c->req_pool + c->req_pool_used) - (uintptr_t) &f->value);
    memcpy(c->req_pool + c->req_pool_used, value, (size_t) value_length);
    *(c->req_pool + c->req_pool_used + (size_t) value_length) = '\0';
    c->req_pool_used += (size_t) value_length + 1;
    c->response->fields_count++;
    return NXT_UNIT_OK;
}
int nxt_unit_response_add_content(nxt_unit_request_info_t *req, const void *src, uint32_t size) {
    conn_t *c = req_to_conn(req);
    if (nxt_slow_path(c == NULL || c->response_buf.free + size > c->response_buf.end)) return NXT_UNIT_ERROR;
    memcpy(c->response_buf.free, src, (size_t) size);
    c->response_buf.free += size;
    c->response->content_length += size;
    return NXT_UNIT_OK;
}
int nxt_unit_response_send(nxt_unit_request_info_t *req) {
    conn_t *c = req_to_conn(req);
    int r;
    if (nxt_slow_path(c == NULL)) return NXT_UNIT_ERROR;
    r = conn_send_response(c);
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
    pthread_mutex_lock(&c->dispatch_mutex);
    c->handler_done = 1;
    pthread_cond_signal(&c->dispatch_cond);
    pthread_mutex_unlock(&c->dispatch_mutex);
#endif
    return r;
}
int nxt_unit_response_is_sent(nxt_unit_request_info_t *req) {
    conn_t *c = req_to_conn(req);
    return c != NULL && c->response_sent ? 1 : 0;
}
nxt_unit_buf_t *nxt_unit_response_buf_alloc(nxt_unit_request_info_t *req, uint32_t size) {
    conn_t *c = req_to_conn(req);
    if (nxt_slow_path(c == NULL)) return NULL;
    if (nxt_slow_path(c->req_pool_used + size > REQ_POOL_SIZE)) return NULL;
    c->response_buf.start = c->req_pool + c->req_pool_used;
    c->response_buf.free = c->response_buf.start;
    c->response_buf.end = c->response_buf.start + size;
    c->req_pool_used += size;
    return &c->response_buf;
}
int nxt_unit_request_is_websocket_handshake(nxt_unit_request_info_t *req) {
    return req != NULL && req->request != NULL && req->request->websocket_handshake ? 1 : 0;
}
int nxt_unit_response_upgrade(nxt_unit_request_info_t *req) {
    conn_t *c;
    nxt_unit_request_t *r;
    nxt_unit_field_t *f;
    char *key_ptr = NULL;
    size_t key_len = 0;
    unsigned char key_buf[64];
    unsigned char accept_bin[20];
    char accept_b64[32];
    const char *magic = "258EAFA5-E914-47DA-95CA-C5AB0DC85B11";
    size_t magic_len = 36;
    int i, n;
    if (nxt_slow_path(req == NULL || req->request == NULL || !req->request->websocket_handshake))
        return NXT_UNIT_ERROR;
    c = req_to_conn(req);
    if (nxt_slow_path(c == NULL)) return NXT_UNIT_ERROR;
    r = req->request;
    for (i = 0; i < (int) r->fields_count; i++) {
        f = &r->fields[i];
        if (f->name_length == 18 && strncasecmp((char *) nxt_unit_sptr_get(&f->name), "Sec-WebSocket-Key", 18) == 0) {
            key_ptr = (char *) nxt_unit_sptr_get(&f->value);
            key_len = (size_t) f->value_length;
            break;
        }
    }
    if (nxt_slow_path(key_ptr == NULL || key_len == 0 || key_len > 64)) return NXT_UNIT_ERROR;
    memcpy(key_buf, key_ptr, key_len);
    memcpy(key_buf + key_len, magic, magic_len);
    sha1_hash(key_buf, key_len + magic_len, accept_bin);
    base64_encode(accept_bin, 20, accept_b64, sizeof(accept_b64));
    n = snprintf(c->send_buf, SEND_BUF_SIZE,
        "HTTP/1.1 101 Switching Protocols\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Accept: %s\r\nSec-WebSocket-Version: 13\r\n\r\n",
        accept_b64);
    if (nxt_slow_path(n <= 0 || (size_t) n >= SEND_BUF_SIZE)) return NXT_UNIT_ERROR;
    c->send_len = (size_t) n;
    c->response_sent = 1;
    c->is_websocket = 1;
    return NXT_UNIT_OK;
}
int nxt_unit_response_is_websocket(nxt_unit_request_info_t *req) {
    conn_t *c = req_to_conn(req);
    return (c != NULL && c->is_websocket) ? 1 : 0;
}

/* Parse WebSocket frames from recv_buf; dispatch each to websocket_handler. Returns 0 on success, -1 on error/close. */
static int conn_parse_websocket_frames(embed_ctx_t *emb, conn_t *c) {
    size_t payload_len, frame_len, ext_len, i;
    uint8_t *buf;
    uint64_t payload_len64;
    conn_t *prev_conn;
    while (c->recv_parsed + 2 <= c->recv_len) {
        buf = (uint8_t *) (c->recv_buf + c->recv_parsed);
        memcpy(&c->ws_header, buf, 2);
        ext_len = 0;
        payload_len64 = (uint64_t) (buf[1] & 0x7F);
        if (payload_len64 == 126) {
            ext_len = 2;
            if (c->recv_parsed + 4 > c->recv_len) return 0;
            payload_len64 = (uint64_t) buf[2] << 8 | buf[3];
        } else if (payload_len64 == 127) {
            ext_len = 8;
            if (c->recv_parsed + 10 > c->recv_len) return 0;
            payload_len64 = (uint64_t) buf[2] << 56 | (uint64_t) buf[3] << 48 | (uint64_t) buf[4] << 40 | (uint64_t) buf[5] << 32
                | (uint64_t) buf[6] << 24 | (uint64_t) buf[7] << 16 | (uint64_t) buf[8] << 8 | buf[9];
        }
        payload_len = (size_t) payload_len64;
        if (payload_len > RECV_BUF_SIZE) return -1;
        frame_len = 2 + ext_len + (buf[1] & 0x80 ? 4 : 0) + payload_len;
        if (c->recv_parsed + frame_len > c->recv_len) return 0;
        c->ws_payload_off = c->recv_parsed + 2 + ext_len + (buf[1] & 0x80 ? 4 : 0);
        c->ws_payload_len = payload_len;
        if (buf[1] & 0x80) {
            memcpy(c->ws_mask, buf + 2 + ext_len, 4);
            for (i = 0; i < payload_len; i++)
                c->recv_buf[c->ws_payload_off + i] ^= c->ws_mask[i & 3];
        }
        c->ws_frame_size = frame_len;
        c->content_buf.start = c->recv_buf + c->ws_payload_off;
        c->content_buf.free = c->content_buf.start;
        c->content_buf.end = c->content_buf.start + payload_len;
        c->ws_frame.req = c->req_info;
        c->ws_frame.payload_len = payload_len64;
        c->ws_frame.header = &c->ws_header;
        c->ws_frame.mask = NULL;
        c->ws_frame.content_buf = &c->content_buf;
        c->ws_frame.content_length = payload_len64;
        prev_conn = current_dispatch_conn;
        current_dispatch_conn = c;
        if (emb->init->callbacks.websocket_handler != NULL)
            emb->init->callbacks.websocket_handler(&c->ws_frame);
        current_dispatch_conn = prev_conn;
        if ((buf[0] & 0x0F) == 8)
            return -1;
        /* recv_parsed advanced by nxt_unit_websocket_done when handler is done */
    }
    if (c->recv_parsed > 0 && c->recv_parsed < c->recv_len) {
        memmove(c->recv_buf, c->recv_buf + c->recv_parsed, c->recv_len - c->recv_parsed);
        c->recv_len -= c->recv_parsed;
        c->recv_parsed = 0;
    } else if (c->recv_parsed >= c->recv_len) {
        c->recv_len = 0;
        c->recv_parsed = 0;
    }
    return 0;
}

nxt_unit_request_info_t *nxt_unit_get_request_info_from_data(void *data) {
    return (nxt_unit_request_info_t *) data;
}
int nxt_unit_buf_send(nxt_unit_buf_t *buf) {
    (void) buf;
    return NXT_UNIT_OK;
}
void nxt_unit_buf_free(nxt_unit_buf_t *buf) {
    (void) buf;
}
nxt_unit_buf_t *nxt_unit_buf_next(nxt_unit_buf_t *buf) {
    (void) buf;
    return NULL;
}
uint32_t nxt_unit_buf_max(void) { return 65536; }
uint32_t nxt_unit_buf_min(void) { return 4096; }
int nxt_unit_response_write(nxt_unit_request_info_t *req, const void *start, size_t size) {
    conn_t *c = req_to_conn(req);
    if (nxt_slow_path(c == NULL || c->response_buf.free + size > c->response_buf.end)) return NXT_UNIT_ERROR;
    memcpy(c->response_buf.free, start, size);
    c->response_buf.free += size;
    c->response->content_length += (uint64_t) size;
    return NXT_UNIT_OK;
}
ssize_t nxt_unit_response_write_nb(nxt_unit_request_info_t *req,
    const void *start, size_t size, size_t min_size) {
    (void) min_size;
    if (nxt_slow_path(nxt_unit_response_write(req, start, size) != NXT_UNIT_OK)) return -1;
    return (ssize_t) size;
}
int nxt_unit_response_write_cb(nxt_unit_request_info_t *req, nxt_unit_read_info_t *read_info) {
    (void) req;
    (void) read_info;
    return NXT_UNIT_ERROR;
}
ssize_t nxt_unit_request_read(nxt_unit_request_info_t *req, void *dst, size_t size) {
    conn_t *c = req_to_conn(req);
    size_t avail;
    if (nxt_slow_path(c == NULL)) return -1;
    avail = c->recv_len - c->recv_parsed;
    if (size > avail) size = avail;
    memcpy(dst, c->recv_buf + c->recv_parsed, size);
    c->recv_parsed += size;
    return (ssize_t) size;
}
ssize_t nxt_unit_request_readline_size(nxt_unit_request_info_t *req, size_t max_size) {
    (void) req;
    (void) max_size;
    return -1;
}
void nxt_unit_request_done(nxt_unit_request_info_t *req, int rc) {
    conn_t *c = req_to_conn(req);
    (void) rc;
    if (nxt_fast_path(c != NULL)) {
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
        pthread_mutex_lock(&c->dispatch_mutex);
        c->handler_done = 1;
        pthread_cond_signal(&c->dispatch_cond);
        pthread_mutex_unlock(&c->dispatch_mutex);
#endif
    }
}

int nxt_unit_websocket_send(nxt_unit_request_info_t *req, uint8_t opcode,
    uint8_t last, const void *start, size_t size) {
    conn_t *c;
    size_t len, header_len, total;
    uint8_t *p;
    if (nxt_slow_path(req == NULL)) return NXT_UNIT_ERROR;
    c = req_to_conn(req);
    if (nxt_slow_path(c == NULL || !c->is_websocket)) return NXT_UNIT_ERROR;
    header_len = 2 + (size > 125 ? (size > 65535 ? 8 : 2) : 0);
    total = header_len + size;
    if (nxt_slow_path(c->send_len + total > SEND_BUF_SIZE)) return NXT_UNIT_ERROR;
    p = (uint8_t *) (c->send_buf + c->send_len);
    p[0] = (uint8_t) ((last ? 0x80 : 0) | (opcode & 0x0F));
    if (size <= 125) {
        p[1] = (uint8_t) size;
    } else if (size <= 65535) {
        p[1] = 126;
        p[2] = (uint8_t)(size >> 8); p[3] = (uint8_t)size;
    } else {
        p[1] = 127;
        p[2] = (uint8_t)(size >> 56); p[3] = (uint8_t)(size >> 48); p[4] = (uint8_t)(size >> 40); p[5] = (uint8_t)(size >> 32);
        p[6] = (uint8_t)(size >> 24); p[7] = (uint8_t)(size >> 16); p[8] = (uint8_t)(size >> 8); p[9] = (uint8_t)size;
    }
    if (size > 0 && start != NULL)
        memcpy(p + header_len, start, size);
    c->send_len += total;
    return NXT_UNIT_OK;
}
int nxt_unit_websocket_sendv(nxt_unit_request_info_t *req, uint8_t opcode,
    uint8_t last, const struct iovec *iov, int iovcnt) {
    size_t total = 0;
    int i;
    if (nxt_slow_path(req == NULL || iov == NULL)) return NXT_UNIT_ERROR;
    for (i = 0; i < iovcnt; i++)
        total += iov[i].iov_len;
    if (nxt_slow_path(total == 0))
        return nxt_unit_websocket_send(req, opcode, last, NULL, 0);
    if (nxt_slow_path(total > SEND_BUF_SIZE)) return NXT_UNIT_ERROR;
    {
        conn_t *c = req_to_conn(req);
        size_t len, header_len, off = 0;
        uint8_t *p;
        if (nxt_slow_path(c == NULL || !c->is_websocket)) return NXT_UNIT_ERROR;
        header_len = 2 + (total > 125 ? (total > 65535 ? 8 : 2) : 0);
        if (nxt_slow_path(c->send_len + header_len + total > SEND_BUF_SIZE)) return NXT_UNIT_ERROR;
        p = (uint8_t *) (c->send_buf + c->send_len);
        p[0] = (uint8_t) ((last ? 0x80 : 0) | (opcode & 0x0F));
        if (total <= 125) {
            p[1] = (uint8_t) total;
        } else if (total <= 65535) {
            p[1] = 126;
            p[2] = (uint8_t)(total >> 8); p[3] = (uint8_t)total;
        } else {
            p[1] = 127;
            p[2] = (uint8_t)(total >> 56); p[3] = (uint8_t)(total >> 48); p[4] = (uint8_t)(total >> 40); p[5] = (uint8_t)(total >> 32);
            p[6] = (uint8_t)(total >> 24); p[7] = (uint8_t)(total >> 16); p[8] = (uint8_t)(total >> 8); p[9] = (uint8_t)total;
        }
        for (i = 0; i < iovcnt && off < total; i++) {
            len = iov[i].iov_len;
            if (len > total - off) len = total - off;
            memcpy(p + header_len + off, iov[i].iov_base, len);
            off += len;
        }
        c->send_len += header_len + total;
    }
    return NXT_UNIT_OK;
}
ssize_t nxt_unit_websocket_read(nxt_unit_websocket_frame_t *ws, void *dst, size_t size) {
    conn_t *c;
    size_t avail;
    ssize_t res;
    if (nxt_slow_path(ws == NULL || dst == NULL)) return -1;
    c = req_to_conn(ws->req);
    if (nxt_slow_path(c == NULL)) return -1;
    avail = (size_t) (ws->content_buf->end - ws->content_buf->free);
    if (size > avail) size = avail;
    memcpy(dst, ws->content_buf->free, size);
    res = (ssize_t) size;
    ws->content_buf->free += size;
    ws->content_length -= (uint64_t) res;  /* match Unit: remaining payload length */
    return res;
}
int nxt_unit_websocket_retain(nxt_unit_websocket_frame_t *ws) {
    (void) ws;
    return NXT_UNIT_OK;
}
void nxt_unit_websocket_done(nxt_unit_websocket_frame_t *ws) {
    conn_t *c;
    if (nxt_slow_path(ws == NULL)) return;
    c = req_to_conn(ws->req);
    if (nxt_fast_path(c != NULL))
        c->recv_parsed += c->ws_frame_size;
}

void *nxt_unit_malloc(nxt_unit_ctx_t *ctx, size_t size) {
    (void) ctx;
    return malloc(size);
}
void nxt_unit_free(nxt_unit_ctx_t *ctx, void *p) {
    (void) ctx;
    free(p);
}

void nxt_unit_log(nxt_unit_ctx_t *ctx, int level, const char *fmt, ...) {
    (void) ctx;
    (void) level;
    (void) fmt;
}
void nxt_unit_req_log(nxt_unit_request_info_t *req, int level, const char *fmt, ...) {
    (void) req;
    (void) level;
    (void) fmt;
}

/* --- Helpers --- */
#define nxt_lowcase(c) ((unsigned char) ((c >= 'A' && c <= 'Z') ? c | 0x20 : c))

static uint16_t field_hash(const char *name, size_t len) {
    /* Match nxt_unit_field_hash from NGINX Unit: lowercase before hashing */
    uint32_t hash = 159406; /* Magic value copied from nxt_http_parse.c */
    size_t i;
    for (i = 0; i < len; i++) {
        unsigned char ch = nxt_lowcase(name[i]);
        hash = (hash << 4) + hash + ch;
    }
    hash = (hash >> 16) ^ hash;
    return (uint16_t) hash;
}

static conn_t *conn_new(int fd, embed_ctx_t *emb) {
    conn_t *c = (conn_t *) calloc(1, sizeof(conn_t));
    if (nxt_slow_path(c == NULL)) return NULL;
    c->fd = fd;
    c->emb = emb;
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
    pthread_mutex_init(&c->dispatch_mutex, NULL);
    pthread_cond_init(&c->dispatch_cond, NULL);
#endif
    return c;
}

static void conn_free(conn_t *c) {
    if (nxt_slow_path(c == NULL)) return;
    if (nxt_fast_path(c->fd >= 0)) close(c->fd);
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
    pthread_mutex_destroy(&c->dispatch_mutex);
    pthread_cond_destroy(&c->dispatch_cond);
#endif
    free(c);
}

/* Percent-decode path (RFC 3986); target stays raw. Decoded length is at most src_len. */
static size_t percent_decode_path(const char *src, size_t src_len, char *dst) {
    size_t di = 0;
    for (size_t si = 0; si < src_len; ) {
        if (src[si] == '%' && si + 2 < src_len) {
            unsigned int a = (unsigned char) src[si + 1];
            unsigned int b = (unsigned char) src[si + 2];
            int ha = (a >= '0' && a <= '9') ? a - '0' : (a >= 'A' && a <= 'F') ? a - 'A' + 10 : (a >= 'a' && a <= 'f') ? a - 'a' + 10 : -1;
            int hb = (b >= '0' && b <= '9') ? b - '0' : (b >= 'A' && b <= 'F') ? b - 'A' + 10 : (b >= 'a' && b <= 'f') ? b - 'a' + 10 : -1;
            if (ha >= 0 && hb >= 0) {
                dst[di++] = (char) ((ha << 4) | hb);
                si += 3;
                continue;
            }
        }
        dst[di++] = src[si++];
    }
    return di;
}

/* Minimal HTTP/1 request parser: request line + headers until \r\n\r\n or \n\n */
static int conn_parse_request(conn_t *c) {
    char *p, *end, *line_end, *colon;
    size_t line_len, method_len, path_len, i;
    size_t headers_end;
    nxt_unit_request_t *req;
    nxt_unit_request_info_t *req_info;
    nxt_unit_field_t *f;
    char *pool;
    size_t pool_off;
    char *host_value = NULL;
    size_t host_value_len = 0;

    if (nxt_fast_path(c->req_info != NULL)) return 0;
    end = c->recv_buf + c->recv_len;
    headers_end = 0;
    for (p = c->recv_buf + c->recv_parsed; p + 2 <= end; p++) {
        if (p[0] == '\r' && p[1] == '\n' && p + 4 <= end && p[2] == '\r' && p[3] == '\n') {
            headers_end = (size_t)(p + 4 - c->recv_buf);
            break;
        }
        if (p[0] == '\n' && p[1] == '\n') {
            headers_end = (size_t)(p + 2 - c->recv_buf);
            break;
        }
    }
    if (nxt_slow_path(headers_end == 0)) return 0;
    c->recv_parsed = headers_end;
    c->headers_end = headers_end;  /* Store for calculating consumed later */
    end = c->recv_buf + headers_end;  /* parse only up to end of headers */

    /* Allocate conn* then request_info so req_to_conn can get conn from req (works from any thread) */
    if (nxt_slow_path(c->req_pool_used + 8 + sizeof(conn_t *) + sizeof(nxt_unit_request_info_t) + sizeof(nxt_unit_request_t) + 64 * sizeof(nxt_unit_field_t) + 4096 > REQ_POOL_MAX))
        return -1;
    pool_align_8(c);
    *(conn_t **)(c->req_pool + c->req_pool_used) = c;
    c->req_pool_used += sizeof(conn_t *);
    req_info = (nxt_unit_request_info_t *) (c->req_pool + c->req_pool_used);
    c->req_pool_used += sizeof(nxt_unit_request_info_t);
    req = (nxt_unit_request_t *) (c->req_pool + c->req_pool_used);
    c->req_pool_used += sizeof(nxt_unit_request_t) + 64 * sizeof(nxt_unit_field_t);
    pool_off = c->req_pool_used;
    pool = c->req_pool + pool_off;

    req_info->unit = &c->emb->unit;
    req_info->ctx = &c->emb->ctx;
    req_info->response_port = NULL;
    req_info->request = req;
    req_info->request_buf = &c->request_buf;
    req_info->response = NULL;
    req_info->response_buf = NULL;
    req_info->response_max_fields = 0;
    req_info->content_buf = &c->content_buf;
    req_info->content_length = 0;
    req_info->content_fd = -1;
    req_info->data = c;

    memset(req, 0, sizeof(nxt_unit_request_t));
    req->content_length_field = NXT_UNIT_NONE_FIELD;
    req->content_type_field = NXT_UNIT_NONE_FIELD;
    req->cookie_field = NXT_UNIT_NONE_FIELD;
    req->authorization_field = NXT_UNIT_NONE_FIELD;

    p = c->recv_buf;
    line_end = (char *) memchr(p, '\r', (size_t)(end - p));
    if (line_end == NULL) line_end = (char *) memchr(p, '\n', (size_t)(end - p));
    if (line_end == NULL) return -1;
    line_len = (size_t)(line_end - p);
    if (line_end < end && *line_end == '\r' && line_end + 1 < end && line_end[1] == '\n')
        line_end++;  /* skip \r for next line start */
    for (i = 0; i < line_len && p[i] != ' '; i++) ;
    if (i >= line_len) return -1;
    method_len = i;
    while (i < line_len && p[i] == ' ') i++;
    if (i >= line_len) return -1;
    path_len = 0;
    while (i + path_len < line_len && p[i + path_len] != ' ' && p[i + path_len] != '?') path_len++;
    size_t query_len = 0;
    if (i + path_len < line_len && p[i + path_len] == '?') {
        size_t q_start = i + path_len + 1;
        while (q_start + query_len < line_len && p[q_start + query_len] != ' ')
            query_len++;
    }
    /* target = raw path + ? + query (full request-target for URI parsing); path = percent-decoded; query = query string */
    size_t target_len = path_len + (query_len > 0 ? 1 + query_len : 0);
    if (pool_off + method_len + target_len + 1 + path_len * 2 + query_len + 64 + 8 > REQ_POOL_MAX) return -1;
    memcpy(pool, p, method_len);
    pool[method_len] = '\0';
    req->method_length = (uint8_t) method_len;
    req->method.offset = (uint32_t) ((uintptr_t) pool - (uintptr_t) &req->method);
    pool += method_len + 1;
    pool_off += method_len + 1;
    /* target: full request-target (path?query) as received, so URI parsers get path and query */
    memcpy(c->req_pool + pool_off, p + i, path_len);
    if (query_len > 0) {
        c->req_pool[pool_off + path_len] = '?';
        memcpy(c->req_pool + pool_off + path_len + 1, p + i + path_len + 1, query_len);
        c->req_pool[pool_off + path_len + 1 + query_len] = '\0';
    } else {
        c->req_pool[pool_off + path_len] = '\0';
    }
    req->target_length = (uint32_t) target_len;
    req->target.offset = (uint32_t) ((uintptr_t) (c->req_pool + pool_off) - (uintptr_t) &req->target);
    pool_off += target_len + 1;
    pool = c->req_pool + pool_off;
    /* path: percent-decoded (RFC 3986, like NGINX Unit) */
    {
        size_t path_dec_len = percent_decode_path(p + i, path_len, c->req_pool + pool_off);
        c->req_pool[pool_off + path_dec_len] = '\0';
        req->path_length = (uint32_t) path_dec_len;
        req->path.offset = (uint32_t) ((uintptr_t) (c->req_pool + pool_off) - (uintptr_t) &req->path);
        pool += path_dec_len + 1;
        pool_off += path_dec_len + 1;
    }
    /* query: from ? to next space (like NGINX Unit) */
    if (query_len > 0) {
        memcpy(c->req_pool + pool_off, p + i + path_len + 1, query_len);
        c->req_pool[pool_off + query_len] = '\0';
        req->query_length = (uint32_t) query_len;
        req->query.offset = (uint32_t) ((uintptr_t) (c->req_pool + pool_off) - (uintptr_t) &req->query);
        pool_off += query_len + 1;
    } else {
        req->query_length = 0;
        req->query.offset = (uint32_t) ((uintptr_t) (c->req_pool + pool_off - 1) - (uintptr_t) &req->query);
    }
    req->version_length = 8;
    req->version.offset = (uint32_t) ((uintptr_t) (c->req_pool + pool_off) - (uintptr_t) &req->version);
    memcpy(c->req_pool + pool_off, "HTTP/1.1", 8);
    c->req_pool[pool_off + 8] = '\0';
    pool_off += 9;
    req->remote_length = 0;
    req->local_addr_length = 0;
    req->local_port_length = 0;
    req->tls = 0;
    req->websocket_handshake = 0;
    req->app_target = 0;

    p = line_end + 1;
    if (p < end && p[-1] == '\r' && p < end) p++;  /* skip \n after \r */
    req->fields_count = 0;
    while (p < end && !(p[0] == '\n' || (p[0] == '\r' && p + 1 < end && p[1] == '\n'))) {
        line_end = (char *) memchr(p, '\r', (size_t)(end - p));
        if (line_end == NULL) line_end = (char *) memchr(p, '\n', (size_t)(end - p));
        if (line_end == NULL) return -1;
        colon = (char *) memchr(p, ':', (size_t)(line_end - p));
        if (colon != NULL && colon > p) {
            size_t name_len = (size_t)(colon - p);
            while (name_len > 0 && (p[name_len - 1] == ' ' || p[name_len - 1] == '\t')) name_len--;
            colon++;
            while (colon < line_end && (*colon == ' ' || *colon == '\t')) colon++;
            size_t value_len = (size_t)(line_end - colon);
            if (req->fields_count < 64 && pool_off + name_len + value_len + 4 < REQ_POOL_MAX) {
                f = &req->fields[req->fields_count];
                f->hash = (uint16_t) field_hash(p, name_len);
                f->name_length = (uint8_t) name_len;
                f->value_length = (uint32_t) value_len;
                f->name.offset = (uint32_t) ((uintptr_t) (c->req_pool + pool_off) - (uintptr_t) &f->name);
                memcpy(c->req_pool + pool_off, p, name_len);
                c->req_pool[pool_off + name_len] = '\0';
                pool_off += name_len + 1;
                f->value.offset = (uint32_t) ((uintptr_t) (c->req_pool + pool_off) - (uintptr_t) &f->value);
                memcpy(c->req_pool + pool_off, colon, value_len);
                c->req_pool[pool_off + value_len] = '\0';
                pool_off += value_len + 1;
                if (f->hash == NXT_UNIT_HASH_CONTENT_LENGTH)
                    req->content_length = (uint64_t) strtoull((char *) nxt_unit_sptr_get(&f->value), NULL, 10);
                if (f->hash == NXT_UNIT_HASH_HOST && host_value == NULL) {
                    host_value = (char *) nxt_unit_sptr_get(&f->value);
                    host_value_len = value_len;
                }
                req->fields_count++;
            }
        }
        p = line_end + 1;
        if (p <= end && line_end < end && *line_end == '\r' && p[0] == '\n') p++;
    }
    /* Set server_name from Host header (like NGINX Unit) */
    req->server_name.offset = (uint32_t) ((uintptr_t) (c->req_pool + pool_off) - (uintptr_t) &req->server_name);
    if (host_value != NULL && host_value_len > 0) {
        /* Remove port number if present (e.g., "example.com:8080" -> "example.com") */
        size_t host_len = host_value_len;
        for (i = 0; i < host_value_len; i++) {
            if (host_value[i] == ':') {
                host_len = i;
                break;
            }
        }
        if (pool_off + host_len + 1 > REQ_POOL_MAX) return -1;
        memcpy(c->req_pool + pool_off, host_value, host_len);
        c->req_pool[pool_off + host_len] = '\0';
        req->server_name_length = (uint32_t) host_len;
        pool_off += host_len + 1;
    } else {
        /* Default to localhost if no Host header (like NGINX Unit) */
        if (pool_off + 10 > REQ_POOL_MAX) return -1;
        memcpy(c->req_pool + pool_off, "localhost", 9);
        c->req_pool[pool_off + 9] = '\0';
        req->server_name_length = 9;
        pool_off += 10;
    }
    /* Detect WebSocket handshake: GET with Upgrade: websocket, Connection: upgrade, Sec-WebSocket-Key, Sec-WebSocket-Version: 13 (match Unit) */
    for (i = 0; i < (int) req->fields_count; i++) {
        nxt_unit_field_t *hf = &req->fields[i];
        char *hname = (char *) nxt_unit_sptr_get(&hf->name);
        char *hval = (char *) nxt_unit_sptr_get(&hf->value);
        size_t nlen = (size_t) hf->name_length;
        size_t vlen = (size_t) hf->value_length;
        size_t k;
        if (nlen == 7 && strncasecmp(hname, "Upgrade", 7) == 0
            && vlen == 9 && strncasecmp(hval, "websocket", 9) == 0)
            req->websocket_handshake = (uint8_t) (req->websocket_handshake | 1);
        if (nlen == 10 && strncasecmp(hname, "Connection", 10) == 0 && vlen >= 7) {
            for (k = 0; k + 7 <= vlen; k++)
                if (strncasecmp(hval + k, "upgrade", 7) == 0) {
                    req->websocket_handshake = (uint8_t) (req->websocket_handshake | 2);
                    break;
                }
        }
        if (nlen == 18 && strncasecmp(hname, "Sec-WebSocket-Key", 18) == 0 && vlen > 0)
            req->websocket_handshake = (uint8_t) (req->websocket_handshake | 4);
        if (nlen == 21 && strncasecmp(hname, "Sec-WebSocket-Version", 21) == 0
            && vlen == 2 && (hval[0] == '1' && hval[1] == '3'))
            req->websocket_handshake = (uint8_t) (req->websocket_handshake | 8);
    }
    if (req->websocket_handshake != 15) req->websocket_handshake = 0;  /* need all four */

    c->req_pool_used = pool_off;
    c->request_buf.start = c->recv_buf;
    c->request_buf.free = c->recv_buf + c->recv_parsed;
    c->request_buf.end = c->recv_buf + c->recv_len;
    c->req_info = req_info;
    c->request = req;
    /* Only mark request ready when we have the full body (if Content-Length set) */
    if (c->recv_len >= c->recv_parsed + (size_t) req->content_length)
        c->request_ready = 1;
    return 0;
}

static int conn_dispatch_request(conn_t *c) {
    conn_t *prev = current_dispatch_conn;
    current_dispatch_conn = c;
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
    c->handler_done = 0;
#endif
    if (c->emb->init->callbacks.request_handler != NULL)
        c->emb->init->callbacks.request_handler(c->req_info);
#ifdef SCALANATIVE_MULTITHREADING_ENABLED
    /* If handler runs on another thread (e.g. BlockingHandler), wait until it calls response_send or request_done. */
    pthread_mutex_lock(&c->dispatch_mutex);
    while (!c->handler_done)
        pthread_cond_wait(&c->dispatch_cond, &c->dispatch_mutex);
    pthread_mutex_unlock(&c->dispatch_mutex);
#endif
    current_dispatch_conn = prev;
    return 0;
}

static int conn_send_response(conn_t *c) {
    nxt_unit_response_t *r;
    char status_line[64];
    int n, i;
    size_t len, body_len;

    if (nxt_fast_path(c->response_sent))
        return NXT_UNIT_OK;
    /* Note: We always build headers here because response_sent == 0 means we're starting a new response.
     * Reset send_len to 0 to clear any leftover data from a previous response. */
    c->send_len = 0;
    if (nxt_slow_path(c->response == NULL)) {
        snprintf(c->send_buf, sizeof(c->send_buf),
            "HTTP/1.1 500 Internal Server Error\r\nContent-Length: 0\r\nConnection: close\r\n\r\n");
        c->send_len = strlen(c->send_buf);
        goto send;
    }
    r = c->response;
    body_len = (size_t)(c->response_buf.free - c->response_buf.start);
    n = snprintf(status_line, sizeof(status_line), "HTTP/1.1 %u \r\n", (unsigned) r->status);
    if (nxt_slow_path(n <= 0 || (size_t) n >= sizeof(status_line))) return NXT_UNIT_ERROR;
    len = 0;
    if (len + (size_t) n <= SEND_BUF_SIZE)
        memcpy(c->send_buf + len, status_line, (size_t) n);
    len += (size_t) n;
    /* Add Content-Length so the client knows when the body ends (avoids curl hanging on empty body) */
    if (len + 32 <= SEND_BUF_SIZE) {
        n = snprintf(c->send_buf + len, SEND_BUF_SIZE - len, "Content-Length: %zu\r\n", body_len);
        if (n > 0) len += (size_t) n;
    }
    for (i = 0; i < (int) r->fields_count && len < SEND_BUF_SIZE - 4; i++) {
        nxt_unit_field_t *f = &r->fields[i];
        char *name = (char *) nxt_unit_sptr_get(&f->name);
        char *value = (char *) nxt_unit_sptr_get(&f->value);
        n = snprintf(c->send_buf + len, SEND_BUF_SIZE - len, "%.*s: %.*s\r\n",
            (int) f->name_length, name, (int) f->value_length, value);
        if (n <= 0) break;
        len += (size_t) n;
    }
    if (len + 2 <= SEND_BUF_SIZE) {
        c->send_buf[len++] = '\r';
        c->send_buf[len++] = '\n';
    }
    if (body_len > 0 && len + body_len <= SEND_BUF_SIZE) {
        /* Body may be at response_buf.start (e.g. send_buf+512); append after headers */
        memmove(c->send_buf + len, c->response_buf.start, body_len);
        len += body_len;
    }
    c->send_len = len;
send:
    n = (int) write(c->fd, c->send_buf, c->send_len);
    if (n > 0) {
        memmove(c->send_buf, c->send_buf + (size_t) n, c->send_len - (size_t) n);
        c->send_len -= (size_t) n;
    }
    if (c->send_len == 0)
        c->response_sent = 1;
    return NXT_UNIT_OK;
}
