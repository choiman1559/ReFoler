package com.refoler.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.refoler.Refoler;
import com.refoler.app.R;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.consts.RecordConst;
import com.refoler.app.process.service.SyncFileListService;
import com.refoler.app.utils.JsonRequest;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Button uploadFileNow = findViewById(R.id.uploadFileNow);
        Button registerDeviceNow = findViewById(R.id.registerDeviceNow);

        uploadFileNow.setOnClickListener((v) -> {
            startService(new Intent(this, SyncFileListService.class));
        });

        registerDeviceNow.setOnClickListener((v) -> {
            Refoler.RequestPacket.Builder requestBuilder = Refoler.RequestPacket.newBuilder();
            requestBuilder.setActionName(RecordConst.SERVICE_ACTION_TYPE_POST);
            requestBuilder.addDevice(DeviceWrapper.getSelfDeviceInfo(this));
            JsonRequest.postRequestPacket(this, RecordConst.SERVICE_TYPE_DEVICE_REGISTRATION, requestBuilder, (response) -> {
                Log.d("BackendImplementation", "Device Registered!");
            });
        });
    }
}
