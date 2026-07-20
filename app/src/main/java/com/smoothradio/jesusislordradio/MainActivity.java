package com.smoothradio.jesusislordradio;


import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.splashscreen.SplashScreen;
import androidx.transition.TransitionManager;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.airbnb.lottie.LottieAnimationView;
import com.google.android.gms.ads.AdError;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.FullScreenContentCallback;
import com.google.android.gms.ads.LoadAdError;
import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.interstitial.InterstitialAd;
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback;
import com.google.android.ump.ConsentForm;
import com.google.android.ump.ConsentInformation;
import com.google.android.ump.ConsentRequestParameters;
import com.google.android.ump.UserMessagingPlatform;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.EventListener;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QuerySnapshot;


import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicBoolean;

public class MainActivity extends AppCompatActivity {
    //UI
    ConstraintLayout constraintLayout;
    ImageView ivPlay;
    ImageView ivLargeLogo;
    TextView tvProgress;
    TextView tvMetadata;
    ImageView ivRefresh;
    ImageView ivSetTimer;
    ImageView ivInfo;
    String state = "";
    String metadata = "";
    String newMetadata = "";
    public LottieAnimationView equalizerAnimation;
    LottieAnimationView loadingAnimation;
    boolean isPlaying = false;
    //Ads
    AdView topAdView;
    public InterstitialAd interstitialAd;
    static boolean isShowingAd;
    static int adFailedCountdown = 0;
    boolean proceed;

    //Consent form
    private ConsentInformation consentInformation;
    // Use an atomic boolean to initialize the Google Mobile Ads SDK and load ads once.
    private final AtomicBoolean isMobileAdsInitializeCalled = new AtomicBoolean(false);

    //For starting service
    Intent serviceIntent;
    String url = "";
    //Broadcast Action
    public static final String Action_Change_Listener_Name = "JILEventChangeListener";
    public static final String Action_Update_UI = "JILstateFromService";
    public static final String Metadata_Change_Action_Name = "JILMetadataChangeListener";
    //onClickListeners
    PlayButton playButtonListener;
    Refresh refreshListener;
    SetTimer setTimerListener;
    Info infoListener;
    //receivers
    EventReceiver eventReceiver;
    UpdateUIReceiver updateUIReceiver;
    MetadataReceiver metadataReceiver;
    //OnlineDatabase
    FirebaseFirestore db = FirebaseFirestore.getInstance();
    public static ArrayList<String> linksFromTxt = new ArrayList<>();
    public static ArrayList<String> linksAfterUpdate = new ArrayList<>();
    SharedPreferences sharedPreferences;
    SharedPreferences.Editor prefsEditor;
    ListenerRegistration listenerRegistration;
    private boolean info;


    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        SplashScreen splashScreen = SplashScreen.installSplashScreen(this); // for displaying splash screen

        showConsentForm();

        setContentView(R.layout.activity_main);

        // Delay Startup for 2 secs
        new Handler(Looper.myLooper()).postDelayed(() -> proceed = true, 1800);

        final View content = findViewById(android.R.id.content);
        content.getViewTreeObserver().addOnPreDrawListener(
                new ViewTreeObserver.OnPreDrawListener() {
                    @Override
                    public boolean onPreDraw() {
                        // Check whether the initial data is ready.
                        if (proceed) {
                            // The content is ready. Start drawing.
                            content.getViewTreeObserver().removeOnPreDrawListener(this);
                            return true;
                        } else {
                            // The content isn't ready. Suspend.
                            return false;
                        }
                    }
                });

        //Ads
        topAdView = findViewById(R.id.adView);
        AdRequest topAdRequest = new AdRequest.Builder().build();
        topAdView.loadAd(topAdRequest);

        //Initial Check
        sharedPreferences = getSharedPreferences("JILSharedPref", Context.MODE_PRIVATE);
        prefsEditor = sharedPreferences.edit();
        boolean firstTime = sharedPreferences.getBoolean("isFirstTime", true);
        if (firstTime) {
            createInitialTxt();
            prefsEditor.putBoolean("isFirstTime", false);
            prefsEditor.apply();
        }

        txtToArrayList();
        url = linksFromTxt.get(0);

