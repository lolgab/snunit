package snunit.unsafe

import scala.annotation.targetName
import scala.scalanative.posix.sys.types.pid_t
import scala.scalanative.unsafe.Nat._8
import scala.scalanative.unsafe._

/*
 * Thread context.
 *
 * First (main) context is provided 'for free'.  To receive and process
 * requests in other thread, one need to allocate context and use it
 * further in this thread.
 */
opaque type nxt_unit_ctx_t = CStruct2[Ptr[Byte], Ptr[nxt_unit_t]]

/*
 * Unit port identification structure.
 *
 * Each port can be uniquely identified by listen process id (pid) and port id.
 * This identification is required to refer the port from different process.
 */
opaque type nxt_unit_port_id_t = CStruct3[pid_t, CInt, CShort]

opaque type request_handler_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
inline def request_handler_t(inline f: Ptr[nxt_unit_request_info_t] => Unit): request_handler_t = f

opaque type data_handler_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]

opaque type websocket_handler_t = CFuncPtr1[Ptr[nxt_unit_websocket_frame_t], Unit]
inline def websocket_handler_t(inline f: Ptr[nxt_unit_websocket_frame_t] => Unit): websocket_handler_t = f

opaque type add_port_t = CFuncPtr2[Ptr[nxt_unit_ctx_t], Ptr[nxt_unit_port_t], CInt]
inline def add_port_t(inline f: (Ptr[nxt_unit_ctx_t], Ptr[nxt_unit_port_t]) => CInt): add_port_t = f

opaque type remove_port_t = CFuncPtr3[Ptr[nxt_unit_t], Ptr[nxt_unit_ctx_t], Ptr[nxt_unit_port_t], Unit]
inline def remove_port_t(
    inline f: (Ptr[nxt_unit_t], Ptr[nxt_unit_ctx_t], Ptr[nxt_unit_port_t]) => Unit
): remove_port_t = f

opaque type quit_t = CFuncPtr1[Ptr[nxt_unit_ctx_t], Unit]
inline def quit_t(inline f: Ptr[nxt_unit_ctx_t] => Unit): quit_t = f

// TODO Implement properly
opaque type close_handler_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
opaque type remove_pid_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
opaque type shm_ack_handler_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
opaque type port_send_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
opaque type port_recv_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
// end TODO

/*
 * Set of application-specific callbacks. Application may leave all optional
 * callbacks as NULL.
 */
opaque type nxt_unit_callbacks_t = CStruct11[
  request_handler_t,
  data_handler_t,
  websocket_handler_t,
  close_handler_t,
  add_port_t,
  remove_port_t,
  remove_pid_t,
  quit_t,
  shm_ack_handler_t,
  port_send_t,
  port_recv_t
]

opaque type nxt_unit_init_t = CStruct11[
  Ptr[Byte],
  Ptr[Byte],
  CInt,
  CInt,
  CInt,
  nxt_unit_callbacks_t,
  nxt_unit_port_t,
  CInt,
  nxt_unit_port_t,
  nxt_unit_port_t,
  CInt
]

given Tag[nxt_unit_init_t] = Tag.materializeCStruct11Tag[
  Ptr[Byte],
  Ptr[Byte],
  CInt,
  CInt,
  CInt,
  nxt_unit_callbacks_t,
  nxt_unit_port_t,
  CInt,
  nxt_unit_port_t,
  nxt_unit_port_t,
  CInt
]

opaque type nxt_websocket_header_t = CStruct3[
  Byte,
  Byte,
  CArray[Byte, _8]
]

export externs.*

@link("unit")
@extern
object externs {
  /*
   * Initialize Unit application library with necessary callbacks and
   * ready/reply port parameters, send 'READY' response to master.
   */
  def nxt_unit_init(init: Ptr[nxt_unit_init_t]): Ptr[nxt_unit_ctx_t] = extern

