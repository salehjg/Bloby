package io.github.salehjg.bloby;

import android.os.Handler;
import android.os.Looper;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.nio.charset.StandardCharsets;

public class ByteServer {

    public interface OnDataReceivedListener {
        void onBlobReceived(String blobName, String datetime, String fullJson, byte[] fileData);

        void onServerStatus(String status);
    }

    private OnDataReceivedListener listener;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

    private Socket currentClientSocket = null;
    private boolean hasConnectedClient = false;

    public void setOnDataReceivedListener(OnDataReceivedListener listener) {
        this.listener = listener;
    }

    public void startServer(int port) {
        if (isRunning) {
            notifyStatus("Server already running!");
            return;
        }

        new Thread(() -> {
            try {
                serverSocket = new ServerSocket(port);
                isRunning = true;
                notifyStatus("Server started on port " + port + ", waiting for connections...");

                while (isRunning && !serverSocket.isClosed()) {
                    try {
                        Socket clientSocket = serverSocket.accept();
                        notifyStatus("Client connected: " + clientSocket.getInetAddress());
                        handleClient(clientSocket);
                    } catch (Exception e) {
                        if (isRunning) {
                            notifyStatus("Error accepting connection: " + e.getMessage());
                        }
                    }
                }
            } catch (Exception e) {
                notifyStatus("Server error: " + e.getMessage());
                e.printStackTrace();
            } finally {
                isRunning = false;
                notifyStatus("Server stopped");
            }
        }).start();
    }

    public boolean hasConnectedClient() {
        return hasConnectedClient && currentClientSocket != null && !currentClientSocket.isClosed();
    }

    public void sendBlobToClient(String jsonData, byte[] fileData) throws Exception {
        if (!hasConnectedClient()) {
            throw new Exception("No client connected");
        }

        try {
            java.io.OutputStream outputStream = currentClientSocket.getOutputStream();

            // Convert JSON to bytes
            byte[] jsonBytes = jsonData.getBytes(java.nio.charset.StandardCharsets.UTF_8);

            // Send JSON length as 4-byte integer (big-endian)
            int jsonLength = jsonBytes.length;
            outputStream.write((jsonLength >> 24) & 0xFF);
            outputStream.write((jsonLength >> 16) & 0xFF);
            outputStream.write((jsonLength >> 8) & 0xFF);
            outputStream.write(jsonLength & 0xFF);

            // Send JSON data
            outputStream.write(jsonBytes);

            // Send file length as 4-byte integer (big-endian)
            int fileLength = fileData.length;
            outputStream.write((fileLength >> 24) & 0xFF);
            outputStream.write((fileLength >> 16) & 0xFF);
            outputStream.write((fileLength >> 8) & 0xFF);
            outputStream.write(fileLength & 0xFF);

            // Send file data
            outputStream.write(fileData);

            outputStream.flush();

            if (listener != null) {
                listener.onServerStatus("Sent blob to client: JSON " + jsonBytes.length + " bytes, File " + fileData.length + " bytes");
            }

        } catch (Exception e) {
            hasConnectedClient = false;
            currentClientSocket = null;
            throw e;
        }
    }

