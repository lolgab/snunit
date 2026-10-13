// Package unitmux lets several components of one program serve HTTP through FreeUnit, which gives a program a
// single handler (unit.ListenAndServe can be called once). Each component registers its routes with Handle and the
// one that serves the rest calls ListenAndServe.
//
// Modified builds of Jaeger (snunit project, https://github.com/lolgab/snunit) use it to serve the query API and UI
// and the OTLP/HTTP receiver through the same FreeUnit application.
package unitmux

import (
	"net/http"

	unit "unit.nginx.org/go"
)

var mux = http.NewServeMux()

// Handle registers the handler for the pattern (as http.ServeMux does). It can be called before or after
// ListenAndServe.
func Handle(pattern string, handler http.Handler) {
	mux.Handle(pattern, handler)
}

// ListenAndServe serves through FreeUnit the registered handlers, and the fallback for the rest of the requests.
func ListenAndServe(fallback http.Handler) error {
	mux.Handle("/", fallback)
	return unit.ListenAndServe("", mux)
}
