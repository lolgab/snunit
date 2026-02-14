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

#ifndef _NXT_UNIT_RESPONSE_H_INCLUDED_
#define _NXT_UNIT_RESPONSE_H_INCLUDED_


#include <inttypes.h>

#include "nxt_unit_sptr.h"
#include "nxt_unit_field.h"

struct nxt_unit_response_s {
    uint64_t              content_length;
    uint32_t              fields_count;
    uint32_t              piggyback_content_length;
    uint16_t              status;

    nxt_unit_sptr_t       piggyback_content;

    nxt_unit_field_t      fields[];
};


#endif /* _NXT_UNIT_RESPONSE_H_INCLUDED_ */
