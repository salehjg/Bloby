package io.github.salehjg.bloby;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONObject;

import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;

import java.io.ByteArrayOutputStream;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.SocketException;
import java.io.File;

import android.os.Environment;
import android.Manifest;
import android.content.pm.PackageManager;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;


public class MainActivity extends AppCompatActivity {

    private TextView textViewIpAddress;
    private RecyclerView mainRecyclerView;
    private ListView logListView;
    private Button buttonWipeAll, buttonToggleLogs;

    private boolean isLogVisible = false;
    private ArrayList<String> logData;
    private ArrayAdapter<String> logAdapter;

    // Server and data handling
    private ByteServer byteServer;
    private DataAdapter dataAdapter;


    // Add these constants to your MainActivity class
    private static final int PERMISSIONS_REQUEST_CODE = 1000;
    private static final String BLOBY_FOLDER = "Bloby";

    private ActivityResultLauncher<Intent> mFileEditLauncher;


    private void loadSavedBlobs() {
        File filesDir = getFilesDir();
        File[] blobDirs = filesDir.listFiles();

        if (blobDirs == null) return;

        for (File blobDir : blobDirs) {
            if (blobDir.isDirectory()) {
                File jsonFile = new File(blobDir, "blob.json");
                if (jsonFile.exists()) {
                    try {
                        StringBuilder jsonContent = new StringBuilder();
                        try (java.io.BufferedReader reader =
                                     new java.io.BufferedReader(new java.io.FileReader(jsonFile))) {
                            String line;
                            while ((line = reader.readLine()) != null) {
                                jsonContent.append(line);
                            }
                        }

                        String fullJson = jsonContent.toString();
                        JSONObject jsonObject = new JSONObject(fullJson);

                        String blobName = jsonObject.optString("blob_name", blobDir.getName());
                        String datetime = jsonObject.optString("datetime", "unknown");

                        // Add to adapter
                        dataAdapter.addJsonData(blobName, datetime, fullJson);

                        addLogEntry("Restored blob: " + blobName);

                    } catch (Exception e) {
                        addLogEntry("Error loading blob from " + blobDir.getName() + ": " + e.getMessage());
                    }
                }
            }
        }
    }

    // Add this method to create external storage directory
    private File getBlobyExternalDirectory() {
        File externalDir = new File(Environment.getExternalStorageDirectory(), BLOBY_FOLDER);
        if (!externalDir.exists()) {
            externalDir.mkdirs();
        }
        return externalDir;
    }