        //UI
        constraintLayout = findViewById(R.id.constaraintLayout);
        ivPlay = findViewById(R.id.ivPlayButton);
        ivLargeLogo = findViewById(R.id.ivLargeLogo);
        ivRefresh = findViewById(R.id.ivRefresh);
        tvProgress = findViewById(R.id.tvProgress);
        ivSetTimer = findViewById(R.id.ivSetTimer);
        ivInfo = findViewById(R.id.ivInfo);
        tvMetadata = findViewById(R.id.tvMetadata);
        //loadingAnimation
        loadingAnimation = findViewById(R.id.loadingAnimationRadioItem);
        loadingAnimation.setVisibility(View.INVISIBLE);
        //equalizerAnimation
        equalizerAnimation = findViewById(R.id.equalizerAnimation);
        equalizerAnimation.playAnimation();
        equalizerAnimation.setVisibility(View.INVISIBLE);
        //OnclickListeners
        playButtonListener = new PlayButton();
        refreshListener = new Refresh();
        setTimerListener = new SetTimer();
        infoListener = new Info();
        //Adding OnclickListeners
        ivPlay.setOnClickListener(playButtonListener);
        ivRefresh.setOnClickListener(refreshListener);
        ivSetTimer.setOnClickListener(setTimerListener);
        ivInfo.setOnClickListener(infoListener);

        //registering broadcast
        IntentFilter eventFilter = new IntentFilter();
        eventFilter.addAction(Action_Change_Listener_Name);//Player States
        eventReceiver = new EventReceiver();


        IntentFilter restoreUIReceiver = new IntentFilter();
        restoreUIReceiver.addAction(Action_Update_UI);//Get current player state and restore UI
        updateUIReceiver = new UpdateUIReceiver();


