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

        # Send file data
        s.sendall(file_bytes)

        print(f"Data sent successfully. JSON: {len(json_bytes)} bytes, File: {len(file_bytes)} bytes")


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


def receive_file():
    raise NotImplementedError("The 'receive' functionality is not implemented yet.")


def main():
    parser = argparse.ArgumentParser(description="File sender and receiver CLI")
    subparsers = parser.add_subparsers(dest='command')

    send_parser = subparsers.add_parser('send', help='Send a file to an IP address')
    send_parser.add_argument('file', type=str, help='Path to the file to send')
    send_parser.add_argument('ip', type=str, help='Destination IP address')

    receive_parser = subparsers.add_parser('receive', help='Receive a file')
    # Future arguments for receive can be added here

    args = parser.parse_args()

    if args.command == 'send':
        send_file(args.file, args.ip)
    elif args.command == 'receive':
        receive_file()


if __name__ == "__main__":
    main()