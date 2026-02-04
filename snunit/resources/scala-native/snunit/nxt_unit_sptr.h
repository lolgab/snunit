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

#ifndef _NXT_UNIT_SPTR_H_INCLUDED_
#define _NXT_UNIT_SPTR_H_INCLUDED_

#include <inttypes.h>
#include <stddef.h>
#include <string.h>

#include "nxt_unit_typedefs.h"

union nxt_unit_sptr_u {
    uint8_t   base[1];
    uint32_t  offset;
};

static inline void nxt_unit_sptr_set(nxt_unit_sptr_t *sptr, void *ptr) {
    sptr->offset = (uint32_t) ((uint8_t *) ptr - (uint8_t *) sptr);
}

/* Offset is from the address of the sptr itself (matches Scala: sptr + !sptr). */
static inline void *nxt_unit_sptr_get(nxt_unit_sptr_t *sptr) {
    return (uint8_t *) sptr + sptr->offset;
}

#endif
