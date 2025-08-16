package io.github.salehjg.bloby;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ByteServer {

    public interface OnDataReceivedListener {
        void onJsonDataReceived(String blobName, String datetime, String fullJson);
        void onServerStatus(String status);
    }

    private OnDataReceivedListener listener;
    private ServerSocket serverSocket;
    private boolean isRunning = false;

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

                        // Handle each client in a separate thread
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

    private void handleClient(Socket clientSocket) {
        new Thread(() -> {
            try (InputStream inputStream = clientSocket.getInputStream()) {
                ByteArrayOutputStream buffer = new ByteArrayOutputStream();

                byte[] temp = new byte[4096];
                int bytesRead;
                while ((bytesRead = inputStream.read(temp)) != -1) {
                    buffer.write(temp, 0, bytesRead);
                }

                byte[] receivedData = buffer.toByteArray();
                String receivedString = new String(receivedData);

                // Log to console
                System.out.println("Received data: " + receivedString);

                // Try to parse as JSON
                try {
                    JSONObject jsonObject = new JSONObject(receivedString);
                    String blobName = jsonObject.optString("blob_name", "Unknown");
                    String datetime = jsonObject.optString("datetime", "Unknown");

                    // Notify the UI thread with parsed data
                    notifyJsonDataReceived(blobName, datetime, receivedString);

                } catch (JSONException e) {
                    notifyStatus("Invalid JSON received: " + e.getMessage());
                    // Still notify with raw data as fallback
                    notifyJsonDataReceived("Invalid JSON", "N/A", receivedString);
                }

                clientSocket.close();
                notifyStatus("Client disconnected");

            } catch (Exception e) {
                notifyStatus("Error handling client: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();
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

    private void notifyJsonDataReceived(String blobName, String datetime, String fullJson) {
        if (listener != null) {
            // Use Handler to post to main UI thread
            new Handler(Looper.getMainLooper()).post(() ->
                    listener.onJsonDataReceived(blobName, datetime, fullJson)
            );
        }
    }

    private void notifyStatus(String status) {
        if (listener != null) {
            // Use Handler to post to main UI thread
            new Handler(Looper.getMainLooper()).post(() ->
                    listener.onServerStatus(status)
            );
        }
    }

    public boolean isRunning() {
        return isRunning;
    }
}