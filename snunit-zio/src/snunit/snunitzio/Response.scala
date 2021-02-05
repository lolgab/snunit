package snunit.snunitzio

import snunit.StatusCode

final case class Response(code: StatusCode, body: String, headers: Seq[(String, String)])