  /*
   * Main function useful in case when application does not have it's own
   * event loop. nxt_unit_run() starts infinite message wait and process loop.
   *
   *  for (;;) {
   *      app_lib->port_recv(...);
   *      nxt_unit_process_msg(...);
   *  }
   *
   * The normally function returns when QUIT message received from Unit.
   */
  def nxt_unit_run(ctx: Ptr[nxt_unit_ctx_t]): CInt = extern

  def nxt_unit_run_once(ctx: Ptr[nxt_unit_ctx_t]): CInt = extern

  def nxt_unit_process_port_msg(ctx: Ptr[nxt_unit_ctx_t], port: Ptr[nxt_unit_port_t]): CInt = extern

  def nxt_unit_done(ctx: Ptr[nxt_unit_ctx_t]): CInt = extern

  /*
   * Allocate response structure capable to store limited numer of fields.
   * The structure may be accessed directly via req->response pointer or
   * filled step-by-step using functions add_field and add_content.
   */
  def nxt_unit_response_init(
      req: Ptr[nxt_unit_request_info_t],
      status: CShort,
      max_fields_count: CInt,
      max_fields_size: CInt
  ): CInt = extern

  def nxt_unit_response_add_field(
      req: Ptr[nxt_unit_request_info_t],
      name: CString,
      name_length: Byte,
      value: CString,
      value_length: Int
  ): CInt = extern

  def nxt_unit_response_add_content(req: Ptr[nxt_unit_request_info_t], src: CString, size: Int): CInt = extern

  def nxt_unit_response_send(req: Ptr[nxt_unit_request_info_t]): CInt = extern

  def nxt_unit_response_buf_alloc(req: Ptr[nxt_unit_request_info_t], size: CInt): Ptr[nxt_unit_buf_t] = extern

  def nxt_unit_request_is_websocket_handshake(req: Ptr[nxt_unit_request_info_t]): CInt = extern

  def nxt_unit_response_upgrade(req: Ptr[nxt_unit_request_info_t]): CInt = extern

  def nxt_unit_response_write_nb(
      req: Ptr[nxt_unit_request_info_t],
      start: CString,
      size: CSSize,
      min_size: CSSize
  ): CSSize = extern

  def nxt_unit_buf_send(buf: Ptr[nxt_unit_buf_t]): CInt = extern

  def nxt_unit_request_read(req: Ptr[nxt_unit_request_info_t], dst: Ptr[Byte], size: CSSize): CSSize = extern

  def nxt_unit_request_done(req: Ptr[nxt_unit_request_info_t], rc: CInt): Unit = extern

  def nxt_unit_websocket_read(ws: Ptr[nxt_unit_websocket_frame_t], dest: Ptr[Byte], size: CSSize): CSSize = extern

  def nxt_unit_websocket_send(
      req: Ptr[nxt_unit_request_info_t],
      opcode: Byte,
      last: Byte,
      start: Ptr[Byte],
      size: CSSize
  ): CInt = extern

  def nxt_unit_websocket_done(ws: Ptr[nxt_unit_websocket_frame_t]): Unit = extern

  def nxt_unit_log(ctx: Ptr[nxt_unit_ctx_t], level: Int, fmt: CString): Unit = extern
}

final val NXT_UNIT_OK = 0
final val NXT_UNIT_ERROR = 1
final val NXT_UNIT_AGAIN = 2
final val NXT_UNIT_CANCELLED = 3

final val NXT_UNIT_LOG_ALERT = 0
final val NXT_UNIT_LOG_ERR = 1
final val NXT_UNIT_LOG_WARN = 2
final val NXT_UNIT_LOG_NOTICE = 3
final val NXT_UNIT_LOG_INFO = 4
final val NXT_UNIT_LOG_DEBUG = 5

inline def nxt_unit_warn(ctx: Ptr[nxt_unit_ctx_t], message: String): Unit =
  Zone { implicit z =>
    nxt_unit_log(ctx, NXT_UNIT_LOG_WARN, toCString(message))
  }

/*
 * Mostly opaque structure with library state.
 *
 * Only user inline defined 'data' pointer exposed here.  The rest is unit
 * implementation specific and hidden.
 */
