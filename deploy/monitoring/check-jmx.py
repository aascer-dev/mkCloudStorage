#!/usr/bin/env python3
"""Read real JVM metrics using the Zabbix Java Gateway wire protocol."""

import argparse
import json
import socket
import struct
import time


def receive_exact(connection, length):
    data = bytearray()
    while len(data) < length:
        chunk = connection.recv(length - len(data))
        if not chunk:
            raise ValueError("Java Gateway closed the connection before the response completed")
        data.extend(chunk)
    return bytes(data)


def check(gateway_host, gateway_port, jmx_host, jmx_port, timeout):
    keys = [
        'jmx["java.lang:type=Runtime","VmName"]',
        'jmx["java.lang:type=Memory","HeapMemoryUsage.used"]',
    ]
    payload = json.dumps({
        "request": "java gateway jmx",
        "jmx_endpoint": f"service:jmx:rmi:///jndi/rmi://{jmx_host}:{jmx_port}/jmxrmi",
        "keys": keys,
    }).encode("utf-8")
    with socket.create_connection((gateway_host, gateway_port), timeout=timeout) as connection:
        connection.settimeout(timeout)
        connection.sendall(b"ZBXD\x01" + struct.pack("<Q", len(payload)) + payload)
        if receive_exact(connection, 5) != b"ZBXD\x01":
            raise ValueError("Unexpected Java Gateway protocol header")
        length = struct.unpack("<Q", receive_exact(connection, 8))[0]
        if length > 1024 * 1024:
            raise ValueError("Java Gateway response is unexpectedly large")
        response = json.loads(receive_exact(connection, length))

    if response.get("response") != "success":
        raise ValueError(f"Java Gateway request failed: {response}")
    results = response.get("data", [])
    if len(results) != len(keys):
        raise ValueError(f"Missing JVM metrics: {response}")
    for key, result in zip(keys, results):
        if "error" in result or "value" not in result:
            raise ValueError(f"Failed to read {key}: {result}")
    if not results[0]["value"] or int(results[1]["value"]) < 0:
        raise ValueError(f"Invalid JVM metrics: {response}")
    print(f"JMX OK: JVM={results[0]['value']}, heap used={results[1]['value']} bytes")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--gateway-host", default="127.0.0.1")
    parser.add_argument("--gateway-port", type=int, default=10052)
    parser.add_argument("--jmx-host", default="backend")
    parser.add_argument("--jmx-port", type=int, default=9010)
    parser.add_argument("--timeout", type=float, default=60)
    args = parser.parse_args()
    if args.timeout <= 0:
        parser.error("--timeout must be positive")

    deadline = time.monotonic() + args.timeout
    last_error = None
    while time.monotonic() < deadline:
        try:
            check(args.gateway_host, args.gateway_port, args.jmx_host, args.jmx_port,
                  min(5, max(0.1, deadline - time.monotonic())))
            return
        except (OSError, ValueError, KeyError, TypeError) as error:
            last_error = error
            time.sleep(min(2, max(0, deadline - time.monotonic())))
    raise SystemExit(f"JMX check failed after {args.timeout:g}s: {last_error}")


if __name__ == "__main__":
    main()
