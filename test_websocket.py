import argparse
import asyncio
import json
import os
import subprocess
from urllib.parse import urlencode

import websockets

DEFAULT_PORT = 8081
DEFAULT_TOKEN = os.environ.get("PORTAL_TOKEN", "").strip()


def setup_adb_forward(port: int = DEFAULT_PORT) -> bool:
    """Set up ADB port forwarding for WebSocket connection."""
    print(f"Setting up ADB forward tcp:{port} -> tcp:{port}...")
    try:
        result = subprocess.run(
            ["adb", "forward", f"tcp:{port}", f"tcp:{port}"],
            capture_output=True,
            text=True,
        )
        if result.returncode == 0:
            print(f"✅ ADB forward established on port {port}")
            return True
        else:
            print(f"❌ ADB forward failed: {result.stderr}")
            return False
    except FileNotFoundError:
        print("❌ ADB not found. Make sure Android SDK platform-tools is in PATH")
        return False
    except Exception as e:
        print(f"❌ Error setting up ADB forward: {e}")
        return False


def build_ws_uri(port: int, token: str, event_format: str | None) -> str:
    params = {}
    if token:
        params["token"] = token
    if event_format:
        params["eventFormat"] = event_format

    base_uri = f"ws://localhost:{port}"
    if not params:
        return base_uri
    return f"{base_uri}/?{urlencode(params)}"


def extract_event_payload(message: dict) -> dict | None:
    if message.get("method") == "events/device":
        params = message.get("params")
        if isinstance(params, dict):
            return params
        return None
    if "type" in message:
        return message
    return None


async def test_connection(
    port: int = DEFAULT_PORT,
    token: str = "",
    event_format: str | None = None,
):
    uri = build_ws_uri(port, token, event_format)
    print(f"Connecting to {uri}...")

    try:
        async with websockets.connect(uri) as websocket:
            print("✅ Connected successfully!")

            # Test 1: Ping
            print("\nSending PING...")
            ping_event = {"type": "PING", "timestamp": 123456789}
            await websocket.send(json.dumps(ping_event))

            # Wait for response
            print("Waiting for response...")
            response = await websocket.recv()
            print(f"Received: {response}")

            data = json.loads(response)
            if data.get("type") == "PONG":
                print("✅ PING/PONG Test Passed")
            else:
                print("❌ PING/PONG Test Failed")

            print("\n" + "=" * 50)
            print("Listening for events (Ctrl+C to stop)...")
            print("Trigger a notification on your phone to see it here!")
            print("=" * 50 + "\n")

            while True:
                msg = await websocket.recv()
                try:
                    message = json.loads(msg)
                    event = extract_event_payload(message)
                    if event is None:
                        print(f"Non-device message: {message}\n")
                        continue

                    event_type = event.get("type", "UNKNOWN")
                    timestamp = event.get("timestamp", "")
                    payload = event.get("payload", {})

                    print(f"[{event_type}] {timestamp}")
                    if isinstance(payload, dict):
                        for key, value in payload.items():
                            print(f"  {key}: {value}")
                    else:
                        print(f"  payload: {payload}")
                    print()
                except json.JSONDecodeError:
                    print(f"Raw event: {msg}\n")

    except ConnectionRefusedError:
        print("❌ Connection Failed: Is the app running and service enabled?")
        print("   Make sure the WebSocket server is enabled in the app settings.")
    except Exception as e:
        print(f"❌ Error: {e}")


def main():
    parser = argparse.ArgumentParser(description="Listen for local Portal WebSocket events.")
    parser.add_argument("port", nargs="?", type=int, default=DEFAULT_PORT)
    parser.add_argument("token", nargs="?", default=DEFAULT_TOKEN)
    event_mode = parser.add_mutually_exclusive_group()
    event_mode.add_argument(
        "--legacy-events",
        action="store_true",
        help="explicitly request legacy raw local event frames via ?eventFormat=legacy",
    )
    event_mode.add_argument(
        "--rpc-events",
        action="store_true",
        help="request RPC-wrapped local device events via ?eventFormat=rpc",
    )
    args = parser.parse_args()

    # Set up ADB forwarding first
    if not setup_adb_forward(args.port):
        print("\nContinuing anyway in case forward is already set up...")

    # Run the WebSocket listener
    try:
        event_format = "rpc" if args.rpc_events else "legacy" if args.legacy_events else None
        asyncio.run(
            test_connection(
                port=args.port,
                token=args.token.strip(),
                event_format=event_format,
            )
        )
    except KeyboardInterrupt:
        print("\nTest stopped by user")


if __name__ == "__main__":
    main()