opaque type nxt_unit_t = CStruct1[Ptr[Byte]]

extension (ptr: Ptr[nxt_unit_t]) {
  inline def data: Ptr[Byte] = ptr._1
  inline def data_=(v: Ptr[Byte]): Unit = ptr._1 = v
}

extension (ptr: Ptr[nxt_unit_ctx_t]) {
  @targetName("ctx_t_data")
  inline def data: Ptr[Byte] = ptr._1
  @targetName("ctx_t_data_=")
  inline def data_=(v: Ptr[Byte]): Unit = ptr._1 = v

  inline def unit: Ptr[nxt_unit_t] = ptr._2
  inline def unit_=(v: Ptr[nxt_unit_t]): Unit = ptr._2 = v
}

extension (ptr: Ptr[nxt_unit_port_id_t]) {
  inline def pid: pid_t = ptr._1
  inline def pid_=(v: pid_t): Unit = ptr._1 = v

  inline def hash: CInt = ptr._2
  inline def hash_=(v: CInt): Unit = ptr._2 = v

  inline def id: CShort = ptr._3
  inline def id_=(v: CShort): Unit = ptr._3 = v
}

/*
 * unit provides port storage which is able to store and find the following
 * data structures.
 */
opaque type nxt_unit_port_t = CStruct4[nxt_unit_port_id_t, Int, Int, Ptr[Byte]]

extension (ptr: Ptr[nxt_unit_port_t]) {
  inline def id: nxt_unit_port_id_t = ptr._1
  inline def id_=(v: nxt_unit_port_id_t): Unit = ptr._1 = v

  inline def in_fd: CInt = ptr._2
  inline def in_fd_=(v: CInt): Unit = ptr._2 = v

  inline def out_fd: CInt = ptr._3
  inline def out_fd_=(v: CInt): Unit = ptr._3 = v

  @targetName("port_t_data")
  inline def data: Ptr[Byte] = ptr._4
  @targetName("port_t_data_=")
  inline def data_=(v: Ptr[Byte]): Unit = ptr._4 = v
}

opaque type nxt_unit_buf_t = CStruct3[Ptr[CChar], Ptr[CChar], Ptr[CChar]]

extension (ptr: Ptr[nxt_unit_buf_t]) {
  inline def start: Ptr[CChar] = ptr._1
  inline def start_=(v: Ptr[CChar]): Unit = ptr._1 = v

  inline def free: Ptr[CChar] = ptr._2
  inline def free_=(v: Ptr[CChar]): Unit = ptr._2 = v

  inline def end: Ptr[CChar] = ptr._2
  inline def end_=(v: Ptr[CChar]): Unit = ptr._2 = v
}

opaque type nxt_unit_field_t = CStruct6[
  CShort,
  Byte,
  Byte,
  CInt,
  nxt_unit_sptr_t,
  nxt_unit_sptr_t
]

extension (ptr: Ptr[nxt_unit_field_t]) {
  inline def hash: CShort = ptr._1

  inline def skip: Byte = (ptr._2 & 1.toByte).toByte

  inline def hopbyhop: Byte = ((ptr._2 >> 1) & 1.toByte).toByte

  inline def name_length: Byte = ptr._3

  inline def value_length: CInt = ptr._4

  inline def name: Ptr[Byte] = nxt_unit_sptr_get(ptr.at5)

  @targetName("field_t_value")
  inline def value: Ptr[Byte] = nxt_unit_sptr_get(ptr.at6)
}

opaque type nxt_unit_request_t = CArray[CChar, Nat.Digit2[Nat._9, Nat._6]]

extension (ptr: Ptr[nxt_unit_request_t]) {
  inline def method_length: Byte = !ptr.at(0)

  inline def version_length: Byte = !ptr.at(1)

  inline def remote_length: Byte = !ptr.at(2)

  inline def local_addr_length: Byte = !ptr.at(3)

  inline def local_port_length: Byte = !ptr.at(4)

  inline def tls: Byte = !ptr.at(5)

  inline def websocket_handshake: Byte = !ptr.at(6)

