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
 */

#ifndef _NXT_UNIT_H_INCLUDED_
#define _NXT_UNIT_H_INCLUDED_

#include <inttypes.h>
#include <sys/types.h>
#include <sys/uio.h>
#include <string.h>

#include "nxt_auto_config.h"
#include "nxt_version.h"
#include "nxt_unit_typedefs.h"
#include "nxt_unit_request.h"
#include "nxt_unit_response.h"

enum {
    NXT_UNIT_OK          = 0,
    NXT_UNIT_ERROR       = 1,
    NXT_UNIT_AGAIN       = 2,
    NXT_UNIT_CANCELLED   = 3,
};

enum {
    NXT_UNIT_LOG_ALERT   = 0,
    NXT_UNIT_LOG_ERR     = 1,
    NXT_UNIT_LOG_WARN    = 2,
    NXT_UNIT_LOG_NOTICE  = 3,
    NXT_UNIT_LOG_INFO    = 4,
    NXT_UNIT_LOG_DEBUG   = 5,
};

#define NXT_UNIT_INIT_ENV  "NXT_UNIT_INIT"
#define NXT_UNIT_SHARED_PORT_ID  ((uint16_t) 0xFFFFu)

struct nxt_unit_s { void *data; };

struct nxt_unit_ctx_s {
    void           *data;
    nxt_unit_t     *unit;
};

struct nxt_unit_port_id_s {
    pid_t   pid;
    uint32_t hash;
    uint16_t id;
};

struct nxt_unit_port_s {
    nxt_unit_port_id_t id;
    int   in_fd;
    int   out_fd;
    void *data;
};

struct nxt_unit_buf_s {
    char *start;
    char *free;
    char *end;
};

struct nxt_unit_request_info_s {
    nxt_unit_t         *unit;
    nxt_unit_ctx_t     *ctx;
    nxt_unit_port_t    *response_port;
    nxt_unit_request_t *request;
    nxt_unit_buf_t     *request_buf;
    nxt_unit_response_t *response;
    nxt_unit_buf_t     *response_buf;
    uint32_t           response_max_fields;
    nxt_unit_buf_t     *content_buf;
    uint64_t           content_length;
    int                content_fd;
    void               *data;
};

struct nxt_unit_callbacks_s {
    void     (*request_handler)(nxt_unit_request_info_t *req);
    void     (*data_handler)(nxt_unit_request_info_t *req);
    void     (*websocket_handler)(nxt_unit_websocket_frame_t *ws);
    void     (*close_handler)(nxt_unit_request_info_t *req);
    int      (*add_port)(nxt_unit_ctx_t *, nxt_unit_port_t *port);
    void     (*remove_port)(nxt_unit_t *, nxt_unit_ctx_t *, nxt_unit_port_t *port);
    void     (*remove_pid)(nxt_unit_t *, pid_t pid);
    void     (*quit)(nxt_unit_ctx_t *);
    void     (*shm_ack_handler)(nxt_unit_ctx_t *);
    ssize_t  (*port_send)(nxt_unit_ctx_t *, nxt_unit_port_t *port,
                 const void *buf, size_t buf_size,
                 const void *oob, size_t oob_size);
    ssize_t  (*port_recv)(nxt_unit_ctx_t *, nxt_unit_port_t *port,
                 void *buf, size_t buf_size, void *oob, size_t *oob_size);
    int      (*ready_handler)(nxt_unit_ctx_t *);
};

struct nxt_unit_init_s {
    void                 *data;
    void                 *ctx_data;
    int                  max_pending_requests;
    uint32_t             request_data_size;
    uint32_t             shm_limit;
    uint32_t             request_limit;
    nxt_unit_callbacks_t callbacks;
    nxt_unit_port_t      ready_port;
    uint32_t             ready_stream;
    nxt_unit_port_t      router_port;
    nxt_unit_port_t      read_port;
    int                  shared_port_fd;
    int                  shared_queue_fd;
    int                  log_fd;
};

typedef ssize_t (*nxt_unit_read_func_t)(nxt_unit_read_info_t *read_info,
    void *dst, size_t size);

struct nxt_unit_read_info_s {
    nxt_unit_read_func_t read;
    int                  eof;
    uint32_t             buf_size;
    void                 *data;
};

