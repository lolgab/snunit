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
#ifndef _NXT_UNIT_WEBSOCKET_H_INCLUDED_
#define _NXT_UNIT_WEBSOCKET_H_INCLUDED_

#include <inttypes.h>

#include "nxt_unit_typedefs.h"
#include "nxt_websocket_header.h"


struct nxt_unit_websocket_frame_s {
    nxt_unit_request_info_t  *req;

    uint64_t                  payload_len;
    nxt_websocket_header_t    *header;
    uint8_t                   *mask;

    nxt_unit_buf_t            *content_buf;
    uint64_t                  content_length;
};


#endif /* _NXT_UNIT_WEBSOCKET_H_INCLUDED_ */