        IntentFilter metadataFilter = new IntentFilter();
        metadataFilter.addAction(Metadata_Change_Action_Name);
        metadataReceiver = new MetadataReceiver();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(eventReceiver, eventFilter, RECEIVER_NOT_EXPORTED);
            registerReceiver(updateUIReceiver, restoreUIReceiver, RECEIVER_NOT_EXPORTED);
            registerReceiver(metadataReceiver, metadataFilter, RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(eventReceiver, eventFilter);
            registerReceiver(updateUIReceiver, restoreUIReceiver);
            registerReceiver(metadataReceiver, metadataFilter);
        }

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions();
        }

        //intent for starting service
        serviceIntent = new Intent(MainActivity.this, StreamService.class);
    }

    public void play() {
        serviceIntent.setAction(StreamService.ACTION_START);
        serviceIntent.putExtra("url", url);
        serviceIntent.putExtra("logo", R.id.ivLargeLogo);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }
        isPlaying = true;
    }

    public class PlayButton implements View.OnClickListener {
        @Override
        public void onClick(View view) {

            if (isPlaying) {
                stopService(serviceIntent);
            } else {
                state = "Preparing Audio";
                updateUI();// to show animation
                if (!isShowingAd) {

                    loadInterstitialAd();
                    checkInternet();
                }else{//if ad already loaded, show it; otherwise nothing will happen so continue waiting for ad to finish loading
//                    showAdWithoutReloading();
                }

            }
        }
    }

    public class Refresh implements View.OnClickListener {

        @Override
        public void onClick(View view) {

            //update ui pre-play
            state = "Preparing Audio";
            updateUI();// to show animation

            if (!isShowingAd) {
                if (isPlaying)// stop from within service is faster than stopping the whole service as a whole
                {
                    serviceIntent.setAction(StreamService.ACTION_SHOW_AD);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        startForegroundService(serviceIntent);
                    } else {
                        startService(serviceIntent);
                    }

                }
                loadInterstitialAd();
                checkInternet();
            }else{//if ad already loaded, show it; otherwise nothing will happen so continue waiting for ad to finish loading
//                showAdWithoutReloading();
            }
            Toast.makeText(MainActivity.this, "refreshed!", Toast.LENGTH_SHORT).show();
        }
    }

    //Broadcast Receiver
    public class EventReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            state = intent.getStringExtra("state");
            updateUI();
        }
    }

    public class MetadataReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            metadata = intent.getStringExtra("title");
            if (metadata != null) {
                newMetadata = metadata.substring(0, Math.min(metadata.length(), 70));
                tvMetadata.setText(newMetadata);
                TransitionManager.beginDelayedTransition(constraintLayout);
            }
        }
    }

    class Info implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            Intent intent = new Intent(MainActivity.this, InfoActivity.class);
            startActivity(intent);
            Toast.makeText(MainActivity.this, "info", Toast.LENGTH_SHORT).show();

        }
    }

    public class UpdateUIReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            state = intent.getStringExtra("stateUI");
            info = intent.getBooleanExtra("info", false);
            updateUI();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        loadInterstitialAdWithoutPlay();
        //Restore UI
        state = "";
        updateUI();// to show animation
        if (isShowingAd) {
            state = "Preparing Audio";
            updateUI();// to show animation
        } else {
            Intent getStateFromServiceIntent = new Intent();
            getStateFromServiceIntent.setAction("JILgetState");
            getStateFromServiceIntent.setPackage(getPackageName());
            sendBroadcast(getStateFromServiceIntent);//get ui state from service
        }
        //Update station from server
        new Thread(() -> listenerRegistration = db.collection("link").orderBy("index").addSnapshotListener(new EventListener<QuerySnapshot>() {
            @Override
            public void onEvent(QuerySnapshot value, FirebaseFirestoreException error) {

                if (error != null) {
                    Toast.makeText(MainActivity.this, "Error updating from server", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (value != null && !value.isEmpty()) {
                    File file = new File(getFilesDir(), "file.txt");
                    BufferedWriter writer = null;
                    try {
                        writer = new BufferedWriter(new FileWriter(file));
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    for (DocumentSnapshot document : value.getDocuments()) {
                        try {
                            String data = document.getString("link");
                            if (data == null) {
                                writer.write("");
                            } else {
                                writer.write(data);
                                writer.newLine();
                            }
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                            // Toast.makeText(getApplicationContext(), "failed write", Toast.LENGTH_LONG).show();
                        } catch (IOException e) {
                            e.printStackTrace();
                            //Toast.makeText(getApplicationContext(), "no write", Toast.LENGTH_LONG).show();
                        }
                    }
                    try {
                        writer.flush();
                        writer.close();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    //txtToArrayList();
                    txtToArrayListAfterUpdate();
                    if (!linksFromTxt.equals(linksAfterUpdate)) {
                        linksFromTxt.clear();
                        linksFromTxt.addAll(linksAfterUpdate);
                        url = linksFromTxt.get(0);
                        Toast.makeText(MainActivity.this, "Station updated.", Toast.LENGTH_SHORT).show();
                    }

                } else {
                    Toast.makeText(MainActivity.this, "Error updating from server", Toast.LENGTH_SHORT).show();
                }
            }
        })).start();

    }

    void updateUI() {
        TransitionManager.beginDelayedTransition(constraintLayout);

        if (state.equals("Preparing Audio")) {
            tvProgress.setText(state);
            tvMetadata.setText("");
            loadingAnimation.setVisibility(View.VISIBLE);
            equalizerAnimation.setVisibility(View.INVISIBLE);
            isPlaying = true;

        } else if (state.equals("Idle")) {
            tvProgress.setText("");
            tvMetadata.setText("");
            loadingAnimation.setVisibility(View.INVISIBLE);
            equalizerAnimation.setVisibility(View.INVISIBLE);
            if (info) {
                ivPlay.setImageResource(R.drawable.play_button_background);
                info = false;
            } else {
                ivPlay.setImageResource(R.drawable.play_button_background);
            }
            isPlaying = false;
        } else if (state.equals("Playing")) {
            tvProgress.setText(state);
            loadingAnimation.setVisibility(View.INVISIBLE);
            equalizerAnimation.setVisibility(View.VISIBLE);
            if (info) {
                ivPlay.setImageResource(R.drawable.pause_icon);
                info = false;
            } else {
                ivPlay.setImageResource(R.drawable.pause_icon);
            }
            isPlaying = true;
        } else if (state.equals("Ended")) {
            tvProgress.setText(state);
            tvMetadata.setText("");
            loadingAnimation.setVisibility(View.INVISIBLE);
            equalizerAnimation.setVisibility(View.INVISIBLE);
            if (info) {
                ivPlay.setImageResource(R.drawable.play_button_background);
                info = false;
            } else {
                ivPlay.setImageResource(R.drawable.play_button_background);
            }
            isPlaying = false;
        } else if (state.equals("Buffering")) {
            tvProgress.setText(state);
            loadingAnimation.setVisibility(View.VISIBLE);
            equalizerAnimation.setVisibility(View.INVISIBLE);
            if (info) {

                ivPlay.setImageResource(R.drawable.play_button_background);
                info = false;
            } else {
                ivPlay.setImageResource(R.drawable.play_button_background);
            }
            isPlaying = true;
        } else {
            state = "";
            tvProgress.setText(state);
            tvMetadata.setText("");
            loadingAnimation.setVisibility(View.INVISIBLE);
            equalizerAnimation.setVisibility(View.INVISIBLE);
            if (info) {
                ivPlay.setImageResource(R.drawable.play_button_background);
                info = false;
            } else {
                ivPlay.setImageResource(R.drawable.play_button_background);
            }
            isPlaying = false;
        }
    }


    void txtToArrayList() {
        try {
            // Toast.makeText(MainActivity.this, "reading", Toast.LENGTH_SHORT).show();
            linksFromTxt.clear();
            File file = new File(getFilesDir(), "file.txt");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            BufferedReader br = new BufferedReader(reader);
            linksFromTxt.add(br.readLine());

        } catch (FileNotFoundException e) {
            e.printStackTrace();
            // Toast.makeText(MainActivity.this, "file not found", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            //Toast.makeText(MainActivity.this, "ioexcept", Toast.LENGTH_SHORT).show();
        }
    }

    void txtToArrayListAfterUpdate() {
        try {
            // Toast.makeText(MainActivity.this, "reading", Toast.LENGTH_SHORT).show();
            linksAfterUpdate.clear();
            File file = new File(getFilesDir(), "file.txt");
            BufferedReader reader = new BufferedReader(new FileReader(file));
            BufferedReader br = new BufferedReader(reader);
            linksAfterUpdate.add(br.readLine());
            //Toast.makeText(MainActivity.this, "file read succeful", Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            //Toast.makeText(MainActivity.this, "file not found", Toast.LENGTH_SHORT).show();
        } catch (IOException e) {
            e.printStackTrace();
            //Toast.makeText(MainActivity.this, "ioexcept", Toast.LENGTH_SHORT).show();
        }
    }


    void createInitialTxt() {
        File file = new File(getFilesDir(), "file.txt");
        BufferedWriter writer = null;
        try {
            writer = new BufferedWriter(new FileWriter(file));
        } catch (IOException e) {
            e.printStackTrace();
        }
        try {
            writer.write("https://s3.radio.co/s97f38db97/listen?1629657993604");//0
            writer.newLine();
            // Toast.makeText(this, "initial list used", Toast.LENGTH_SHORT).show();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            //  Toast.makeText(getApplicationContext(), "failed write", Toast.LENGTH_LONG).show();
        } catch (IOException e) {
            e.printStackTrace();
            // Toast.makeText(getApplicationContext(), "no write", Toast.LENGTH_LONG).show();
        }
        try {
            writer.flush();
            writer.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    void showAd() {
        if (interstitialAd != null) {
            isShowingAd = true;
            interstitialAd.show(MainActivity.this);
            interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
                @Override
                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
                    super.onAdFailedToShowFullScreenContent(adError);
                    interstitialAd = null;
                    isShowingAd = false;
                    stopService(serviceIntent);
//                    loadInterstitialAd();
                }

                @Override
                public void onAdDismissedFullScreenContent() {
                    super.onAdDismissedFullScreenContent();
                    interstitialAd = null;
                    play();
                    isShowingAd = false;
                }
            });
        } else {
            loadInterstitialAd();
        }
    }

    void loadInterstitialAd() {
        if (interstitialAd == null) {
            isShowingAd = true;
            AdRequest interstitialAdRequest = new AdRequest.Builder().build();
            InterstitialAd.load(this, "ca-app-pub-9799428944156340/9540019228", interstitialAdRequest, new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    super.onAdLoaded(interstitialAd);
                    MainActivity.this.interstitialAd = interstitialAd;
                    if (isPlaying)//only show ad if user has pressed play and hasn't stopped the service manually.
                    {
                        showAd();
                    }
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    interstitialAd = null;
                    if (loadAdError.getCode() == 1 || loadAdError.getCode() == 3) {
                        play();// only play when user has seen enough ads or no ad fill
                        isShowingAd = false;
                        adFailedCountdown = 0;
                    } else {
                        countdownAdFailed();
                    }
                }
            });
        } else {
            if (isPlaying)//only show ad if user has pressed play and hasn't stopped the service manually.
            {
                showAd();
            }
        }
    }

    void loadInterstitialAdWithoutPlay() {
        if (interstitialAd == null) {
            AdRequest interstitialAdRequest = new AdRequest.Builder().build();
            InterstitialAd.load(this, "ca-app-pub-9799428944156340/9540019228", interstitialAdRequest, new InterstitialAdLoadCallback() {
                @Override
                public void onAdLoaded(@NonNull InterstitialAd interstitialAd) {
                    super.onAdLoaded(interstitialAd);
                    MainActivity.this.interstitialAd = interstitialAd;
                }

                @Override
                public void onAdFailedToLoad(@NonNull LoadAdError loadAdError) {
                    super.onAdFailedToLoad(loadAdError);
                    interstitialAd = null;
                    loadInterstitialAdWithoutPlay();
                }
            });
        }
    }

