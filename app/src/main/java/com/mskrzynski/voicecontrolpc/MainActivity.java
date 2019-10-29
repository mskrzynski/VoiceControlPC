package com.mskrzynski.voicecontrolpc;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.DividerItemDecoration;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.os.CountDownTimer;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.util.Log;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.Locale;
import static com.mskrzynski.voicecontrolpc.ItemArrayAdapter.does_socket_exists;
import static com.mskrzynski.voicecontrolpc.ItemArrayAdapter.nacisnietoConnect;
import static com.mskrzynski.voicecontrolpc.ItemArrayAdapter.nacisnietoDisconnect;
import static com.mskrzynski.voicecontrolpc.ItemArrayAdapter.startedtalking;
import static com.mskrzynski.voicecontrolpc.ItemArrayAdapter.stoppedtalking;
import static com.mskrzynski.voicecontrolpc.SocketThread.dis;
import static com.mskrzynski.voicecontrolpc.SocketThread.dos;
import static com.mskrzynski.voicecontrolpc.SocketThread.error_occured;
import static com.mskrzynski.voicecontrolpc.SocketThread.socket;
import static com.mskrzynski.voicecontrolpc.UDPBroadcast.listaAdresowIP;

public class MainActivity extends AppCompatActivity implements RecognitionListener {

