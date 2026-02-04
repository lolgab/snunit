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

#ifndef _NXT_UNIT_FIELD_H_INCLUDED_
#define _NXT_UNIT_FIELD_H_INCLUDED_

#include <inttypes.h>
#include "nxt_unit_sptr.h"

#define NXT_UNIT_HASH_CONTENT_LENGTH 0x1EA0
#define NXT_UNIT_HASH_CONTENT_TYPE   0x5F7D
#define NXT_UNIT_HASH_COOKIE         0x23F2
#define NXT_UNIT_HASH_HOST           0x69C8

struct nxt_unit_field_s {
    uint16_t              hash;
    uint8_t               skip:1;
    uint8_t               hopbyhop:1;
    uint8_t               name_length;
    uint32_t              value_length;
    nxt_unit_sptr_t       name;
    nxt_unit_sptr_t       value;
};

#endif