    // Add permission checking method
    private boolean checkStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            return Environment.isExternalStorageManager();
        } else {
            int write = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
            int read = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);
            return read == PackageManager.PERMISSION_GRANTED && write == PackageManager.PERMISSION_GRANTED;
        }
    }

    // Add permission request method
    private void requestStoragePermissions() {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.R) {
            try {
                Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION);
                intent.addCategory("android.intent.category.DEFAULT");
                intent.setData(android.net.Uri.parse(String.format("package:%s", getApplicationContext().getPackageName())));
                startActivityForResult(intent, PERMISSIONS_REQUEST_CODE);
            } catch (Exception e) {
                Intent intent = new Intent();
                intent.setAction(android.provider.Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                startActivityForResult(intent, PERMISSIONS_REQUEST_CODE);
            }
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_CODE);
        }
    }

    /**
     * Open file with appropriate application using content URI
     */
    private void openFileForEditing(String blobName) {
        try {
            // Get the blob directory from internal storage
            File blobDir = new File(getFilesDir(), blobName);
            if (!blobDir.exists()) {
                Toast.makeText(this, "Blob directory not found", Toast.LENGTH_SHORT).show();
                return;
            }

            // Read the JSON to get the original file name
            File jsonFile = new File(blobDir, "blob.json");
            if (!jsonFile.exists()) {
                Toast.makeText(this, "Blob JSON not found", Toast.LENGTH_SHORT).show();
                return;
            }

            // Parse JSON to get file name
            StringBuilder jsonContent = new StringBuilder();
            try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(jsonFile))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    jsonContent.append(line);
                }
            }

            JSONObject jsonObject = new JSONObject(jsonContent.toString());
            String fileName = jsonObject.optString("file_name", "unknown");

            // Find the actual file in internal storage
            File targetFile = new File(blobDir, fileName);
            if (!targetFile.exists()) {
                Toast.makeText(this, "File not found: " + fileName, Toast.LENGTH_SHORT).show();
                return;
            }

            // Create intent with FileProvider URI - exactly like your working code
            Intent intent = new Intent(Intent.ACTION_VIEW);
            intent.setData(androidx.core.content.FileProvider.getUriForFile(
                    getApplicationContext(),
                    getPackageName() + ".provider",
                    targetFile
            ));
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);

            // Launch with result launcher
            mFileEditLauncher.launch(intent);

            addLogEntry("Opened file for editing: " + fileName);
            Toast.makeText(this, "File opened. Changes will be saved automatically.", Toast.LENGTH_SHORT).show();

        } catch (Exception e) {
            Toast.makeText(this, "Error opening file: " + e.getMessage(), Toast.LENGTH_LONG).show();
            addLogEntry("Error opening file: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Determine MIME type based on file extension
     */
    private String getMimeType(String fileName) {
        String extension = "";
        int lastDot = fileName.lastIndexOf('.');
        if (lastDot > 0) {
            extension = fileName.substring(lastDot + 1).toLowerCase();
        }

        // Common MIME types
        switch (extension) {
            case "pdf":
                return "application/pdf";
            case "doc":
                return "application/msword";
            case "docx":
                return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
            case "xls":
                return "application/vnd.ms-excel";
            case "xlsx":
                return "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            case "ppt":
                return "application/vnd.ms-powerpoint";
            case "pptx":
                return "application/vnd.openxmlformats-officedocument.presentationml.presentation";
            case "txt":
                return "text/plain";
            case "jpg":
            case "jpeg":
                return "image/jpeg";
            case "png":
                return "image/png";
            case "gif":
                return "image/gif";
            case "bmp":
                return "image/bmp";
            case "webp":
                return "image/webp";
            case "mp4":
                return "video/mp4";
            case "avi":
                return "video/x-msvideo";
            case "mov":
                return "video/quicktime";
            case "mp3":
                return "audio/mpeg";
            case "wav":
                return "audio/wav";
            case "zip":
                return "application/zip";
            case "rar":
                return "application/x-rar-compressed";
            case "json":
                return "application/json";
            case "xml":
                return "text/xml";
            case "html":
            case "htm":
                return "text/html";
            case "css":
                return "text/css";
            case "js":
                return "application/javascript";
            default:
                return "application/octet-stream"; // Generic binary
        }
    }

    private void sendBlobToConnectedClient(String fullJson, String blobName) {
        // Check if we have a connected client
        if (!byteServer.hasConnectedClient()) {
            Toast.makeText(this, "No Python client connected", Toast.LENGTH_SHORT).show();
            addLogEntry("Send failed: No client connected");
            return;
        }

        // Show confirmation dialog
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Send Blob")
                .setMessage("Send '" + blobName + "' to connected Python client?")
                .setPositiveButton("Send", (dialog, which) -> {
                    sendBlobInBackground(fullJson, blobName);
                })
                .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                .show();
    }

    private void sendBlobInBackground(String fullJson, String blobName) {
        new Thread(() -> {
            try {
                runOnUiThread(() -> {
                    addLogEntry("Preparing to send blob: " + blobName);
                });

                // Read the file data from internal storage
                File blobDir = new File(getFilesDir(), blobName);
                if (!blobDir.exists()) {
                    runOnUiThread(() -> {
                        addLogEntry("Error: Blob directory not found: " + blobName);
                        Toast.makeText(this, "Blob not found", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // Get the original file name from JSON
                JSONObject jsonObject = new JSONObject(fullJson);
                String fileName = jsonObject.optString("file_name", "unknown");

                // Read the file data
                File targetFile = new File(blobDir, fileName);
                if (!targetFile.exists()) {
                    runOnUiThread(() -> {
                        addLogEntry("Error: File not found: " + fileName);
                        Toast.makeText(this, "File not found: " + fileName, Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                byte[] fileData = readFileToBytes(targetFile);
                if (fileData == null) {
                    runOnUiThread(() -> {
                        addLogEntry("Error: Could not read file data");
                        Toast.makeText(this, "Could not read file data", Toast.LENGTH_SHORT).show();
                    });
                    return;
                }

                // Send data to connected client
                byteServer.sendBlobToClient(fullJson, fileData);

                runOnUiThread(() -> {
                    addLogEntry("Successfully sent blob: " + blobName + " (" + fileData.length + " bytes)");
                    Toast.makeText(this, "Blob sent successfully!", Toast.LENGTH_SHORT).show();
                });

                // Close server after sending blob
                byteServer.stopServer();
                runOnUiThread(() -> {
                    addLogEntry("Server stopped after sending blob. Restarting...");
                    byteServer.startServer(12345);
                });

            } catch (Exception e) {
                runOnUiThread(() -> {
                    addLogEntry("Error sending blob: " + e.getMessage());
                    Toast.makeText(this, "Error sending blob: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
                e.printStackTrace();
            }
        }).start();
    }

    private byte[] readFileToBytes(File file) {
        try (java.io.FileInputStream fis = new java.io.FileInputStream(file);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            byte[] buffer = new byte[4096];
            int bytesRead;
            while ((bytesRead = fis.read(buffer)) != -1) {
                baos.write(buffer, 0, bytesRead);
            }
            return baos.toByteArray();

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Views
        textViewIpAddress = findViewById(R.id.textViewIpAddress);
        mainRecyclerView = findViewById(R.id.mainRecyclerView);
        logListView = findViewById(R.id.logListView);
        buttonWipeAll = findViewById(R.id.buttonAction2);
        buttonToggleLogs = findViewById(R.id.buttonToggleLogs);

        // --- Setup Log ListView ---
        logData = new ArrayList<>();
        logAdapter = new ArrayAdapter<>(
                this,
                android.R.layout.simple_list_item_1,
                logData
        );
        logListView.setAdapter(logAdapter);

        // Add initial log entries
        addLogEntry("Application started");
        addLogEntry("UI components initialized");
        addLogEntry("Ready for connections");

        // Initialize and display IP Address
        String deviceIp = getDeviceIpAddress();
        updateIpAddress(deviceIp);

        // --- Setup RecyclerView (Main List) ---
        dataAdapter = new DataAdapter();
        mainRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mainRecyclerView.setAdapter(dataAdapter);
        loadSavedBlobs();

        mFileEditLauncher = registerForActivityResult(
                new ActivityResultContracts.StartActivityForResult(),
                new ActivityResultCallback<ActivityResult>() {
                    @Override
                    public void onActivityResult(ActivityResult result) {
                        // File editing is complete
                        addLogEntry("File editing completed");
                        // The file changes are automatically saved by the external app
                        // since we granted WRITE_URI_PERMISSION
                    }
                }
        );

        // Set up item action listeners
        dataAdapter.setOnItemActionListener(new DataAdapter.OnItemActionListener() {
            @Override
            public void onViewClicked(String fullJson, int position) {
                // Get the blob name from the JSON
                String blobName = "unknown";
                try {
                    JSONObject jsonObject = new JSONObject(fullJson);
                    blobName = jsonObject.optString("blob_name", "unknown");
                } catch (Exception e) {
                    addLogEntry("Error parsing JSON for blob name: " + e.getMessage());
                }

                final String finalBlobName = blobName;

                // Show dialog with options
                AlertDialog.Builder builder = new AlertDialog.Builder(MainActivity.this);
                builder.setTitle("File Options")
                        .setMessage("Choose what to do with this file:")
                        .setPositiveButton("Edit File", (dialog, which) -> {
                            openFileForEditing(finalBlobName);
                        })
                        .setNeutralButton("View JSON", (dialog, which) -> {
                            showDataDialog("Full JSON Data", fullJson);
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();

                addLogEntry("File options shown for: " + finalBlobName);
            }

            @Override
            public void onSendClicked(String fullJson, String blobName, int position) {
                sendBlobToConnectedClient(fullJson, blobName);
            }


            @Override
            public void onDeleteClicked(int position) {
                try {
                    DataAdapter.DataItem item = dataAdapter.getItem(position);
                    if (item != null) {
                        String blobName = item.getBlobName();

                        // Delete the blob directory (json + file inside)
                        File blobDir = new File(getFilesDir(), blobName);
                        if (blobDir.exists()) {
                            deleteRecursive(blobDir);
                            addLogEntry("Deleted blob directory: " + blobName);
                        } else {
                            addLogEntry("Blob directory not found: " + blobName);
                        }

                        // Remove from adapter (UI list)
                        dataAdapter.removeItem(position);
                        Toast.makeText(MainActivity.this, "Blob deleted", Toast.LENGTH_SHORT).show();
                    }
                } catch (Exception e) {
                    addLogEntry("Error deleting blob: " + e.getMessage());
                    Toast.makeText(MainActivity.this, "Error deleting blob", Toast.LENGTH_SHORT).show();
                    e.printStackTrace();
                }
            }


        });

        // Initialize ByteServer
        byteServer = new ByteServer();
        byteServer.setOnDataReceivedListener(new ByteServer.OnDataReceivedListener() {
            @Override
            public void onBlobReceived(String blobName, String datetime, String fullJson, byte[] fileData) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            addLogEntry("Processing received blob: " + blobName);

                            // Create a folder named after the blobName in internal storage
                            File blobDir = new File(getFilesDir(), blobName);
                            if (!blobDir.exists()) {
                                blobDir.mkdirs();
                            }

                            // Save JSON as-is
                            File jsonFile = new File(blobDir, "blob.json");
                            try (FileOutputStream fos = new FileOutputStream(jsonFile)) {
                                fos.write(fullJson.getBytes(StandardCharsets.UTF_8));
                            }

                            // Extract file_name from JSON
                            JSONObject jsonObject = new JSONObject(fullJson);
                            String fileName = jsonObject.optString("file_name", "default_blob_file");

                            // Save byte array as file
                            File blobFile = new File(blobDir, fileName);
                            try (FileOutputStream fos = new FileOutputStream(blobFile)) {
                                fos.write(fileData);
                            }

                            addLogEntry("Saved blob: " + blobName + ", path: " + blobFile.getAbsolutePath());
                            dataAdapter.addJsonData(blobName, datetime, fullJson); // Ensure this is also safe or wrapped
                            Toast.makeText(MainActivity.this, "Received blob: " + blobName, Toast.LENGTH_SHORT).show();

                            byteServer.stopServer();
                            addLogEntry("Server stopped after receiving blob. Restarting...");
                            byteServer.startServer(12345); // This starts a new server thread, which is fine

                        } catch (Exception e) {
                            addLogEntry("Error saving blob: " + e.getMessage());
                            e.printStackTrace();
                        }
                    }
                });
            }

            @Override
            public void onServerStatus(String status) { // This is MainActivity.java:509
                // Switch to the main UI thread before updating the UI
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        addLogEntry("Server: " + status); // This will now run on the main thread
                    }
                });
            }
        });

        buttonWipeAll.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // Confirm with user
                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Wipe All Data")
                        .setMessage("Are you sure you want to delete ALL saved blobs and files?")
                        .setPositiveButton("Yes", (dialog, which) -> {
                            try {
                                // 1. Delete all folders and files in private storage
                                File filesDir = getFilesDir();
                                deleteRecursive(filesDir);

                                // 2. Clear RecyclerView adapter (main list)
                                dataAdapter.clearData();

                                // 3. Log & toast
                                addLogEntry("Wiped all stored blobs and cleared list.");
                                Toast.makeText(MainActivity.this, "All data deleted.", Toast.LENGTH_SHORT).show();

                            } catch (Exception e) {
                                addLogEntry("Error wiping data: " + e.getMessage());
                                Toast.makeText(MainActivity.this, "Error deleting data.", Toast.LENGTH_SHORT).show();
                            }
                        })
                        .setNegativeButton("Cancel", (dialog, which) -> dialog.dismiss())
                        .show();
            }
        });


        buttonToggleLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLogVisibility();
            }
        });

        // Handle Log ListView item clicks
        logListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                String selectedLog = (String) parent.getItemAtPosition(position);
                Toast.makeText(MainActivity.this, "Log: " + selectedLog, Toast.LENGTH_SHORT).show();
            }
        });

        byteServer.startServer(12345);
    }

    private void deleteRecursive(File fileOrDir) {
        if (fileOrDir != null && fileOrDir.exists()) {
            if (fileOrDir.isDirectory()) {
                for (File child : fileOrDir.listFiles()) {
                    deleteRecursive(child);
                }
            }
            // Don’t delete root filesDir itself, only its contents
            if (!fileOrDir.equals(getFilesDir())) {
                fileOrDir.delete();
            }
        }
    }


    /**
     * Toggle the visibility of the log ListView
     */
    private void toggleLogVisibility() {
        if (isLogVisible) {
            logListView.setVisibility(View.GONE);
            buttonToggleLogs.setText("Show Logs");
            isLogVisible = false;
        } else {
            logListView.setVisibility(View.VISIBLE);
            buttonToggleLogs.setText("Hide Logs");
            isLogVisible = true;
        }
    }

    /**
     * Add a new log entry to the log list
     */
    private void addLogEntry(String logMessage) {
        String timestamp = new java.text.SimpleDateFormat("HH:mm:ss", java.util.Locale.getDefault())
                .format(new java.util.Date());
        String formattedLog = "[" + timestamp + "] " + logMessage;

        logData.add(0, formattedLog); // Add to top of list
        logAdapter.notifyDataSetChanged();

        // Limit log entries to prevent memory issues
        if (logData.size() > 100) {
            logData.remove(logData.size() - 1);
        }
    }

    /**
     * Update the IP address display
     */
    private void updateIpAddress(String ipAddress) {
        textViewIpAddress.setText("IP Address: " + ipAddress);
    }

    /**
     * Show data in a dialog
     */
    private void showDataDialog(String title, String data) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(title)
                .setMessage(data)
                .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                .setNegativeButton("Copy", (dialog, which) -> {
                    // Copy to clipboard
                    android.content.ClipboardManager clipboard =
                            (android.content.ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                    android.content.ClipData clip = android.content.ClipData.newPlainText("Received Data", data);
                    clipboard.setPrimaryClip(clip);
                    Toast.makeText(this, "Copied to clipboard", Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    /**
     * Get the device's IP address
     *
     * @return IP address as string, or "Not Available" if not found
     */
    private String getDeviceIpAddress() {
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface networkInterface = interfaces.nextElement();

                // Skip loopback and inactive interfaces
                if (networkInterface.isLoopback() || !networkInterface.isUp()) {
                    continue;
                }

                Enumeration<InetAddress> addresses = networkInterface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();

                    // We want IPv4 addresses that are not loopback
                    if (!address.isLoopbackAddress() &&
                            !address.isLinkLocalAddress() &&
                            address.getHostAddress().indexOf(':') == -1) { // IPv4 check

                        String ipAddress = address.getHostAddress();

                        // Prefer addresses that start with common private ranges
                        if (ipAddress.startsWith("192.168.") ||
                                ipAddress.startsWith("10.") ||
                                ipAddress.startsWith("172.")) {
                            return ipAddress;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return "Not Available";
    }
}