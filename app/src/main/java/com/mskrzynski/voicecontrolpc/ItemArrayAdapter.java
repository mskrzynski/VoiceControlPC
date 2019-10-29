package com.mskrzynski.voicecontrolpc;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.AsyncTask;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.widget.ToggleButton;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.RecyclerView;
import java.io.IOException;
import java.util.ArrayList;
import static com.mskrzynski.voicecontrolpc.MainActivity.is_remote_server;
import static com.mskrzynski.voicecontrolpc.MainActivity.speech;
import static com.mskrzynski.voicecontrolpc.MainActivity.wynik;
import static com.mskrzynski.voicecontrolpc.MainActivity.permission_not_given;
import static com.mskrzynski.voicecontrolpc.SocketThread.dos;
import static com.mskrzynski.voicecontrolpc.SocketThread.socket;

public class ItemArrayAdapter extends RecyclerView.Adapter<ItemArrayAdapter.ViewHolder> {

    private int listItemLayout;
    private int clickedButtonPosition = -1;
    private ArrayList<Server> itemList;
    private AsyncTask createSocket;
    private static String ipAddress;
    static boolean does_socket_exists = false;
    static boolean nacisnietoConnect = false;
    static boolean nacisnietoDisconnect = false;
    static boolean startedtalking = false;
    static boolean stoppedtalking = false;

    ItemArrayAdapter(int layoutId, ArrayList<Server> itemList) {
        listItemLayout = layoutId;
        this.itemList = itemList;
    }

    @Override
    public int getItemCount() {
        if (itemList == null) return 0;
        else return itemList.size();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        final View view = LayoutInflater.from(parent.getContext()).inflate(listItemLayout, parent, false);
        return new ViewHolder(view, new ViewHolder.MyClickListener() {

            @Override
            public void onConnect(int position) {
                nacisnietoConnect = true;
                nacisnietoDisconnect = false;
                startedtalking = false;
                stoppedtalking = false;
                clickedButtonPosition = position;
                notifyDataSetChanged();

                //usuwamy poprzedni socket jeśli istnieje
                if(does_socket_exists) new SendStopSign().execute();

                //tworzymy socket z serwerem
                ipAddress = itemList.get(position).getIpAddress();
                createSocket = new CreateSocket().execute();
                does_socket_exists = true;
            }
            
            @Override
            public void onDisconnect(int position){
                nacisnietoConnect = false;
                nacisnietoDisconnect = true;
                startedtalking = false;
                stoppedtalking = false;
                clickedButtonPosition = position;
                notifyDataSetChanged();
                new SendStopSign().execute();
                createSocket.cancel(false);
                does_socket_exists = false;
            }

            @Override
            public void onTalk(int position) {
                nacisnietoConnect = false;
                nacisnietoDisconnect = false;
                startedtalking = true;
                stoppedtalking = false;
                clickedButtonPosition = position;

                ActivityCompat.requestPermissions((Activity) view.getContext(), new String[]{Manifest.permission.RECORD_AUDIO}, 667);
            }

            @Override
            public void onEndTalk(int position){
                nacisnietoConnect = false;
                nacisnietoDisconnect = false;
                startedtalking = false;
                stoppedtalking = true;
                clickedButtonPosition = position;
                notifyDataSetChanged();
            }
        });
    }

    @SuppressLint("SetTextI18n")
    @Override
    public void onBindViewHolder(@NonNull final ViewHolder holder, final int listPosition) {
        holder.ip_address.setText(itemList.get(listPosition).getIpAddress());
        holder.hostname.setText(itemList.get(listPosition).getHostname());
        holder.talk_button.setClickable(false);
        holder.connect_button.setChecked(false);

        if(is_remote_server){
                holder.ip_address.setText(itemList.get(listPosition).getIpAddress() + ":" + itemList.get(listPosition).getHostname());
                holder.hostname.setText("n/a");
                holder.connect_button.setChecked(true);
                holder.connect_button.setClickable(false);
                holder.talk_button.setClickable(true);
                does_socket_exists = true;
        }

        if(startedtalking && !permission_not_given){
            if (clickedButtonPosition == listPosition){
                holder.status.setText(wynik);
                holder.talk_button.setChecked(false);
                holder.talk_button.setClickable(true);
                holder.connect_button.setChecked(true);
            }
        }

        if(stoppedtalking){
            if (clickedButtonPosition == listPosition){
                holder.talk_button.setClickable(true);
                speech.stopListening();
                holder.connect_button.setChecked(true);
            }

        }

        if(nacisnietoConnect) {
            if (clickedButtonPosition == listPosition){
                holder.connect_button.setChecked(true);
                holder.talk_button.setClickable(true);
                speech.stopListening();
            }

            else {
                holder.connect_button.setChecked(false);
                holder.talk_button.setChecked(false);
                holder.talk_button.setClickable(false);
            }
        }

        if(nacisnietoDisconnect) {
            if (clickedButtonPosition == listPosition){
                holder.connect_button.setChecked(false);
                holder.talk_button.setChecked(false);
                holder.talk_button.setClickable(false);
                speech.stopListening();
            }
        }

        if(permission_not_given) {
            if (clickedButtonPosition == listPosition){
                holder.connect_button.setChecked(true);
                holder.talk_button.setChecked(false);
                holder.talk_button.setClickable(true);
            }
        }
    }

    static class ViewHolder extends RecyclerView.ViewHolder implements View.OnClickListener{
        MyClickListener listener;
        TextView ip_address;
        TextView hostname;
        ToggleButton connect_button;
        ToggleButton talk_button;
        TextView status;

        ViewHolder(View itemView, MyClickListener listener) {
            super(itemView);

            ip_address = itemView.findViewById(R.id.ipAddress);
            hostname = itemView.findViewById(R.id.hostname);
            connect_button = itemView.findViewById(R.id.connect_button);
            talk_button = itemView.findViewById(R.id.talk_button);
            status = itemView.findViewById(R.id.status);

            this.listener = listener;
            connect_button.setOnClickListener(this);
            talk_button.setOnClickListener(this);
        }

        @Override
        public void onClick(View view) {
            switch (view.getId()) {
                case R.id.connect_button:

                    boolean connect_on = ((ToggleButton) view).isChecked();

                    if(connect_on){
                        listener.onConnect(this.getLayoutPosition());
                    }
                    else{
                        listener.onDisconnect(this.getLayoutPosition());
                    }
                    break;

                case R.id.talk_button:

                    boolean talk_on = ((ToggleButton) view).isChecked();

                    if(talk_on){
                        listener.onTalk(this.getLayoutPosition());
                    }
                    else{
                        listener.onEndTalk(this.getLayoutPosition());
                    }
                    break;

                default:
                    break;
            }
        }

        public interface MyClickListener {
            void onConnect(int position);
            void onDisconnect(int position);
            void onTalk(int position);
            void onEndTalk(int position);
        }
    }

    private static class CreateSocket extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... urls) {
            Thread socketThread = new Thread(new SocketThread(ipAddress, 8163));
            socketThread.start();
            return null;
        }

        protected void onCancelled(Void result) {
            try {
                socket.close();
                dos.close();
            }
            catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    private static class SendStopSign extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... urls) {
            try {
                //Wiadomośc stopu
                String stop_signal = "\n";
                dos.write(stop_signal.getBytes());
            }
            catch (IOException e) {
                e.printStackTrace();
            }
            return null;
        }
    }
}