  inline def app_target: Byte = !ptr.at(7)

  inline def server_name_length: CInt = !ptr.at(8).asInstanceOf[Ptr[CInt]]

  inline def target_length: CInt = !ptr.at(12).asInstanceOf[Ptr[CInt]]

  inline def path_length: CInt = !ptr.at(16).asInstanceOf[Ptr[CInt]]

  inline def query_length: CInt = !ptr.at(20).asInstanceOf[Ptr[CInt]]

  inline def fields_count: CInt = !ptr.at(24).asInstanceOf[Ptr[CInt]]

  inline def content_length_field: CInt = !ptr.at(28).asInstanceOf[Ptr[CInt]]

  inline def content_type_field: CInt = !ptr.at(32).asInstanceOf[Ptr[CInt]]

  inline def cookie_field: CInt = !ptr.at(36).asInstanceOf[Ptr[CInt]]

  inline def authorization_field: CInt = !ptr.at(40).asInstanceOf[Ptr[CInt]]

  inline def content_length: CLongLong = !ptr.at(48).asInstanceOf[Ptr[CLongLong]] // CUnsignedLongLong

  @targetName("request_t_method")
  inline def method: Ptr[Byte] = nxt_unit_sptr_get(ptr.at(56).asInstanceOf[Ptr[nxt_unit_sptr_t]])

  inline def version: Ptr[Byte] = nxt_unit_sptr_get(ptr.at(60).asInstanceOf[Ptr[nxt_unit_sptr_t]])

  inline def remote: Ptr[Byte] = nxt_unit_sptr_get(ptr.at(64).asInstanceOf[Ptr[nxt_unit_sptr_t]])

  inline def local_addr: Ptr[Byte] = nxt_unit_sptr_get(ptr.at(68).asInstanceOf[Ptr[nxt_unit_sptr_t]])

  inline def local_port: Ptr[Byte] = nxt_unit_sptr_get(ptr.at(72).asInstanceOf[Ptr[nxt_unit_sptr_t]])

  inline def server_name: Ptr[Byte] = nxt_unit_sptr_get(ptr.at(76).asInstanceOf[Ptr[nxt_unit_sptr_t]])

  inline def target: Ptr[Byte] = nxt_unit_sptr_get(ptr.at(80).asInstanceOf[Ptr[nxt_unit_sptr_t]])

  inline def path: Ptr[Byte] = nxt_unit_sptr_get(ptr.at(84).asInstanceOf[Ptr[nxt_unit_sptr_t]])

  inline def query: Ptr[Byte] = nxt_unit_sptr_get(ptr.at(88).asInstanceOf[Ptr[nxt_unit_sptr_t]])

  inline def preread_content: Ptr[Byte] = nxt_unit_sptr_get(ptr.at(92).asInstanceOf[Ptr[nxt_unit_sptr_t]])

  inline def fields: Ptr[nxt_unit_field_t] =
    nxt_unit_sptr_get(ptr.at(96).asInstanceOf[Ptr[nxt_unit_sptr_t]]).asInstanceOf[Ptr[nxt_unit_field_t]]
}

opaque type nxt_unit_response_t = CStruct6[CLongInt, CInt, CInt, CShort, nxt_unit_sptr_t, nxt_unit_field_t]

extension (ptr: Ptr[nxt_unit_response_t]) {
  @targetName("response_t_content_length")
  inline def content_length: CLongInt = ptr._1
  @targetName("response_t_content_length_=")
  inline def content_length_=(v: CLongInt): Unit = ptr._1 = v

  @targetName("response_t_fields_count")
  inline def fields_count: CInt = ptr._2
  @targetName("response_t_fields_count_=")
  inline def fields_count_=(v: CInt): Unit = ptr._2 = v

  inline def piggyback_content_length: CInt = ptr._3
  inline def piggyback_content_length_=(v: CInt): Unit = ptr._3 = v

  inline def status: Short = ptr._4
  inline def status_=(v: Short): Unit = ptr._4 = v

