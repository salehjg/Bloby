package io.github.salehjg.bloby;

import android.app.AlertDialog;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Enumeration;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.Socket;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.SocketException;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class MainActivity extends AppCompatActivity {

    private TextView textViewIpAddress;
    private RecyclerView mainRecyclerView;
    private ListView logListView;
    private Button buttonAction1, buttonAction2, buttonToggleLogs, buttonAction4;

    private boolean isLogVisible = false;
    private ArrayList<String> logData;
    private ArrayAdapter<String> logAdapter;

    // Server and data handling
    private ByteServer byteServer;
    private DataAdapter dataAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Initialize Views
        textViewIpAddress = findViewById(R.id.textViewIpAddress);
        mainRecyclerView = findViewById(R.id.mainRecyclerView);
        logListView = findViewById(R.id.logListView);
        buttonAction1 = findViewById(R.id.buttonAction1);
        buttonAction2 = findViewById(R.id.buttonAction2);
        buttonToggleLogs = findViewById(R.id.buttonToggleLogs);
        buttonAction4 = findViewById(R.id.buttonAction4);

        // Initialize and display IP Address
        String deviceIp = getDeviceIpAddress();
        updateIpAddress(deviceIp);

        // --- Setup RecyclerView (Main List) ---
        dataAdapter = new DataAdapter();
        mainRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        mainRecyclerView.setAdapter(dataAdapter);

        // Set up item action listeners
        dataAdapter.setOnItemActionListener(new DataAdapter.OnItemActionListener() {
            @Override
            public void onViewClicked(String fullJson, int position) {
                // Show full JSON data in a dialog
                showDataDialog("Full JSON Data", fullJson);
                addLogEntry("Viewed JSON item at position " + position);
            }

            @Override
            public void onSaveClicked(String fullJson, String blobName, int position) {
                // Save JSON data to file using blob name
                saveJsonToFile(fullJson, blobName, position);
                addLogEntry("Saved JSON item: " + blobName);
                Toast.makeText(MainActivity.this, "JSON saved as " + blobName + ".json", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onDeleteClicked(int position) {
                dataAdapter.removeItem(position);
                addLogEntry("Deleted JSON item at position " + position);
                Toast.makeText(MainActivity.this, "Item deleted", Toast.LENGTH_SHORT).show();
            }
        });

        // Initialize ByteServer
        byteServer = new ByteServer();
        byteServer.setOnDataReceivedListener(new ByteServer.OnDataReceivedListener() {
            @Override
            public void onJsonDataReceived(String blobName, String datetime, String fullJson) {
                // Add received JSON data to the main list
                dataAdapter.addJsonData(blobName, datetime, fullJson);
                addLogEntry("JSON received - Blob: " + blobName + ", DateTime: " + datetime);
            }

            @Override
            public void onServerStatus(String status) {
                addLogEntry("Server: " + status);
            }
        });

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

        // --- Set OnClick Listeners for Buttons ---
        buttonAction1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (!byteServer.isRunning()) {
                    byteServer.startServer(12345);
                    buttonAction1.setText("Stop Server");
                } else {
                    byteServer.stopServer();
                    buttonAction1.setText("Start Server");
                }
            }
        });

        buttonAction2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                dataAdapter.clearData();
                addLogEntry("Action 2: Cleared received data");
                Toast.makeText(MainActivity.this, "Data cleared.", Toast.LENGTH_SHORT).show();
            }
        });

        buttonToggleLogs.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                toggleLogVisibility();
            }
        });

        buttonAction4.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                addLogEntry("Test log entry - Button 4 pressed");
                Toast.makeText(MainActivity.this, "Test log added!", Toast.LENGTH_SHORT).show();
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
     * Save JSON data to a file using blob name
     */
    private void saveJsonToFile(String jsonData, String blobName, int position) {
        try {
            // Use blob name for filename, fallback to position if blob name is invalid
            String filename = blobName.replaceAll("[^a-zA-Z0-9._-]", "_");
            if (filename.isEmpty()) {
                filename = "json_data_" + position;
            }

            File file = new File(getExternalFilesDir(null), filename + ".json");
            FileWriter writer = new FileWriter(file);

            // Write metadata and JSON data
            writer.write("// Saved on: " + new java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss",
                    java.util.Locale.getDefault()).format(new java.util.Date()) + "\n");
            writer.write("// Position: " + position + "\n");
            writer.write("// Blob Name: " + blobName + "\n\n");
            writer.write(jsonData);
            writer.close();
        } catch (IOException e) {
            Toast.makeText(this, "Error saving JSON file: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Get the device's IP address
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