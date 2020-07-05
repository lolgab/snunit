./mill snunit.autowire.nativeLink
# cp ~/unit/build/unit_app_test ~/scala/snunit/out/snunit/nativeLink/dest/out
curl -X PUT --data-binary '0' --unix-socket /usr/local/var/run/unit/control.sock http://localhost/config/applications/test_app/processes
curl -X PUT --data-binary '1' --unix-socket /usr/local/var/run/unit/control.sock http://localhost/config/applications/test_app/processes
# curl http://localhost:8081
# curl -X PUT --data-binary 'hey' http://localhost:8081
# tail -n 50 /usr/local/var/log/unit/unit.log
