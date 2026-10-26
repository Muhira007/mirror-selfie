"""USB-assisted integration test against one phone; no credentials are written to disk.

Requires the existing Python websocket-client, numpy and opencv-python packages.
Start sharing on the phone and leave its QR visible before running:
  python scripts/test-remote-camera.py --serial SERIAL --adb PATH

Tests camera commands, recorder events, one-use QR and transport resume over TLS.
Does not implement a WebRTC video receiver or validate recovered video frames.
"""
import argparse
import base64
import hashlib
import ipaddress
import json
import socket
import ssl
import subprocess
import time
import uuid

import cv2
import numpy as np
import websocket


def run(adb, serial):
    def device(*args):
        return subprocess.run([adb, "-s", serial, *args], check=True, capture_output=True, timeout=15).stdout

    png = device("exec-out", "screencap", "-p")
    code, _, _ = cv2.QRCodeDetector().detectAndDecode(cv2.imdecode(np.frombuffer(png, np.uint8), cv2.IMREAD_COLOR))
    assert code.startswith("mirror1:") and len(code) <= 2048, "No visible pairing QR"
    encoded = code.split(":", 1)[1]
    p = json.loads(base64.urlsafe_b64decode(encoded + "=" * (-len(encoded) % 4)))
    address = ipaddress.ip_address(p["h"])
    assert address.version == 4 and address.is_private and not address.is_loopback
    assert 1024 <= p["p"] <= 65535

    def connect():
        # Pin is checked before sending any application credential. Self-signed certs
        # cannot be validated against the desktop's ordinary public CA roots.
        connection = websocket.create_connection(
            f'wss://{p["h"]}:{p["p"]}', timeout=5,
            sslopt={"cert_reqs": ssl.CERT_NONE, "check_hostname": False},
            http_no_proxy=[p["h"]],
        )
        cert = connection.sock.getpeercert(binary_form=True)
        if hashlib.sha256(cert).hexdigest() != p["f"]:
            connection.close()
            raise AssertionError("Certificate pin mismatch")
        connection.settimeout(1)
        return connection

    def send(connection, kind, **fields):
        connection.send(json.dumps({"v": 1, "session": p["s"], "type": kind, **fields}))

    def wait_for(connection, predicate, timeout=25):
        deadline = time.monotonic() + timeout
        while time.monotonic() < deadline:
            try:
                message = json.loads(connection.recv())
            except websocket.WebSocketTimeoutException:
                continue
            if predicate(message):
                return message
        raise AssertionError("Timed out waiting for confirmed camera state")

    last_command = 0.0
    def command(connection, action, value="", **fields):
        nonlocal last_command
        time.sleep(max(0, .9 - (time.monotonic() - last_command)))
        send(connection, "control", action=action, value=value, request=str(uuid.uuid4()), **fields)
        last_command = time.monotonic()

    ws = connect()
    resume = ""
    recording = False
    try:
        send(ws, "auth", token=p["t"])
        authenticated = wait_for(ws, lambda m: m.get("type") == "authenticated")
        resume = authenticated["resume"]
        assert resume != p["t"] and len(resume) == 64
        device("shell", "am", "start", "-n", "id.mirrorselfie/.CameraActivity")
        state = wait_for(ws, lambda m: m.get("type") == "cameraState" and m.get("available"))
        assert "battery" in state and state["freeBytes"] > 0
        print("PASS authenticated camera and storage/battery telemetry", flush=True)

        command(ws, "mode", "video")
        wait_for(ws, lambda m: m.get("type") == "cameraState" and m.get("mode") == "video" and m.get("available"))
        command(ws, "video")
        recording = True
        state = wait_for(ws, lambda m: m.get("type") == "cameraState" and m.get("recording") and m.get("recordedMs", 0) >= 2000)
        before = state["recordedMs"]
        print("PASS remote mode, recorder Start and confirmed duration", flush=True)

        ws.sock.shutdown(socket.SHUT_RDWR)
        ws.sock.close()
        ws.connected = False
        # Reject QR reuse even while the previously authenticated monitor is absent.
        invalid = connect()
        try:
            send(invalid, "auth", token=p["t"])
            rejected = invalid.recv()
            assert rejected == "", "Consumed QR accepted unexpectedly"
        finally:
            invalid.close()
        print("PASS consumed QR rejected after transport loss", flush=True)

        ws = connect()
        send(ws, "resume", token=resume)
        wait_for(ws, lambda m: m.get("type") == "authenticated")
        state = wait_for(ws, lambda m: m.get("type") == "cameraState" and m.get("recording") and m.get("recordedMs", 0) >= before)
        print("PASS authenticated transport resumed; recorder still running", flush=True)
        command(ws, "video")
        wait_for(ws, lambda m: m.get("type") == "cameraState" and not m.get("recording") and m.get("message") == "Video tersimpan")
        recording = False
        print("PASS remote Stop and successful Finalize after resume", flush=True)
    finally:
        if recording and resume:
            try:
                # Stop a test recording even when an earlier assertion failed.
                if not ws.connected:
                    ws = connect()
                    send(ws, "resume", token=resume)
                    wait_for(ws, lambda m: m.get("type") == "authenticated")
                latest = wait_for(ws, lambda m: m.get("type") == "cameraState")
                if latest.get("recording"):
                    command(ws, "video")
                    wait_for(ws, lambda m: m.get("type") == "cameraState" and not m.get("recording"))
            except Exception:
                print("Test cleanup could not confirm recording stopped; check the phone.", flush=True)
        try:
            send(ws, "bye")
        except Exception:
            pass
        ws.close()


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--serial", required=True)
    parser.add_argument("--adb", required=True)
    options = parser.parse_args()
    try:
        run(options.adb, options.serial)
    except Exception as error:
        # Never include a QR, resume secret, SDP or arbitrary remote payload in logs.
        print(f"FAIL {type(error).__name__}: test incomplete; inspect phone state")
        raise SystemExit(1)