nxt_unit_ctx_t *nxt_unit_init(nxt_unit_init_t *init, const char *host, int port);
int nxt_unit_run(nxt_unit_ctx_t *ctx);
int nxt_unit_run_ctx(nxt_unit_ctx_t *ctx);
int nxt_unit_run_shared(nxt_unit_ctx_t *ctx);
nxt_unit_request_info_t *nxt_unit_dequeue_request(nxt_unit_ctx_t *ctx);
int nxt_unit_run_once(nxt_unit_ctx_t *ctx);
int nxt_unit_process_port_msg(nxt_unit_ctx_t *ctx, nxt_unit_port_t *port);
void nxt_unit_done(nxt_unit_ctx_t *ctx);
nxt_unit_ctx_t *nxt_unit_ctx_alloc(nxt_unit_ctx_t *, void *);
void nxt_unit_port_id_init(nxt_unit_port_id_t *port_id, pid_t pid, uint16_t id);
uint16_t nxt_unit_field_hash(const char *name, size_t name_length);
void nxt_unit_split_host(char *host_start, uint32_t host_length,
    char **name, uint32_t *name_length, char **port, uint32_t *port_length);
void nxt_unit_request_group_dup_fields(nxt_unit_request_info_t *req);

int nxt_unit_response_init(nxt_unit_request_info_t *req,
    uint16_t status, uint32_t max_fields_count, uint32_t max_fields_size);
int nxt_unit_response_realloc(nxt_unit_request_info_t *req,
    uint32_t max_fields_count, uint32_t max_fields_size);
int nxt_unit_response_is_init(nxt_unit_request_info_t *req);
int nxt_unit_response_add_field(nxt_unit_request_info_t *req,
    const char *name, uint8_t name_length,
    const char *value, uint32_t value_length);
int nxt_unit_response_add_content(nxt_unit_request_info_t *req,
    const void *src, uint32_t size);
int nxt_unit_response_send(nxt_unit_request_info_t *req);
int nxt_unit_response_is_sent(nxt_unit_request_info_t *req);
nxt_unit_buf_t *nxt_unit_response_buf_alloc(nxt_unit_request_info_t *req,
    uint32_t size);
int nxt_unit_request_is_websocket_handshake(nxt_unit_request_info_t *req);
int nxt_unit_response_upgrade(nxt_unit_request_info_t *req);
int nxt_unit_response_is_websocket(nxt_unit_request_info_t *req);
nxt_unit_request_info_t *nxt_unit_get_request_info_from_data(void *data);
int nxt_unit_buf_send(nxt_unit_buf_t *buf);
void nxt_unit_buf_free(nxt_unit_buf_t *buf);
nxt_unit_buf_t *nxt_unit_buf_next(nxt_unit_buf_t *buf);
uint32_t nxt_unit_buf_max(void);
uint32_t nxt_unit_buf_min(void);
int nxt_unit_response_write(nxt_unit_request_info_t *req, const void *start,
    size_t size);
ssize_t nxt_unit_response_write_nb(nxt_unit_request_info_t *req,
    const void *start, size_t size, size_t min_size);
int nxt_unit_response_write_cb(nxt_unit_request_info_t *req,
    nxt_unit_read_info_t *read_info);
ssize_t nxt_unit_request_read(nxt_unit_request_info_t *req, void *dst,
    size_t size);
ssize_t nxt_unit_request_readline_size(nxt_unit_request_info_t *req,
    size_t max_size);
void nxt_unit_request_done(nxt_unit_request_info_t *req, int rc);

int nxt_unit_websocket_send(nxt_unit_request_info_t *req, uint8_t opcode,
    uint8_t last, const void *start, size_t size);
int nxt_unit_websocket_sendv(nxt_unit_request_info_t *req, uint8_t opcode,
    uint8_t last, const struct iovec *iov, int iovcnt);
ssize_t nxt_unit_websocket_read(nxt_unit_websocket_frame_t *ws, void *dst,
    size_t size);
int nxt_unit_websocket_retain(nxt_unit_websocket_frame_t *ws);
void nxt_unit_websocket_done(nxt_unit_websocket_frame_t *ws);

void *nxt_unit_malloc(nxt_unit_ctx_t *ctx, size_t size);
void nxt_unit_free(nxt_unit_ctx_t *ctx, void *p);

#if defined __has_attribute && __has_attribute(format)
#define NXT_ATTR_FORMAT  __attribute__((format(printf, 3, 4)))
#else
#define NXT_ATTR_FORMAT
#endif

void nxt_unit_log(nxt_unit_ctx_t *ctx, int level, const char *fmt, ...) NXT_ATTR_FORMAT;
void nxt_unit_req_log(nxt_unit_request_info_t *req, int level,
    const char *fmt, ...) NXT_ATTR_FORMAT;

#endif /* _NXT_UNIT_H_INCLUDED_ */
