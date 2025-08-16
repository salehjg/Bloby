package io.github.salehjg.bloby;

import android.os.Handler;
import android.os.Looper;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.InputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class ByteServer {

    public interface OnDataReceivedListener {
        void onBlobReceived(String blobName, String datetime, String fullJson, byte[] fileData);
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
            try (DataInputStream dis = new DataInputStream(clientSocket.getInputStream())) {

                // Read JSON length
                int jsonLength = dis.readInt();
                byte[] jsonBytes = new byte[jsonLength];
                dis.readFully(jsonBytes);

                String jsonString = new String(jsonBytes, "UTF-8");
                System.out.println("Received JSON: " + jsonString);

                String blobName = "Unknown";
                String datetime = "Unknown";

                try {
                    JSONObject jsonObject = new JSONObject(jsonString);
                    blobName = jsonObject.optString("blob_name", "Unknown");
                    datetime = jsonObject.optString("datetime", "Unknown");
                } catch (JSONException e) {
                    notifyStatus("Invalid JSON received: " + e.getMessage());
                }

                // Read remaining bytes as file data
                ByteArrayOutputStream fileBuffer = new ByteArrayOutputStream();
                byte[] temp = new byte[4096];
                int bytesRead;
                while ((bytesRead = dis.read(temp)) != -1) {
                    fileBuffer.write(temp, 0, bytesRead);
                }

                byte[] fileData = fileBuffer.toByteArray();
                notifyBlobReceived(blobName, datetime, jsonString, fileData);

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