    private void handleClient(Socket clientSocket) {
        this.currentClientSocket = clientSocket;
        this.hasConnectedClient = true;

        if (listener != null) {
            listener.onServerStatus("Python client connected, starting listener thread");
        }

        // Create a dedicated thread to listen for incoming data
        new Thread(() -> {
            try {
                InputStream inputStream = clientSocket.getInputStream();
                int blobCount = 0;

                if (listener != null) {
                    listener.onServerStatus("Listener thread started, beginning receive loop");
                }

                // KEEP LISTENING IN A LOOP - don't exit after one blob
                while (hasConnectedClient && !clientSocket.isClosed()) {
                    try {
                        if (listener != null) {
                            listener.onServerStatus("Loop iteration " + (blobCount + 1) + ", waiting for next blob...");
                        }

                        // Try to read JSON length - this will block until data arrives
                        if (listener != null) {
                            listener.onServerStatus("Attempting to read JSON length header...");
                        }

                        byte[] jsonLengthBytes = new byte[4];
                        int totalRead = 0;

                        // Read exactly 4 bytes for JSON length
                        while (totalRead < 4) {
                            if (listener != null && totalRead == 0) {
                                listener.onServerStatus("Blocking read for JSON length...");
                            }

                            int bytesRead = inputStream.read(jsonLengthBytes, totalRead, 4 - totalRead);

                            if (listener != null) {
                                listener.onServerStatus("Read " + bytesRead + " bytes for JSON length header");
                            }

                            if (bytesRead == -1) {
                                if (listener != null) {
                                    listener.onServerStatus("Client disconnected (EOF) - exiting listener");
                                }
                                return; // Exit thread when client disconnects
                            }
                            totalRead += bytesRead;
                        }

                        if (listener != null) {
                            listener.onServerStatus("Received JSON length header, processing blob...");
                        }

                        // Parse JSON length
                        int jsonLength = ((jsonLengthBytes[0] & 0xFF) << 24) |
                                ((jsonLengthBytes[1] & 0xFF) << 16) |
                                ((jsonLengthBytes[2] & 0xFF) << 8) |
                                (jsonLengthBytes[3] & 0xFF);

                        if (listener != null) {
                            listener.onServerStatus("JSON length: " + jsonLength);
                        }

                        // Read JSON data
                        byte[] jsonBytes = new byte[jsonLength];
                        totalRead = 0;
                        while (totalRead < jsonLength) {
                            int bytesRead = inputStream.read(jsonBytes, totalRead, jsonLength - totalRead);
                            if (bytesRead == -1) {
                                if (listener != null) {
                                    listener.onServerStatus("Connection lost while reading JSON");
                                }
                                return;
                            }
                            totalRead += bytesRead;
                        }

                        String jsonString = new String(jsonBytes, StandardCharsets.UTF_8);
                        if (listener != null) {
                            listener.onServerStatus("Received JSON, length: " + jsonString.length());
                        }

                        // Read file length
                        byte[] fileLengthBytes = new byte[4];
                        totalRead = 0;
                        while (totalRead < 4) {
                            int bytesRead = inputStream.read(fileLengthBytes, totalRead, 4 - totalRead);
                            if (bytesRead == -1) {
                                if (listener != null) {
                                    listener.onServerStatus("Connection lost while reading file length");
                                }
                                return;
                            }
                            totalRead += bytesRead;
                        }

                        int fileLength = ((fileLengthBytes[0] & 0xFF) << 24) |
                                ((fileLengthBytes[1] & 0xFF) << 16) |
                                ((fileLengthBytes[2] & 0xFF) << 8) |
                                (fileLengthBytes[3] & 0xFF);

                        if (listener != null) {
                            listener.onServerStatus("File length: " + fileLength);
                        }

                        // Read file data
                        byte[] fileBytes = new byte[fileLength];
                        totalRead = 0;
                        while (totalRead < fileLength) {
                            int bytesRead = inputStream.read(fileBytes, totalRead, fileLength - totalRead);
                            if (bytesRead == -1) {
                                if (listener != null) {
                                    listener.onServerStatus("Connection lost while reading file data");
                                }
                                return;
                            }
                            totalRead += bytesRead;
                        }

                        if (listener != null) {
                            listener.onServerStatus("Successfully received blob, processing...");
                        }

                        // Parse JSON and trigger the callback ON MAIN THREAD
                        try {
                            JSONObject jsonObject = new JSONObject(jsonString);
                            String blobName = jsonObject.optString("blob_name", "unknown");
                            String datetime = jsonObject.optString("datetime", "unknown");

                            // CRITICAL: Call listener on main thread for UI updates
                            if (listener != null) {
                                // Post to main thread using Handler
                                android.os.Handler mainHandler = new android.os.Handler(android.os.Looper.getMainLooper());
                                mainHandler.post(() -> {
                                    listener.onBlobReceived(blobName, datetime, jsonString, fileBytes);
                                    listener.onServerStatus("Blob processed and added to list: " + blobName);
                                });
                            }

                            blobCount++;
                            if (listener != null) {
                                listener.onServerStatus("Blob " + blobCount + " completed successfully, continuing loop...");
                            }

                        } catch (Exception e) {
                            if (listener != null) {
                                listener.onServerStatus("Error parsing received data: " + e.getMessage());
                            }
                            // Don't return here - continue the loop even with parsing errors
                        }

                        // CONTINUE LOOP - don't exit, wait for next blob
                        if (listener != null) {
                            listener.onServerStatus("End of blob processing, looping back...");
                        }

                    } catch (IOException e) {
                        if (listener != null) {
                            listener.onServerStatus("IO Error in receive loop: " + e.getMessage());
                        }
                        break; // Exit loop on IO error
                    } catch (Exception e) {
                        if (listener != null) {
                            listener.onServerStatus("Unexpected error in receive loop: " + e.getMessage());
                        }
                        // Continue loop for non-IO exceptions
                    }
                }

                if (listener != null) {
                    listener.onServerStatus("Exited receive loop - hasConnectedClient: " + hasConnectedClient + ", isClosed: " + clientSocket.isClosed());
                }

            } catch (Exception e) {
                if (listener != null) {
                    listener.onServerStatus("Listener thread error: " + e.getMessage());
                }
            } finally {
                if (listener != null) {
                    listener.onServerStatus("Listener thread ended");
                }
            }
        }).start();

        // Keep main connection alive
        try {
            while (hasConnectedClient && !clientSocket.isClosed()) {
                Thread.sleep(1000);
            }
        } catch (Exception e) {
            if (listener != null) {
                listener.onServerStatus("Connection keeper error: " + e.getMessage());
            }
        } finally {
            this.hasConnectedClient = false;
            this.currentClientSocket = null;
            try {
                if (!clientSocket.isClosed()) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                // ignore
            }
            if (listener != null) {
                listener.onServerStatus("Python client disconnected");
            }
        }
    }

    public void stopServer() {
        isRunning = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void notifyBlobReceived(String blobName, String datetime, String fullJson, byte[] fileData) {
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    listener.onBlobReceived(blobName, datetime, fullJson, fileData)
            );
        }
    }

    private void notifyStatus(String status) {
        if (listener != null) {
            new Handler(Looper.getMainLooper()).post(() ->
                    listener.onServerStatus(status)
            );
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}
