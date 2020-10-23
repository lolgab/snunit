package snunit.unsafe

import scala.scalanative.posix.sys.types.pid_t
import scala.scalanative.unsafe.Nat._8
import scala.scalanative.unsafe._

import snunit.unsafe.nxt_unit_sptr._

@link("unit")
@extern
object CApi {
  /*
   * Mostly opaque structure with library state.
   *
   * Only user defined 'data' pointer exposed here.  The rest is unit
   * implementation specific and hidden.
   */
  type nxt_unit_t = CStruct1[Ptr[Byte]]

  /*
   * Thread context.
   *
   * First (main) context is provided 'for free'.  To receive and process
   * requests in other thread, one need to allocate context and use it
   * further in this thread.
   */
  type nxt_unit_ctx_t = CStruct2[Ptr[Byte], Ptr[nxt_unit_t]]

  /*
   * Unit port identification structure.
   *
   * Each port can be uniquely identified by listen process id (pid) and port id.
   * This identification is required to refer the port from different process.
   */
  type nxt_unit_port_id_t = CStruct3[pid_t, CInt, CShort]

  /*
   * unit provides port storage which is able to store and find the following
   * data structures.
   */
  type nxt_unit_port_t = CStruct4[nxt_unit_port_id_t, Int, Int, Ptr[Byte]]

  type nxt_unit_buf_t = CStruct3[Ptr[CChar], Ptr[CChar], Ptr[CChar]]

  type nxt_unit_field_t = CStruct6[
    CShort,
    Byte,
    Byte,
    CInt,
    nxt_unit_sptr_t,
    nxt_unit_sptr_t
  ]

  type nxt_unit_response_t = CStruct6[
    CLongInt,
    CInt,
    CInt,
    CShort,
    nxt_unit_sptr_t,
    nxt_unit_field_t
  ]

  type nxt_unit_request_info_t = CStruct12[
    Ptr[nxt_unit_t],
    Ptr[nxt_unit_ctx_t],
    Ptr[nxt_unit_port_t],
    Ptr[Byte], // nxt_unit_request_t
    Ptr[nxt_unit_buf_t],
    Ptr[nxt_unit_response_t],
    Ptr[nxt_unit_buf_t],
    CInt,
    Ptr[nxt_unit_buf_t],
    CLongInt,
    CInt,
    Ptr[Byte]
  ]

  type request_handler_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
  type data_handler_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
  type websocket_handler_t = CFuncPtr1[Ptr[nxt_unit_websocket_frame_t], Unit]
  type add_port_t = CFuncPtr2[Ptr[nxt_unit_ctx_t], Ptr[nxt_unit_port_t], CInt]
  type remove_port_t = CFuncPtr2[Ptr[nxt_unit_t], Ptr[nxt_unit_port_t], Unit]
  type quit_t = CFuncPtr1[Ptr[nxt_unit_ctx_t], Unit]

  // TODO Implement properly
  type close_handler_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
  type remove_pid_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
  type shm_ack_handler_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
  type port_send_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
  type port_recv_t = CFuncPtr1[Ptr[nxt_unit_request_info_t], Unit]
  // end TODO

