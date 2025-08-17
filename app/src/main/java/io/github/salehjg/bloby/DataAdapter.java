package io.github.salehjg.bloby;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;

public class DataAdapter extends RecyclerView.Adapter<DataAdapter.DataViewHolder> {

    public interface OnItemActionListener {
        void onViewClicked(String fullJson, int position);
        void onSendClicked(String fullJson, String blobName, int position);
        void onDeleteClicked(int position);
    }

    private ArrayList<DataItem> dataList;
    private OnItemActionListener actionListener;

    public DataAdapter() {
        this.dataList = new ArrayList<>();
        setHasStableIds(true); // Enable stable IDs
    }

    public void setOnItemActionListener(OnItemActionListener listener) {
        this.actionListener = listener;
    }

    @NonNull
    @Override
    public DataViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.list_item_main, parent, false);
        return new DataViewHolder(view);
    }

    @Override
    public long getItemId(int position) {
        return dataList.get(position).getId();
    }

    @Override
    public void onBindViewHolder(@NonNull DataViewHolder holder, int position) {
        DataItem item = dataList.get(position);
        holder.textViewData.setText("Blob: " + item.getBlobName());
        holder.textViewTimestamp.setText("DateTime: " + item.getDatetime());

        // Set button click listeners using getAdapterPosition() to get current position
        holder.buttonView.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            if (currentPosition != RecyclerView.NO_POSITION && actionListener != null) {
                actionListener.onViewClicked(dataList.get(currentPosition).getFullJson(), currentPosition);
            }
        });

        holder.buttonSend.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            if (currentPosition != RecyclerView.NO_POSITION && actionListener != null) {
                DataItem currentItem = dataList.get(currentPosition);
                actionListener.onSendClicked(currentItem.getFullJson(), currentItem.getBlobName(), currentPosition);
            }
        });

        holder.buttonDelete.setOnClickListener(v -> {
            int currentPosition = holder.getAdapterPosition();
            if (currentPosition != RecyclerView.NO_POSITION && actionListener != null) {
                actionListener.onDeleteClicked(currentPosition);
            }
        });
    }

    @Override
    public int getItemCount() {
        return dataList.size();
    }

    public void addJsonData(String blobName, String datetime, String fullJson) {
        String receivedTime = new SimpleDateFormat("HH:mm:ss", Locale.getDefault())
                .format(new Date());
        long id = System.currentTimeMillis() + dataList.size(); // Generate unique ID
        dataList.add(0, new DataItem(blobName, datetime, fullJson, receivedTime, id)); // Add to top
        notifyItemInserted(0);
    }

    public void removeItem(int position) {
        if (position >= 0 && position < dataList.size()) {
            dataList.remove(position);
            notifyItemRemoved(position);
        }
    }

    public DataItem getItem(int position) {
        if (position >= 0 && position < dataList.size()) {
            return dataList.get(position);
        }
        return null;
    }

    public void clearData() {
        dataList.clear();
        notifyDataSetChanged();
    }

    static class DataViewHolder extends RecyclerView.ViewHolder {
        TextView textViewData;
        TextView textViewTimestamp;
        Button buttonView;
        Button buttonSend;
        Button buttonDelete;

        public DataViewHolder(@NonNull View itemView) {
            super(itemView);
            textViewData = itemView.findViewById(R.id.textViewData);
            textViewTimestamp = itemView.findViewById(R.id.textViewTimestamp);
            buttonView = itemView.findViewById(R.id.buttonView);
            buttonSend = itemView.findViewById(R.id.buttonSend);
            buttonDelete = itemView.findViewById(R.id.buttonDelete);
        }
    }

    static class DataItem {
        private String blobName;
        private String datetime;
        private String fullJson;
        private String receivedTime;
        private long id;

        public DataItem(String blobName, String datetime, String fullJson, String receivedTime, long id) {
            this.blobName = blobName;
            this.datetime = datetime;
            this.fullJson = fullJson;
            this.receivedTime = receivedTime;
            this.id = id;
        }

        public String getBlobName() {
            return blobName;
        }

        public String getDatetime() {
            return datetime;
        }

        public String getFullJson() {
            return fullJson;
        }

        public String getReceivedTime() {
            return receivedTime;
        }

        public long getId() {
            return id;
        }
    }
}