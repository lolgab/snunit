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
 * Stub for vendored embed build (no Unit configure).
 */
#ifndef _NXT_AUTO_CONFIG_H_INCLUDED_
#define _NXT_AUTO_CONFIG_H_INCLUDED_

#define NXT_DEBUG 0

/* Branch prediction hints (from unit src/nxt_clang.h). */
#if defined(__GNUC__) || defined(__clang__)
#define nxt_expect(c, x)   __builtin_expect((long) (x), (c))
#define nxt_fast_path(x)   nxt_expect(1, x)
#define nxt_slow_path(x)   nxt_expect(0, x)
#else
#define nxt_expect(c, x)   (x)
#define nxt_fast_path(x)   (x)
#define nxt_slow_path(x)   (x)
#endif

#endif