  /*
   * Set of application-specific callbacks. Application may leave all optional
   * callbacks as NULL.
   */
  type nxt_unit_callbacks_t = CStruct11[
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

  type nxt_unit_init_t = CStruct11[
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

  type nxt_websocket_header_t = CStruct8[
    Byte,
    Byte,
    Byte,
    Byte,
    Byte,
    Byte,
    Byte,
    CArray[Byte, _8]
  ]

  type nxt_unit_websocket_frame_t = CStruct6[
    Ptr[nxt_unit_request_info_t],
    CLongInt,
    Ptr[nxt_websocket_header_t],
    Ptr[Byte],
    Ptr[nxt_unit_buf_t],
    CLongInt
  ]

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

  def nxt_unit_response_write_nb(
      req: Ptr[nxt_unit_request_info_t],
      start: CString,
      size: CSize,
      min_size: CSize
  ): CSize = extern

  def nxt_unit_buf_send(buf: Ptr[nxt_unit_buf_t]): CInt = extern

  def nxt_unit_request_read(req: Ptr[nxt_unit_request_info_t], dst: Ptr[Byte], size: CSize): CSize = extern

  def nxt_unit_request_done(req: Ptr[nxt_unit_request_info_t], rc: CInt): Unit = extern

  def nxt_unit_websocket_read(ws: Ptr[nxt_unit_websocket_frame_t], dest: Ptr[Byte], size: CSize): CSize = extern

  def nxt_unit_websocket_send(
      req: Ptr[nxt_unit_request_info_t],
      opcode: Byte,
      last: Byte,
      start: Ptr[Byte],
      size: CSize
  ): CInt = extern

  def nxt_unit_websocket_done(ws: Ptr[nxt_unit_websocket_frame_t]): Unit = extern

  def nxt_unit_log(ctx: Ptr[nxt_unit_ctx_t], level: Int, fmt: CString): Unit = extern
}
object CApiOps {
  import CApi._

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

  def nxt_unit_warn(ctx: Ptr[nxt_unit_ctx_t], message: String): Unit =
    Zone { implicit z =>
      nxt_unit_log(ctx, NXT_UNIT_LOG_WARN, toCString(message))
    }

  implicit class nxt_unit_t_ops(private val ptr: Ptr[nxt_unit_t]) extends AnyVal {
    def data: Ptr[Byte] = ptr._1
    def data_=(v: Ptr[Byte]): Unit = ptr._1 = v
  }

  implicit class nxt_unit_ctx_t_ops(private val ptr: Ptr[nxt_unit_ctx_t]) extends AnyVal {
    def data: Ptr[Byte] = ptr._1
    def data_=(v: Ptr[Byte]): Unit = ptr._1 = v

    def unit: Ptr[nxt_unit_t] = ptr._2
    def unit_=(v: Ptr[nxt_unit_t]): Unit = ptr._2 = v
  }

  implicit class nxt_unit_port_id_t_ops(private val ptr: Ptr[nxt_unit_port_id_t]) extends AnyVal {
    def pid: pid_t = ptr._1
    def pid_=(v: pid_t): Unit = ptr._1 = v

    def hash: CInt = ptr._2
    def hash_=(v: CInt): Unit = ptr._2 = v

    def id: CShort = ptr._3
    def id_=(v: CShort): Unit = ptr._3 = v
  }

  implicit class nxt_unit_port_t_ops(private val ptr: Ptr[nxt_unit_port_t]) extends AnyVal {
    def id: Ptr[nxt_unit_port_id_t] = ptr.at1
    def id_=(v: Ptr[nxt_unit_port_id_t]): Unit = ptr._1 = v

    def in_fd: CInt = ptr._2
    def in_fd_=(v: CInt): Unit = ptr._2 = v

    def out_fd: CInt = ptr._3
    def out_fd_=(v: CInt): Unit = ptr._3 = v

    def data: Ptr[Byte] = ptr._4
    def data_=(v: Ptr[Byte]): Unit = ptr._4 = v
  }

  implicit class nxt_unit_buf_t_ops(private val ptr: Ptr[nxt_unit_buf_t]) extends AnyVal {
    def start: Ptr[CChar] = ptr._1
    def start_=(v: Ptr[CChar]): Unit = ptr._1 = v

    def free: Ptr[CChar] = ptr._2
    def free_=(v: Ptr[CChar]): Unit = ptr._2 = v

    def end: Ptr[CChar] = ptr._2
    def end_=(v: Ptr[CChar]): Unit = ptr._2 = v
  }

  implicit class nxt_unit_field_t_ops(private val ptr: Ptr[nxt_unit_field_t]) extends AnyVal {
    def hash: CShort = ptr._1

    def skip: Byte = (ptr._2 & 1.toByte).toByte

    def hopbyhop: Byte = ((ptr._2 >> 1) & 1.toByte).toByte

    def name_length: Byte = ptr._3

    def value_length: CInt = ptr._4

    def name: Ptr[Byte] = nxt_unit_sptr_get(ptr.at5)

    def value: Ptr[Byte] = nxt_unit_sptr_get(ptr.at6)
  }

  trait nxt_unit_request_t
  implicit class nxt_unit_request_t_ops(private val ptr: Ptr[nxt_unit_request_t]) extends AnyVal {
    def method_length: Byte = !ptr.asInstanceOf[Ptr[Byte]]

    def version_length: Byte = !(ptr.asInstanceOf[Ptr[Byte]] + 1).asInstanceOf[Ptr[Byte]]

    def remote_length: Byte = !(ptr.asInstanceOf[Ptr[Byte]] + 2).asInstanceOf[Ptr[Byte]]

    def local_length: Byte = !(ptr.asInstanceOf[Ptr[Byte]] + 3).asInstanceOf[Ptr[Byte]]

    def tls: Byte = !(ptr.asInstanceOf[Ptr[Byte]] + 4).asInstanceOf[Ptr[Byte]]

    def websocket_handshake: Byte = !(ptr.asInstanceOf[Ptr[Byte]] + 5).asInstanceOf[Ptr[Byte]]

    def app_target: Byte = !(ptr.asInstanceOf[Ptr[Byte]] + 6).asInstanceOf[Ptr[Byte]]

    def server_name_length: CInt = !(ptr.asInstanceOf[Ptr[Byte]] + 8).asInstanceOf[Ptr[CInt]]

    def target_length: CInt = !(ptr.asInstanceOf[Ptr[Byte]] + 12).asInstanceOf[Ptr[CInt]]

    def path_length: CInt = !(ptr.asInstanceOf[Ptr[Byte]] + 16).asInstanceOf[Ptr[CInt]]

    def query_length: CInt = !(ptr.asInstanceOf[Ptr[Byte]] + 20).asInstanceOf[Ptr[CInt]]

    def fields_count: CInt = !(ptr.asInstanceOf[Ptr[Byte]] + 24).asInstanceOf[Ptr[CInt]]

    def content_length_field: CInt = !(ptr.asInstanceOf[Ptr[Byte]] + 28).asInstanceOf[Ptr[CInt]]

    def content_type_field: CInt = !(ptr.asInstanceOf[Ptr[Byte]] + 32).asInstanceOf[Ptr[CInt]]

    def cookie_field: CInt = !(ptr.asInstanceOf[Ptr[Byte]] + 36).asInstanceOf[Ptr[CInt]]

    def content_length: CLongInt = !(ptr.asInstanceOf[Ptr[Byte]] + 40).asInstanceOf[Ptr[CLongInt]]

    def method: Ptr[Byte] = nxt_unit_sptr_get((ptr.asInstanceOf[Ptr[Byte]] + 48).asInstanceOf[Ptr[nxt_unit_sptr_t]])

    def version: Ptr[Byte] = nxt_unit_sptr_get((ptr.asInstanceOf[Ptr[Byte]] + 52).asInstanceOf[Ptr[nxt_unit_sptr_t]])

    def remote: Ptr[Byte] = nxt_unit_sptr_get((ptr.asInstanceOf[Ptr[Byte]] + 56).asInstanceOf[Ptr[nxt_unit_sptr_t]])

    def local: Ptr[Byte] = nxt_unit_sptr_get((ptr.asInstanceOf[Ptr[Byte]] + 60).asInstanceOf[Ptr[nxt_unit_sptr_t]])

    def server_name: Ptr[Byte] =
      nxt_unit_sptr_get((ptr.asInstanceOf[Ptr[Byte]] + 64).asInstanceOf[Ptr[nxt_unit_sptr_t]])

    def target: Ptr[Byte] = nxt_unit_sptr_get((ptr.asInstanceOf[Ptr[Byte]] + 68).asInstanceOf[Ptr[nxt_unit_sptr_t]])

    def path: Ptr[Byte] = nxt_unit_sptr_get((ptr.asInstanceOf[Ptr[Byte]] + 72).asInstanceOf[Ptr[nxt_unit_sptr_t]])

    def query: Ptr[Byte] = nxt_unit_sptr_get((ptr.asInstanceOf[Ptr[Byte]] + 76).asInstanceOf[Ptr[nxt_unit_sptr_t]])

    def preread_content: Ptr[Byte] =
      nxt_unit_sptr_get((ptr.asInstanceOf[Ptr[Byte]] + 80).asInstanceOf[Ptr[nxt_unit_sptr_t]])

    def fields: Ptr[nxt_unit_field_t] = (ptr.asInstanceOf[Ptr[Byte]] + 84).asInstanceOf[Ptr[nxt_unit_field_t]]
  }

  implicit class nxt_unit_response_t_ops(private val ptr: Ptr[nxt_unit_response_t]) extends AnyVal {
    def content_length: CLongInt = ptr._1
    def content_length_=(v: CLongInt): Unit = ptr._1 = v

    def fields_count: CInt = ptr._2
    def fields_count_=(v: CInt): Unit = ptr._2 = v

    def piggyback_content_length: CInt = ptr._3
    def piggyback_content_length_=(v: CInt): Unit = ptr._3 = v

    def status: Short = ptr._4
    def status_=(v: Short): Unit = ptr._4 = v

    def piggyback_content: Ptr[nxt_unit_sptr_t] = ptr.at5
    def piggyback_content_=(v: Ptr[nxt_unit_sptr_t]): Unit = ptr._5 = !v

    def fields: Ptr[nxt_unit_field_t] = ptr.at6
  }

  implicit class nxt_unit_request_info_t_ops(private val ptr: Ptr[nxt_unit_request_info_t]) extends AnyVal {
    def unit: Ptr[nxt_unit_t] = ptr._1
    def unit_=(v: Ptr[nxt_unit_t]): Unit = ptr._1 = v

    def ctx: Ptr[nxt_unit_ctx_t] = ptr._2
    def ctx_=(v: Ptr[nxt_unit_ctx_t]): Unit = ptr._2 = v

    def response_port: Ptr[nxt_unit_port_t] = ptr._3
    def response_port_=(v: Ptr[nxt_unit_port_t]): Unit = ptr._3 = v

    def request: Ptr[nxt_unit_request_t] = ptr._4.asInstanceOf[Ptr[nxt_unit_request_t]]
    def request_=(v: Ptr[nxt_unit_request_t]): Unit = ptr._4 = v.asInstanceOf[Ptr[Byte]]

    def request_buf: Ptr[nxt_unit_buf_t] = ptr._5
    def request_buf_=(v: Ptr[nxt_unit_buf_t]): Unit = ptr._5 = v

    def response: Ptr[nxt_unit_response_t] = ptr._6
    def response_=(v: Ptr[nxt_unit_response_t]): Unit = ptr._6 = v

    def response_buf: Ptr[nxt_unit_buf_t] = ptr._7
    def response_buf_=(v: Ptr[nxt_unit_buf_t]): Unit = ptr._7 = v

    def response_max_fields: CInt = ptr._8
    def response_max_fields_=(v: CInt): Unit = ptr._8 = v

    def content_buf: Ptr[nxt_unit_buf_t] = ptr._9
    def content_buf_=(v: Ptr[nxt_unit_buf_t]): Unit = ptr._9 = v

    def content_length: CLongInt = ptr._10
    def content_length_=(v: CLongInt): Unit = ptr._10 = v

    def content_fd: CInt = ptr._11
    def content_fd_=(v: CInt): Unit = ptr._11 = v

    def data: Ptr[Byte] = ptr._12
    def data_=(v: Ptr[Byte]): Unit = ptr._12 = v
  }

  implicit class nxt_unit_callbacks_t_ops(private val ptr: Ptr[nxt_unit_callbacks_t]) extends AnyVal {
    def request_handler: request_handler_t = ptr._1
    def request_handler_=(v: request_handler_t): Unit = ptr._1 = v

    def data_handler: data_handler_t = ptr._2
    def data_handler_=(v: data_handler_t): Unit = ptr._2 = v

    def websocket_handler: websocket_handler_t = ptr._3
    def websocket_handler_=(v: websocket_handler_t): Unit = ptr._3 = v

    def add_port: add_port_t = ptr._5
    def add_port_=(v: add_port_t): Unit = ptr._5 = v

    def remove_port: remove_port_t = ptr._6
    def remove_port_=(v: remove_port_t): Unit = ptr._6 = v

    def quit: quit_t = ptr._8
    def quit_=(v: quit_t): Unit = ptr._8 = v
  }

  implicit class nxt_unit_init_t_ops(private val ptr: Ptr[nxt_unit_init_t]) extends AnyVal {
    def data: Ptr[Byte] = ptr._1
    def data_=(v: Ptr[Byte]): Unit = ptr._1 = v

    def ctx_data: Ptr[Byte] = ptr._2
    def ctx_data_=(v: Ptr[Byte]): Unit = ptr._2 = v

    def max_pending_requests: CInt = ptr._3
    def max_pending_requests_=(v: CInt): Unit = ptr._3 = v

    def request_data_size: CInt = ptr._4
    def request_data_size_=(v: CInt): Unit = ptr._4 = v

    def shm_limit: CInt = ptr._5
    def shm_limit_=(v: CInt): Unit = ptr._5 = v

    def callbacks: Ptr[nxt_unit_callbacks_t] = ptr.at6
    def callbacks_=(v: Ptr[nxt_unit_callbacks_t]): Unit = ptr._6 = v

    def ready_port: Ptr[nxt_unit_port_t] = ptr.at7
    def ready_port_=(v: Ptr[nxt_unit_port_t]): Unit = ptr._7 = v

    def ready_stream: CInt = ptr._8
    def ready_stream_=(v: CInt): Unit = ptr._8 = v

    def router_port: Ptr[nxt_unit_port_t] = ptr.at9
    def router_port_=(v: Ptr[nxt_unit_port_t]): Unit = ptr._9 = v

    def read_port: Ptr[nxt_unit_port_t] = ptr.at10
    def read_port_=(v: Ptr[nxt_unit_port_t]): Unit = ptr._10 = v

    def log_fd: CInt = ptr._11
    def log_fd_=(v: CInt): Unit = ptr._11 = v
  }

  implicit class nxt_websocket_header_t_ops(private val ptr: Ptr[nxt_websocket_header_t]) extends AnyVal {
    def opcode: Byte = ptr._1
    def rsv3: Byte = ptr._2
    def rsv2: Byte = ptr._3
    def rsv1: Byte = ptr._4
    def fin: Byte = ptr._5
    def payload_len: Byte = ptr._6
    def mask: Byte = ptr._7
    def payload_len_ : Ptr[CArray[Byte, _8]] = ptr.at8
  }

  implicit class nxt_unit_websocket_frame_t_ops(private val ptr: Ptr[nxt_unit_websocket_frame_t]) extends AnyVal {
    def req: Ptr[nxt_unit_request_info_t] = ptr._1
    def payload_len: CLongInt = ptr._2
    def header: Ptr[nxt_websocket_header_t] = ptr._3
    def mask: Ptr[Byte] = ptr._4
    def content_buf: Ptr[nxt_unit_buf_t] = ptr._5
    def content_length: CLongInt = ptr._6
  }
}
