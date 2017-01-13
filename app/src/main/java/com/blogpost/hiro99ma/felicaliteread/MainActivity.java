package com.blogpost.hiro99ma.felicaliteread;

import android.content.Intent;
import android.nfc.Tag;
import android.nfc.TagLostException;
import android.nfc.tech.MifareUltralight;
import android.os.RemoteException;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.Toast;

import com.blogpost.hiro99ma.nfc.FelicaLite;
import com.blogpost.hiro99ma.nfc.NfcFactory;

import java.io.IOException;


public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    @Override
    public void onResume() {
        super.onResume();
        boolean ret = NfcFactory.nfcResume(MainActivity.this);
        if (!ret) {
            Log.e(TAG, "fail : resume");
            Toast.makeText(MainActivity.this, "NFC cannot use.", Toast.LENGTH_LONG).show();
        }
    }

    @Override
    public void onPause() {
        NfcFactory.nfcPause(this);
        super.onPause();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);

        Tag tag = NfcFactory.getTag(intent);
        try {
            MifareUltralight mfl;
            FelicaLite felica = FelicaLite.get(tag);
            if (felica == null) {
                return;
            }
            felica.connect();
            byte[] rd = felica.readBlock(0x83);
            if(rd != null) {
                String s = "";
                for (byte r : rd) {
                    s += String.format("%02x-", r);
                }
                Log.d(TAG, s);
            }
            felica.close();
        } catch (TagLostException e) {
            Log.d(TAG, "Tag Lost.");
        } catch (IOException | RemoteException e) {
            e.printStackTrace();
        }
    }
}