  inline def piggyback_content: Ptr[nxt_unit_sptr_t] = ptr.at5
  inline def piggyback_content_=(v: Ptr[nxt_unit_sptr_t]): Unit = ptr._5 = !v

  @targetName("response_t_fields")
  inline def fields: Ptr[nxt_unit_field_t] = ptr.at6
}

opaque type nxt_unit_request_info_t = CStruct12[
  Ptr[nxt_unit_t],
  Ptr[nxt_unit_ctx_t],
  Ptr[nxt_unit_port_t],
  Ptr[nxt_unit_request_t],
  Ptr[nxt_unit_buf_t],
  Ptr[nxt_unit_response_t],
  Ptr[nxt_unit_buf_t],
  CInt,
  Ptr[nxt_unit_buf_t],
  CLongLong, // CUnsignedLongLong,
  CInt,
  Ptr[Byte]
]

extension (ptr: Ptr[nxt_unit_request_info_t]) {
  // inline def unit: Ptr[nxt_unit_t] = !ptr.at(0)
  // inline def unit_=(v: Ptr[nxt_unit_t]): Unit = !ptr.at1 = v

  // inline def ctx: Ptr[nxt_unit_ctx_t] = ptr._2
  // inline def ctx_=(v: Ptr[nxt_unit_ctx_t]): Unit = !ptr.at2 = v

  // inline def response_port: Ptr[nxt_unit_port_t] = ptr._3
  // inline def response_port_=(v: Ptr[nxt_unit_port_t]): Unit = !ptr.at3 = v

  inline def request: Ptr[nxt_unit_request_t] = ptr._4
  // inline def request_=(v: Ptr[nxt_unit_request_t]): Unit = !ptr.at4 = v

  // inline def request_buf: Ptr[nxt_unit_buf_t] = ptr._5
  // inline def request_buf_=(v: Ptr[nxt_unit_buf_t]): Unit = !ptr.at5 = v

  // inline def response: Ptr[nxt_unit_response_t] = ptr._6
  // inline def response_=(v: Ptr[nxt_unit_response_t]): Unit = !ptr.at6 = v

  // inline def response_buf: Ptr[nxt_unit_buf_t] = ptr._7
  // inline def response_buf_=(v: Ptr[nxt_unit_buf_t]): Unit = !ptr.at7 = v

  // inline def response_max_fields: CInt = ptr._8
  // inline def response_max_fields_=(v: CInt): Unit = !ptr.at8 = v

  // inline def content_buf: Ptr[nxt_unit_buf_t] = ptr._9
  // inline def content_buf_=(v: Ptr[nxt_unit_buf_t]): Unit = !ptr.at9 = v

  // inline def content_length: CLongLong = ptr._10 // CUnsignedLongLong
  // inline def content_length_=(v: CLongLong): Unit = !ptr.at10 = v // CUnsignedLongLong

  // inline def content_fd: CInt = ptr._11
  // inline def content_fd_=(v: CInt): Unit = !ptr.at11 = v

  // inline def data: Ptr[Byte] = ptr._12
  // inline def data_=(v: Ptr[Byte]): Unit = !ptr.at12 = v
}

extension (ptr: Ptr[nxt_unit_callbacks_t]) {
  inline def request_handler: request_handler_t = ptr._1
  inline def request_handler_=(v: request_handler_t): Unit = ptr._1 = v

  // inline def data_handler: data_handler_t = ptr._2
  // inline def data_handler_=(v: data_handler_t): Unit = ptr._2 = v

  inline def websocket_handler: websocket_handler_t = ptr._3
  inline def websocket_handler_=(v: websocket_handler_t): Unit = ptr._3 = v

  inline def add_port: add_port_t = ptr._5
  inline def add_port_=(v: add_port_t): Unit = ptr._5 = v

  inline def remove_port: remove_port_t = ptr._6
  inline def remove_port_=(v: remove_port_t): Unit = ptr._6 = v

  inline def quit: quit_t = ptr._8
  inline def quit_=(v: quit_t): Unit = ptr._8 = v
}

