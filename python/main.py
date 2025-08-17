import socket
import argparse
import base64
import json
import os
import hashlib
import uuid
import struct
from datetime import datetime


def send_data(target_ip, target_port, json_payload, file_bytes):
    """Send JSON payload and file bytes in a single connection with proper protocol"""
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((target_ip, target_port))

        # Convert JSON to bytes
        json_str = json.dumps(json_payload, indent=4)
        json_bytes = json_str.encode('utf-8')

        # Send JSON length as 4-byte integer (big-endian)
        json_length = len(json_bytes)
        s.sendall(struct.pack('>I', json_length))

        # Send JSON data
        s.sendall(json_bytes)

        # Send file length as 4-byte integer (big-endian)
        file_length = len(file_bytes)
        s.sendall(struct.pack('>I', file_length))

        # Send file data
        s.sendall(file_bytes)

        print(f"Data sent successfully. JSON: {len(json_bytes)} bytes, File: {len(file_bytes)} bytes")


def receive_data(conn):
    """Receive JSON payload and file bytes from a connection"""
    # Receive JSON length (4 bytes, big-endian)
    json_length_bytes = conn.recv(4)
    if len(json_length_bytes) == 0:
        # Connection closed gracefully
        raise ValueError("Connection closed by peer")
    if len(json_length_bytes) != 4:
        raise ValueError("Failed to receive JSON length")

    json_length = struct.unpack('>I', json_length_bytes)[0]
    print(f"Expecting JSON of length: {json_length} bytes")

    # Receive JSON data
    json_bytes = b''
    while len(json_bytes) < json_length:
        chunk = conn.recv(min(4096, json_length - len(json_bytes)))
        if not chunk:
            raise ValueError("Connection closed while receiving JSON")
        json_bytes += chunk

    # Parse JSON
    json_str = json_bytes.decode('utf-8')
    json_payload = json.loads(json_str)
    print("Received JSON payload:")
    print(json.dumps(json_payload, indent=4))

    # Receive file length (4 bytes, big-endian)
    file_length_bytes = conn.recv(4)
    if len(file_length_bytes) != 4:
        raise ValueError("Failed to receive file length")

    file_length = struct.unpack('>I', file_length_bytes)[0]
    print(f"Expecting file data of length: {file_length} bytes")

    # Receive file data based on expected length
    file_bytes = b''
    while len(file_bytes) < file_length:
        chunk = conn.recv(min(4096, file_length - len(file_bytes)))
        if not chunk:
            raise ValueError("Connection closed while receiving file data")
        file_bytes += chunk

    print(f"Received file data: {len(file_bytes)} bytes")
    return json_payload, file_bytes


def verify_sha256(file_bytes, expected_sha256):
    """Verify SHA256 hash of received file"""
    actual_sha256 = compute_sha256(file_bytes)
    return actual_sha256 == expected_sha256


def compute_sha256(file_bytes):
    sha256_hash = hashlib.sha256(file_bytes).hexdigest()
    return sha256_hash


def send_file(file_path, ip_address):
    uuid_str = str(uuid.uuid4())
    blob_name = f"{os.path.basename(file_path)}_{uuid_str}_{int(datetime.now().timestamp())}"
    current_datetime = datetime.now().isoformat()

    with open(file_path, 'rb') as file:
        file_bytes = file.read()

    sha256 = compute_sha256(file_bytes)

    json_payload = {
        "blob_name": blob_name,
        "datetime": current_datetime,
        "file_name": os.path.basename(file_path),
        "sha256": sha256,
        "uuid": uuid_str
    }

    print("JSON payload:")
    print(json.dumps(json_payload, indent=4))

    # Send both JSON and file in single connection
    send_data(ip_address, 12345, json_payload, file_bytes)


def receive_file(ip_address, force_overwrite=False, port=12345):
    """Connect to Android server and wait for it to send files"""
    print(f"Connecting to {ip_address}:{port} to receive files...")

    try:
        with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
            s.connect((ip_address, port))
            print("Connected successfully to Android server")
            print("Waiting for Android app to send files... (Press Ctrl+C to disconnect)")

            file_count = 0
            while True:
                try:
                    # Wait for Android to send data (blocking call)
                    json_payload, file_bytes = receive_data(s)
                    file_count += 1

                    # Extract file information from JSON
                    file_name = json_payload.get('file_name')
                    expected_sha256 = json_payload.get('sha256')

                    if not file_name:
                        print("Warning: JSON payload missing 'file_name' field, using default")
                        file_name = f"received_file_{file_count}"

                    # Check if file already exists
                    if os.path.exists(file_name):
                        if not force_overwrite:
                            print(f"Error: File '{file_name}' already exists. Use -f to force overwrite.")
                            print("Waiting for next file...")
                            continue
                        else:
                            print(f"Overwriting existing file '{file_name}'")

                    # Verify SHA256 if provided
                    if expected_sha256:
                        if verify_sha256(file_bytes, expected_sha256):
                            print("✓ SHA256 verification passed")
                        else:
                            print("⚠ Warning: SHA256 verification failed!")
                            actual_sha256 = compute_sha256(file_bytes)
                            print(f"Expected: {expected_sha256}")
                            print(f"Actual:   {actual_sha256}")

                    # Write file to disk
                    with open(file_name, 'wb') as f:
                        f.write(file_bytes)

                    print(f"✓ File '{file_name}' received successfully ({len(file_bytes)} bytes)")
                    print("Waiting for next file...")

                except ValueError as e:
                    if "Connection closed by peer" in str(e):
                        print("Android server disconnected")
                        break
                    elif "Failed to receive JSON length" in str(e):
                        print("Android server closed connection")
                        break
                    else:
                        print(f"Error receiving file: {e}")
                        print("Waiting for next file...")
                        continue

                except ConnectionResetError:
                    print("Connection reset by Android server")
                    break
                except Exception as e:
                    print(f"Error receiving file: {e}")
                    print("Waiting for next file...")
                    continue

            if file_count > 0:
                print(f"\nSession complete. Received {file_count} file(s).")
            else:
                print("\nSession ended. No files received.")

    except ConnectionRefusedError:
        print(f"Error: Could not connect to {ip_address}:{port}")
        print("Make sure the Android app server is running")
    except socket.timeout:
        print(f"Error: Connection to {ip_address}:{port} timed out")
    except KeyboardInterrupt:
        print("\n\nDisconnected by user (Ctrl+C)")
    except Exception as e:
        print(f"Connection error: {e}")


def main():
    parser = argparse.ArgumentParser(description="File sender and receiver CLI")
    subparsers = parser.add_subparsers(dest='command')

    send_parser = subparsers.add_parser('send', help='Send a file to an IP address')
    send_parser.add_argument('file', type=str, help='Path to the file to send')
    send_parser.add_argument('ip', type=str, help='Destination IP address')

    receive_parser = subparsers.add_parser('receive', help='Receive a file')
    receive_parser.add_argument('ip', type=str, help='IP address to connect to for receiving file')
    receive_parser.add_argument('-f', '--force', action='store_true',
                                help='Force overwrite if file already exists')
    receive_parser.add_argument('-p', '--port', type=int, default=12345,
                                help='Port to connect to (default: 12345)')

    args = parser.parse_args()

    if args.command == 'send':
        send_file(args.file, args.ip)
    elif args.command == 'receive':
        receive_file(args.ip, args.force, args.port)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()