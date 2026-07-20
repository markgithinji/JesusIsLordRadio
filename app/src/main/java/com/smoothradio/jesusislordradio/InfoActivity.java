package com.smoothradio.jesusislordradio;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;


public class InfoActivity extends AppCompatActivity {
    TextView tvInfo;
    TextView tvFbLink;
    TextView tvEmail;
    String info;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
        tvInfo =findViewById(R.id.tvInfo);
        tvFbLink= findViewById(R.id.fbAddress);
        tvEmail= findViewById(R.id.tvEmail);
        info = "1. Please give a few seconds (depending on your internet connection) for the radio station to prepare."+"\n"+"\n"+
                "2. Use Refresh Button if playback becomes laggy or choppy."+"\n"+"\n"+
                "3. The station may have low sound volume by default; This could require you to turn up the volume."+"\n"+"\n"+
                "4. The station is confirmed working. If the station fails to work, usually the owner has stopped streaming; Try again later."+"\n"+"\n"+
                "5. Logo, link and station details are property of JesusIsLord Radio."+"\n"+"\n"+
                "6. For feedback and assistance, please contact our support Center:";
        tvInfo.setText(info);
        tvFbLink.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);
        tvEmail.setPaintFlags(Paint.UNDERLINE_TEXT_FLAG);

        tvFbLink.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Intent fbIntent = new Intent(Intent.ACTION_VIEW);
                fbIntent.setData(Uri.parse("https://web.facebook.com/Smooth-Radio-App-Kenya-102378815380103"));
                startActivity(fbIntent);
            }
        });
        tvEmail.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                String appVersion="App Version: ";
                try {
                    appVersion+= getApplicationInfo().loadLabel(getPackageManager()).toString();
                    appVersion+= " "+getPackageManager().getPackageInfo(getPackageName(),0).versionName;
                } catch (PackageManager.NameNotFoundException e) {
                    e.printStackTrace();
                }
                String deviceName="Device Name: "+Build.MANUFACTURER+" "+Build.MODEL;
                String androidVersion = "Android Version: "+Build.VERSION.RELEASE;

                Intent mailIntent = new Intent(Intent.ACTION_SENDTO);
                mailIntent.setData(Uri.parse("mailto:"));
                mailIntent.putExtra(Intent.EXTRA_EMAIL,new String[]{"smoothradioapp@gmail.com"});
                mailIntent.putExtra(Intent.EXTRA_TEXT,appVersion+"\n"+deviceName+"\n"+androidVersion+"\n"+"\n");
                mailIntent.putExtra(Intent.EXTRA_SUBJECT,"JESUS IS LORD RADIO APP FEEDBACK");

                try {
                    if(mailIntent.resolveActivity(getPackageManager())!=null){
                    startActivity(Intent.createChooser(mailIntent,"Send Mail..."));
                    }
                }
                catch (ActivityNotFoundException e)
                {
                    Toast.makeText(InfoActivity.this, "There is no email client installed.", Toast.LENGTH_SHORT).show();
                }
            }
        });


    }

    @Override
    public void onBackPressed() {
        finish();
    }
}