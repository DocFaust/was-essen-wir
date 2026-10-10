#!/usr/bin/env bash
exec 3<>/dev/tcp/127.0.0.1/8080 || exit 1
printf 'GET /actuator/health HTTP/1.1\r\nHost: localhost\r\nConnection: close\r\n\r\n' >&3
grep -q '"status":"UP"' <&3
