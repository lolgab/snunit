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

#ifndef _NXT_UNIT_REQUEST_H_INCLUDED_
#define _NXT_UNIT_REQUEST_H_INCLUDED_

#include <inttypes.h>
#include "nxt_unit_sptr.h"
#include "nxt_unit_field.h"

#define NXT_UNIT_NONE_FIELD  0xFFFFFFFFU

struct nxt_unit_request_s {
    uint8_t               method_length;
    uint8_t               version_length;
    uint8_t               remote_length;
    uint8_t               local_addr_length;
    uint8_t               local_port_length;
    uint8_t               tls;
    uint8_t               websocket_handshake;
    uint8_t               app_target;
    uint32_t              server_name_length;
    uint32_t              target_length;
    uint32_t              path_length;
    uint32_t              query_length;
    uint32_t              fields_count;
    uint32_t              content_length_field;
    uint32_t              content_type_field;
    uint32_t              cookie_field;
    uint32_t              authorization_field;
    uint64_t              content_length;
    nxt_unit_sptr_t       method;
    nxt_unit_sptr_t       version;
    nxt_unit_sptr_t       remote;
    nxt_unit_sptr_t       local_addr;
    nxt_unit_sptr_t       local_port;
    nxt_unit_sptr_t       server_name;
    nxt_unit_sptr_t       target;
    nxt_unit_sptr_t       path;
    nxt_unit_sptr_t       query;
    nxt_unit_sptr_t       preread_content;
    nxt_unit_field_t      fields[];
};

#endif