    private Intent recognizerIntent;
    private ArrayList <Server> serverList = new ArrayList<>();
    private RecyclerView recyclerView;
    private ItemArrayAdapter itemArrayAdapter;
    private ProgressBar pasek;
    private Button search_computers;
    private TextView ipInput;
    private TextView portInput;
    private static String ip_remote;
    private static int port_remote;
    private static int szerokosc_obrazu;
    private static int wysokosc_obrazu;
    private static String ip_filename;
    private static SimpleDateFormat data;
    private static Date teraz;
    private static byte[] dane_obrazu;
    @SuppressLint("StaticFieldLeak")
    private static Context context;
    static SpeechRecognizer speech = null;
    static String wynik;
    static boolean permission_not_given = false;
    static boolean is_remote_server = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        //Wyświetlanie zgody na zapisywanie plików
        ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, 668);

        TextView nazwa_telefonu = findViewById(R.id.phone_name);
        search_computers = findViewById(R.id.searchpc_button);
        Button remote_connect_button = findViewById(R.id.remote_connect_button);
        ipInput = findViewById(R.id.ipInput);
        portInput = findViewById(R.id.portInput);
        pasek = findViewById(R.id.talking_progressBar);

        Log.i("MainActivity", "onCreate");

        //Ustawienia paska głośności mówienia
        pasek.setVisibility(View.VISIBLE);
        pasek.setMax(10);
        pasek.setProgress(0);
        pasek.setScaleY(0.25f);

        //utworzenie zmiennej context aby wyskakujące powiadomienia mogły być wykonywane również w innych klasach
        context = MainActivity.this.getApplicationContext();

        //Pokazanie nazwy telefonu
        nazwa_telefonu.setText(String.format("Nazwa telefonu: %s %s", Build.MANUFACTURER, Build.MODEL));

        //tworzymy instancję listy serwerów i jego adapter, oraz separator tychże serwerów
        recyclerView = findViewById(R.id.serverlist_recyclerView);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.addItemDecoration(new DividerItemDecoration(recyclerView.getContext(), DividerItemDecoration.VERTICAL));

        itemArrayAdapter = new ItemArrayAdapter(R.layout.server_item, serverList);
        recyclerView.setAdapter(itemArrayAdapter);

        //tworzymy instancję obsługi mowy
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        Log.i("MainActivity", "isRecognitionAvailable: " + SpeechRecognizer.isRecognitionAvailable(this));
        speech.setRecognitionListener(this);
        recognizerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE, Locale.getDefault()); //w zależności od ustawień telefonu (pisanie głosowe Google)
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        recognizerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        //Obsługa przycisku wyszukiwania serwerów
        search_computers.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {

                //Usuwamy bufor wyniku jeżeli wcześniej istniał
                wynik = "";

                //Ustawiamy układ wyglądu serwera na domyślny
                is_remote_server = false;
                nacisnietoConnect = false;
                nacisnietoDisconnect = false;
                startedtalking = false;
                stoppedtalking = false;
                permission_not_given = false;

                //Rozłącz z komputerem jeżeli jest jakiś połączony
                if (does_socket_exists) {
                        wynik = "\n";
                        does_socket_exists = false;
                        new SendWynik().execute();
                }

                //usuwanie obecnej listy komputerów jeżeli istnieje
                if(!serverList.isEmpty()) serverList.clear();

                //Tworzymy adapter na podstawie którego będziemy wypisywać serwery
                itemArrayAdapter = new ItemArrayAdapter(R.layout.server_item, serverList);
                recyclerView.setAdapter(itemArrayAdapter);

                Toast.makeText(MainActivity.this, "Trwa wyszukiwanie komputerów...", Toast.LENGTH_SHORT).show();

                //Uruchamianie wątku wyszukiwania komputerów
                new CreateUDPBroadcast().execute();

                //Zablokuj guzik w celu uniknięcia konfliktów
                search_computers.setClickable(false);

                //Opóźnienie wypisywania komputerów (3 sekundy) aby UDPBroadcast miał czas zadziałać
                new CountDownTimer(3000, 3000) {

                    public void onTick(long millisUntilFinished) {}

                    public void onFinish() {
                        //wypisanie komputerów jeżeli UDPBroadcast jakiekolwiek znalazł
                        if (!listaAdresowIP.isEmpty()) {
                            Toast.makeText(MainActivity.this, "Wyszukiwanie zakończone", Toast.LENGTH_SHORT).show();

                            //0,2,4... - adresy IP
                            //1,3,5... - hostname'y
                            int address = 0;
                            int hostname= 1;

                            for(int i=1; i<=listaAdresowIP.size()/2; i++){

                                serverList.add(new Server(listaAdresowIP.get(address), listaAdresowIP.get(hostname)));
                                itemArrayAdapter.notifyItemInserted(i);
                                address+=2;
                                hostname+=2;
                            }
                        }
                        else{
                            Toast.makeText(MainActivity.this, "Nie znaleziono komputerów!", Toast.LENGTH_SHORT).show();
                        }

                        //Odblokuj guzik
                        search_computers.setClickable(true);
                    }
                }.start();
            }
        });

        //Obsługa przycisku połączenia zdalnego
        remote_connect_button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                //sprawdzamy czy podano wszystkie wymagane dane
                if (ipInput.getText().length() > 0 && portInput.getText().length() > 0) {

                    //Usuwamy bufor wyniku jeżeli wcześniej istniał
                    wynik = "";

                    //Ustawiamy układ wyglądu serwera na domyślny
                    is_remote_server = false;
                    nacisnietoConnect = false;
                    nacisnietoDisconnect = false;
                    startedtalking = false;
                    stoppedtalking = false;
                    permission_not_given = false;

                    //Rozłącz z komputerem jeżeli jest jakiś połączony
                    if (does_socket_exists) {
                        wynik = "\n";
                        does_socket_exists = false;
                        new SendWynik().execute();
                    }

                    //usuwanie obecnej listy komputerów jeżeli istnieje
                    if(!serverList.isEmpty()) serverList.clear();

                    //Tworzymy adapter na podstawie którego będziemy wypisywać serwery
                    itemArrayAdapter = new ItemArrayAdapter(R.layout.server_item, serverList);
                    recyclerView.setAdapter(itemArrayAdapter);

                    //pobieramy wpisane dane i łączymy się z serwerem
                    ip_remote = ipInput.getText().toString();
                    port_remote = Integer.parseInt(portInput.getText().toString());

                    new CreateRemoteSocket().execute();

                    //opóźnienie w celu sprawdzenia czy tworzenie socketu wyrzuciło wyjątek
                    new CountDownTimer(1500, 1000) {

                        public void onTick(long millisUntilFinished) {
                            Toast.makeText(MainActivity.this, "Oczekiwanie na odpowiedź...", Toast.LENGTH_SHORT).show();
                        }

                        public void onFinish() {
                            if(!error_occured) {
                                //Ukrywamy klawiaturę jeżeli jest widoczna
                                ipInput.onEditorAction(EditorInfo.IME_ACTION_DONE);
                                portInput.onEditorAction(EditorInfo.IME_ACTION_DONE);

                                //Dodajemy serwer do listy
                                serverList.add(new Server(ip_remote, portInput.getText().toString()));
                                itemArrayAdapter.notifyItemInserted(1);
                                is_remote_server = true;
                                itemArrayAdapter.notifyDataSetChanged();
                                Toast.makeText(MainActivity.this, "Połączono z serwerem", Toast.LENGTH_SHORT).show();
                            }
                            else{
                                Toast.makeText(MainActivity.this, "Nie znaleziono serwera", Toast.LENGTH_SHORT).show();
                            }
                        }
                    }.start();
                }
                else{
                    Toast.makeText(MainActivity.this, "Nie podano IP i/lub portu", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    //Wątek odnajdywania lokalnego komputerów
    private static class CreateUDPBroadcast extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... urls) {
            Thread udpBroadcast = new Thread(new UDPBroadcast());
            udpBroadcast.start();
            return null;
        }
    }

    //Wątek łączenie zdalnego
    private static class CreateRemoteSocket extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... urls) {
            Thread socketThread = new Thread(new SocketThread(ip_remote, port_remote));
            socketThread.start();
            return null;
        }
    }

    //Funkcja wykonywana gdy naciśnięto przycisk mówienia (obsługa pozwolenia na użycie mikrofonu i rozpoczęcie nasłuchiwania)
    //Wykonywana również przy uruchomieniu programu aby wywołać zgodę na zapisywanie plików
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 667) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                speech.startListening(recognizerIntent);
            }
            else {
                permission_not_given = true;
                Toast.makeText(MainActivity.this, "Odmowa dostępu do mikrofonu.", Toast.LENGTH_SHORT).show();
                itemArrayAdapter.notifyDataSetChanged();
                permission_not_given = false;
            }
        }
        else if (requestCode == 668) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(MainActivity.this, "Zapisywanie zrzutów/obrazów kamery aktywowane!", Toast.LENGTH_SHORT).show();
            }
            else {
                Toast.makeText(MainActivity.this, "Zapisywanie zrzutów/obrazów kamery nie będzie możliwe!", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (speech != null) speech.destroy();
        Log.i("MainActivity", "onDestroy");
    }

    //Nie wyłączaj programu po naciśnięciu przycisku wstecz
    @Override
    public void onBackPressed() {
        moveTaskToBack(true);
    }

    @Override
    protected void onStart() {
        super.onStart();
        speech = SpeechRecognizer.createSpeechRecognizer(this);
        Log.i("MainActivity", "isRecognitionAvailable: " + SpeechRecognizer.isRecognitionAvailable(this));
        speech.setRecognitionListener(this);
        Log.i("MainActivity", "onStart");
    }

    @Override
    public void onBeginningOfSpeech() {
        Log.i("MainActivity", "onBeginningOfSpeech");
    }

    @Override
    public void onBufferReceived(byte[] buffer) {
        Log.i("MainActivity", "onBufferReceived: " + Arrays.toString(buffer));
    }

    @Override
    public void onEndOfSpeech() {
        Log.i("MainActivity", "onEndOfSpeech");
    }

    @Override
    public void onError(int errorCode) {
        pasek.setProgress(0);
        wynik = getErrorText(errorCode);
        Log.d("MainActivity", "onError: " + wynik);

        //Ignorowanie błędów klienta i przekazywanie innych (błąd klienta pojawia się przy pierwszym nasłuchu od razu)
        if(errorCode != 5) itemArrayAdapter.notifyDataSetChanged();
    }

    @Override
    public void onEvent(int arg0, Bundle arg1) {
        Log.i("MainActivity", "onEvent");
    }

    @Override
    public void onPartialResults(Bundle arg0) {
        Log.i("MainActivity", "onPartialResults");
    }

    @Override
    public void onReadyForSpeech(Bundle arg0) {
        Log.i("MainActivity", "onReadyForSpeech");
    }

    @Override
    public void onResults(Bundle results) {
        pasek.setProgress(0);
        Log.i("MainActivity", "onResults");
        ArrayList<String> matches = results.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);

        //zapisanie wyniku nasłuchiwania
        if (matches != null) wynik = matches.get(0) + "\n";

        //przekazanie wyniku i jego wypisanie
        new SendWynik().execute();

        itemArrayAdapter.notifyDataSetChanged();
    }

    @Override
    public void onRmsChanged(float rmsdB) {
        Log.i("MainActivity", "onRmsChanged: " + rmsdB);
        pasek.setProgress((int) rmsdB);
    }

    public static String getErrorText(int errorCode) {
        String blad;
        switch (errorCode) {
            case SpeechRecognizer.ERROR_AUDIO:
                blad = "Błąd nagrywania dźwięku";
                break;
            case SpeechRecognizer.ERROR_CLIENT:
                blad = "Błąd klienta";
                break;
            case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS:
                blad = "Niewystarczające pozwolenia";
                break;
            case SpeechRecognizer.ERROR_NETWORK:
                blad = "Błąd sieciowy";
                break;
            case SpeechRecognizer.ERROR_NETWORK_TIMEOUT:
                blad = "Przekroczono limit czasu sieci";
                break;
            case SpeechRecognizer.ERROR_NO_MATCH:
                blad = "Nie zrozumiano polecenia, spróbuj jeszcze raz";
                break;
            case SpeechRecognizer.ERROR_RECOGNIZER_BUSY:
                blad = "RecognitionService jest zajęty";
                break;
            case SpeechRecognizer.ERROR_SERVER:
                blad = "Błąd serwera";
                break;
            case SpeechRecognizer.ERROR_SPEECH_TIMEOUT:
                blad = "Nie wydano żadnego polecenia";
                break;
            default:
                blad = "Nieznany błąd";
                break;
        }
        return blad;
    }

    private static class SendWynik extends AsyncTask<Void, Void, Void> {
        protected Void doInBackground(Void... urls) {
            try {
                if(dos != null) {
                    dos.write(wynik.getBytes());
                }
                else {
                    Handler h = new Handler(Looper.getMainLooper());
                    h.post(new Runnable() {
                        public void run() {
                            if(does_socket_exists) {
                                Toast.makeText(context, "Błąd wysyłania odpowiedzi, sprawdż czy podano prawidłowy serwer i czy on działa prawidłowo", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                }
            }
            catch (IOException e) {
                    Handler h = new Handler(Looper.getMainLooper());
                    h.post(new Runnable() {
                        public void run() {
                            if(does_socket_exists) {
                                Toast.makeText(context, "Błąd wysyłania odpowiedzi, sprawdż czy podano prawidłowy serwer i czy on działa prawidłowo", Toast.LENGTH_LONG).show();
                            }
                        }
                    });
                e.printStackTrace();
            }
            //sprawdzamy czy podano wyraz specjalny
            if (wynik.equals("zrzut\n") || wynik.equals("kamera\n")) {

                Handler h = new Handler(Looper.getMainLooper());
                h.post(new Runnable() {
                    public void run() {
                        Toast.makeText(context, "Oczekiwanie na odpowiedź...", Toast.LENGTH_SHORT).show();
                    }
                });

                try {
                    //pobieranie danych do nazwy  pliku (adres IP oraz obecna data)
                    ip_filename = socket.getInetAddress().toString();
                    ip_filename = ip_filename.replace( "/", "" );

                    data = new SimpleDateFormat("yyyy_MM_dd_HH_mm_ss", Locale.getDefault());
                    teraz = new Date();

                    //odbieranie obrazu od serwera
                    szerokosc_obrazu = dis.readInt();
                    if (szerokosc_obrazu == 1) {
                        Handler ha = new Handler(Looper.getMainLooper());
                        ha.post(new Runnable() {
                            public void run() {
                                Toast.makeText(context, "Serwer nie ma podpiętej kamery", Toast.LENGTH_SHORT).show();
                            }
                        });
                    }
                    else {
                        wysokosc_obrazu = dis.readInt();
                        dane_obrazu = new byte[szerokosc_obrazu * wysokosc_obrazu * 4];
                        dis.readFully(dane_obrazu);
                        if (wynik.equals("zrzut\n")) onScreenshot();
                        else if (wynik.equals("kamera\n")) onWebcam();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            return null;
        }
    }

    public static void onScreenshot() {
        Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            public void run() {
                // zamiana otrzymanego obrazu z typu ABGR na ARGB
                Bitmap bitmapa = ConvertToARGB(szerokosc_obrazu, wysokosc_obrazu, dane_obrazu);

                //zapisanie obrazu do telefonu
                File screenshotFile = new File(Environment.getExternalStorageDirectory() +
                        "/VoiceControlPC_Screenshots");

                if (!screenshotFile.exists()) {
                    File screenshotFileDirectory = new File(Environment.getExternalStorageDirectory() +
                            "/VoiceControlPC_Screenshots/");
                    screenshotFileDirectory.mkdirs();
                }

                File screenshot = new File(new File(Environment.getExternalStorageDirectory() +
                        "/VoiceControlPC_Screenshots/"), "zrzut_" + ip_filename + "_" + data.format(teraz) + ".png");
                if (screenshot.exists()) {
                    screenshot.delete();
                }

                try {
                    //Zapisanie pliku
                    FileOutputStream fos = new FileOutputStream(screenshot);
                    bitmapa.compress(Bitmap.CompressFormat.PNG, 100, fos);
                    fos.flush();
                    fos.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }

                Toast.makeText(context, "Pomyślnie zapisano w galerii zrzut ekranu", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static void onWebcam() {
        Handler h = new Handler(Looper.getMainLooper());
        h.post(new Runnable() {
            public void run() {
                // zamiana otrzymanego obrazu z typu ABGR na ARGB
                Bitmap bitmapa = ConvertToARGB(szerokosc_obrazu, wysokosc_obrazu, dane_obrazu);

                //zapisanie obrazu do telefonu
                File webcamFile = new File(Environment.getExternalStorageDirectory() +
                        "/VoiceControlPC_Webcams");

                if (!webcamFile.exists()) {
                    File webcamFileDirectory = new File(Environment.getExternalStorageDirectory() +
                            "/VoiceControlPC_Webcams/");
                    webcamFileDirectory.mkdirs();
                }

                File webcam = new File(new File(Environment.getExternalStorageDirectory() +
                        "/VoiceControlPC_Webcams/"), "kamera_" + ip_filename + "_" + data.format(teraz) + ".jpg");
                if (webcam.exists()) {
                    webcam.delete();
                }

                try {
                    FileOutputStream fos = new FileOutputStream(webcam);
                    bitmapa.compress(Bitmap.CompressFormat.JPEG, 100, fos);
                    fos.flush();
                    fos.close();
                }
                catch (IOException e) {
                    e.printStackTrace();
                }

                Toast.makeText(context, "Pomyślnie zapisano w galerii obraz kamery", Toast.LENGTH_SHORT).show();
            }
        });
    }

    public static Bitmap ConvertToARGB(int szerokosc, int wysokosc, byte[] dane){
        int[] piksele = new int[szerokosc * wysokosc];
        for (int i = 0; i < piksele.length; i++) {
            int daneIndeks = i * 4;
            piksele[i] = ((dane[daneIndeks] & 0xFF) << 24)
                            | ((dane[daneIndeks + 3] & 0xFF) << 16)
                            | ((dane[daneIndeks + 2] & 0xFF) << 8)
                            | (dane[daneIndeks + 1] & 0xFF);
        }
        return Bitmap.createBitmap(piksele, szerokosc, wysokosc, Bitmap.Config.ARGB_8888);
    }
}

