import socket

def send_data(target_ip, target_port, data_bytes):
    with socket.socket(socket.AF_INET, socket.SOCK_STREAM) as s:
        s.connect((target_ip, target_port))  # Connect to Android device
        s.sendall(data_bytes)
        print("Data sent successfully.")

# Example usage
data = b'{"blob_name": "example_blob_001","datetime": "2025-08-16T14:30:00","other_data": "This wont be shown in the list but will be in the full JSON view"}'
send_data('192.168.1.4', 12345, data)  # Replace with your Android device's IP