extension (ptr: Ptr[nxt_unit_init_t]) {
  @targetName("init_t_data")
  inline def data: Ptr[Byte] = ptr._1
  @targetName("init_t_data_=")
  inline def data_=(v: Ptr[Byte]): Unit = ptr._1 = v

  inline def ctx_data: Ptr[Byte] = ptr._2
  inline def ctx_data_=(v: Ptr[Byte]): Unit = ptr._2 = v

  inline def max_pending_requests: CInt = ptr._3
  inline def max_pending_requests_=(v: CInt): Unit = ptr._3 = v

  inline def request_data_size: CInt = ptr._4
  inline def request_data_size_=(v: CInt): Unit = ptr._4 = v

  inline def shm_limit: CInt = ptr._5
  inline def shm_limit_=(v: CInt): Unit = ptr._5 = v

  inline def callbacks: Ptr[nxt_unit_callbacks_t] = ptr.at6
  inline def callbacks_=(v: Ptr[nxt_unit_callbacks_t]): Unit = ptr._6 = v

  inline def ready_port: Ptr[nxt_unit_port_t] = ptr.at7
  inline def ready_port_=(v: Ptr[nxt_unit_port_t]): Unit = ptr._7 = v

  inline def ready_stream: CInt = ptr._8
  inline def ready_stream_=(v: CInt): Unit = ptr._8 = v

  inline def router_port: Ptr[nxt_unit_port_t] = ptr.at9
  inline def router_port_=(v: Ptr[nxt_unit_port_t]): Unit = ptr._9 = v

  inline def read_port: Ptr[nxt_unit_port_t] = ptr.at10
  inline def read_port_=(v: Ptr[nxt_unit_port_t]): Unit = ptr._10 = v

  inline def log_fd: CInt = ptr._11
  inline def log_fd_=(v: CInt): Unit = ptr._11 = v
}

extension (ptr: Ptr[nxt_websocket_header_t]) {
  // Assuming little endianess
  // Check NGINX Unit codebase to support big endian platforms as well
  @targetName("websocket_header_t_opcode")
  inline def opcodeInternal: Byte =
    (ptr._1 & ((1 << 4) - 1)).toByte
  @targetName("websocket_header_t_rsv3")
  inline def rsv3: Byte =
    ((ptr._1 & (1 << 4)) >> 4).toByte
  @targetName("websocket_header_t_rsv2")
  inline def rsv2: Byte =
    ((ptr._1 & (1 << 5)) >> 5).toByte
  @targetName("websocket_header_t_rsv1")
  inline def rsv1: Byte =
    ((ptr._1 & (1 << 6)) >> 6).toByte
  @targetName("websocket_header_t_fin")
  inline def fin: Byte =
    ((ptr._1 & (1 << 7)) >> 7).toByte
  @targetName("websocket_header_t_payload_len")
  inline def payload_len: Byte =
    (ptr._2 & ((1 << 7) - 1)).toByte
  @targetName("websocket_header_t_mask")
  inline def mask: Byte =
    ((ptr._2 & (1 << 7)) >> 7).toByte
  @targetName("websocket_header_t_payload_len_")
  inline def payload_len_ : Ptr[CArray[Byte, _8]] = ptr.at3
}

opaque type nxt_unit_websocket_frame_t = CStruct6[
  Ptr[nxt_unit_request_info_t],
  CLongInt,
  Ptr[nxt_websocket_header_t],
  Ptr[Byte],
  Ptr[nxt_unit_buf_t],
  CLongLong // CUnsignedLongLong
]

extension (ptr: Ptr[nxt_unit_websocket_frame_t]) {
  inline def req: Ptr[nxt_unit_request_info_t] = ptr._1
  inline def payload_len: CLongInt = ptr._2
  @inline def header: Ptr[nxt_websocket_header_t] = ptr._3
  inline def mask: Ptr[Byte] = ptr._4
  inline def content_buf: Ptr[nxt_unit_buf_t] = ptr._5
  @targetName("frame_content_length")
  inline def content_length: CLongLong = ptr._6 // CUnsignedLongLong
}