//    void showAdWithoutReloading() {
//        if (interstitialAd != null) {
//            isShowingAd = true;
//            interstitialAd.show(this);
//            MainActivity.this.interstitialAd.setFullScreenContentCallback(new FullScreenContentCallback() {
//                @Override
//                public void onAdDismissedFullScreenContent() {
//                    MainActivity.this.interstitialAd = null;
//
//                    play();
//                    isShowingAd = false;
//                    loadInterstitialAdWithoutPlay();
//                }
//
//                @Override
//                public void onAdFailedToShowFullScreenContent(@NonNull AdError adError) {
//                    MainActivity.this.interstitialAd = null;
//                    isShowingAd = false;
//                }
//            });
//        }else
//        {
//            loadInterstitialAdWithoutPlay();
//        }
//    }

    void showConsentForm() {
        // Create a ConsentRequestParameters object.
        ConsentRequestParameters params = new ConsentRequestParameters
                .Builder()
                .build();

        consentInformation = UserMessagingPlatform.getConsentInformation(this);
        consentInformation.requestConsentInfoUpdate(
                this,
                params,
                (ConsentInformation.OnConsentInfoUpdateSuccessListener) () -> {
                    UserMessagingPlatform.loadAndShowConsentFormIfRequired(
                            this,
                            (ConsentForm.OnConsentFormDismissedListener) loadAndShowError -> {
                                if (loadAndShowError != null) {
                                    // Consent gathering failed.

                                }

                                // Consent has been gathered.
                                if (consentInformation.canRequestAds()) {
                                    initializeMobileAdsSdk();
                                }
                            }
                    );
                },
                (ConsentInformation.OnConsentInfoUpdateFailureListener) requestConsentError -> {
                    // Consent gathering failed.
                });

        // Check if you can initialize the Google Mobile Ads SDK in parallel
        // while checking for new consent information. Consent obtained in
        // the previous session can be used to request ads.
        if (consentInformation.canRequestAds()) {
            initializeMobileAdsSdk();
        }
    }

    private void initializeMobileAdsSdk() {
        if (isMobileAdsInitializeCalled.getAndSet(true)) {
            return;
        }

        // Initialize the Google Mobile Ads SDK.
        MobileAds.initialize(this);
        loadInterstitialAdWithoutPlay();
    }

    void countdownAdFailed()// counts no of times ad failed to load so as to stop trying to load a new ad
    {
        adFailedCountdown++;
        if (adFailedCountdown < 2) {
            loadInterstitialAd();
        } else {
            //update ui
            stopService(serviceIntent);
            state = "";
            updateUI();
            isPlaying = false;
            Toast.makeText(this, "Please check your internet and try again", Toast.LENGTH_SHORT).show();

            adFailedCountdown = 0;
            isShowingAd = false;// allow user to click button to attempt to start play again
        }
    }

    class SetTimer implements View.OnClickListener {

        @Override
        public void onClick(View view) {
            SetTimerDialog setTimerDialog = new SetTimerDialog(constraintLayout);
            setTimerDialog.show(getSupportFragmentManager(), "SetTimerFrag");
        }
    }

    void requestPermissions() {
        String[] permissions = {android.Manifest.permission.POST_NOTIFICATIONS};
        ActivityCompat.requestPermissions(this, permissions, 0);
    }

    void checkInternet() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(Context.CONNECTIVITY_SERVICE);
        NetworkInfo nInfo = cm.getActiveNetworkInfo();
        boolean connected = nInfo != null && nInfo.isAvailable() && nInfo.isConnected();
        if (!connected) {
            Toast.makeText(this, "Check Internet", Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (interstitialAd != null) {
            interstitialAd = null;
        }
        if (listenerRegistration != null) {
            listenerRegistration.remove();
        }
        unregisterReceiver(eventReceiver);
        unregisterReceiver(updateUIReceiver);
        unregisterReceiver(metadataReceiver);
        deleteCache(MainActivity.this);
    }

    public static void deleteCache(Context context) {
        try {
            File dir = context.getCacheDir();
            deleteDir(dir);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static boolean deleteDir(File dir) {
        if (dir != null && dir.isDirectory()) {
            String[] children = dir.list();
            for (int i = 0; i < children.length; i++) {
                boolean success = deleteDir(new File(dir, children[i]));
                if (!success) {
                    return false;
                }
            }
            return dir.delete();
        } else if (dir != null && dir.isFile()) {
            return dir.delete();
        } else {
            return false;
        }
    }

}