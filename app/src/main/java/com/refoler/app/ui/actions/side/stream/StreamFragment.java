package com.refoler.app.ui.actions.side.stream;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.media3.common.util.UnstableApi;
import androidx.media3.exoplayer.DefaultLoadControl;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.LoadControl;
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory;
import androidx.media3.ui.PlayerView;

import com.refoler.Refoler;
import com.refoler.app.R;
import com.refoler.app.backend.consts.EndPointConst;
import com.refoler.app.process.db.RemoteFile;
import com.refoler.app.ui.holder.SideFragment;

@UnstableApi
public class StreamFragment extends SideFragment {

    RemoteFile remoteFile;
    Refoler.Device device;
    ExoPlayer player;

    public StreamFragment() {
        // Default constructor for fragment manager
    }

    public StreamFragment(Refoler.Device device, RemoteFile remoteFile) {
        this.remoteFile = remoteFile;
        this.device = device;
    }

    @NonNull
    @Override
    public String getFragmentId() {
        return String.format("%s%s%s", device.getDeviceId(), EndPointConst.FILE_PART_CONTROL_SEPARATOR, remoteFile.getPath());
    }

    @Override
    public void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putSerializable("device", device);
        outState.putSerializable("remoteFile", remoteFile.getSerializeOptimized());
    }

    @Override
    public OnBackPressedCallback getOnBackDispatcher() {
        return new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                finishScreen();
                onDestroy();
            }
        };
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        SocketDataSource.Factory socketDataSourceFactory = new SocketDataSource.Factory().setContext(mContext);
        LoadControl loadControl = new DefaultLoadControl
                .Builder()
                .setBufferDurationsMs(3000, 3000, 1500, 500)
                .setPrioritizeTimeOverSizeThresholds(false)
                .setTargetBufferBytes(3145728)
                .build();

        player = new ExoPlayer.Builder(mContext)
                .setMediaSourceFactory(new DefaultMediaSourceFactory(mContext).setDataSourceFactory(socketDataSourceFactory))
                .setLoadControl(loadControl)
                .build();
        player.setMediaItem(SocketDataSource.createMediaItem(mContext, device, remoteFile));
        return inflater.inflate(R.layout.fragment_video_stream, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        PlayerView playerView = view.findViewById(R.id.player_view);

        playerView.setPlayer(player);
        player.prepare();
        player.play();
    }
}
