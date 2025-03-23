package com.refoler.app.process.actions.impl.socket;

import android.content.Context;
import android.util.Log;

import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.backend.consts.DirectActionConst;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.process.actions.ActionOp;
import com.refoler.app.process.actions.DeAsyncJob;
import com.refoler.app.process.actions.FSEditSyncJob;
import com.refoler.app.process.actions.FileActionWorker;
import com.refoler.app.process.actions.impl.misc.DownloadAction;
import com.refoler.app.process.actions.impl.misc.UploadAction;

import org.jetbrains.annotations.TestOnly;

import java.io.File;
import java.io.FileOutputStream;
import java.util.concurrent.atomic.AtomicLong;

@TestOnly
public class SocketDownloadAction implements ActionOp {

    public static final int BUFFER_SIZE = 1048576; // 1024KB Buffer

    /** @noinspection ResultOfMethodCallIgnored*/
    @Override
    public void performActionOp(Context context, Refoler.Device requester, FileAction.ActionRequest actionRequest, FileActionWorker.ActionCallback callback) throws Exception {
        FileAction.ActionResponse.Builder actionResponse = FileAction.ActionResponse.newBuilder();
        actionResponse.setOverallStatus(PacketConst.STATUS_OK);
        FSEditSyncJob editTask = FSEditSyncJob.getInstance();

        for(String targetFilePath : actionRequest.getTargetFilesList()) {
            final File destFile = new File(DownloadAction.getFileDownloadDir(), UploadAction.getNameFromPath(targetFilePath));
            FileAction.ActionResult.Builder result = FileAction.ActionResult.newBuilder();
            result.setOpPaths(targetFilePath);

            if (destFile.exists()) {
                if (actionRequest.hasOverrideExists() && actionRequest.getOverrideExists()) {
                    destFile.delete();
                    destFile.createNewFile();
                } else {
                    result.setResultSuccess(false);
                    result.setErrorCause("File already exists");
                    actionResponse.addResult(result);
                    return;
                }
            } else {
                destFile.createNewFile();
            }

            try(SocketRandAccess socketRandAccess = new SocketRandAccess(requester, targetFilePath, true, false);
                FileOutputStream fileOutputStream = new FileOutputStream(destFile)) {

                socketRandAccess.setSynchronized(true);
                socketRandAccess.requestChannel(context);
                socketRandAccess.seek(0);

                final long totalBytes = socketRandAccess.getFileLength();
                AtomicLong remainingBytes = new AtomicLong(totalBytes);

                new DeAsyncJob<>((job) -> {
                    socketRandAccess.setBufferReceivedListener((resultData, bufferData) -> {
                        try {
                            if(resultData <= 0) {
                                job.setResult(new Object());
                            }
                            fileOutputStream.write(bufferData);
                            remainingBytes.addAndGet(-resultData);
                            Log.d("ddd", "remainingBytes: " + remainingBytes.get() + " readBytes: " + resultData);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
                    socketRandAccess.readBytes(BUFFER_SIZE, 0, (int) totalBytes, true);
                }).runAndWait();

                editTask.addQuery(destFile.getPath());
                result.setResultSuccess(true);
                actionResponse.addResult(result);
            } catch (Exception e) {
                e.printStackTrace();
                result.setResultSuccess(false);
                result.setErrorCause(e.toString());
                actionResponse.addResult(result);
            }
        }

        editTask.addCallBack(new FSEditSyncJob.TaskResultCallBack() {
            @Override
            public void onEditSuccess(boolean needsQueryEntireList) {
                if(needsQueryEntireList) {
                    callback.onFinish(actionResponse);
                }
            }

            @Override
            public void onUploadSuccess() {
                callback.onFinish(actionResponse);
            }

            @Override
            public void onUploadFailed(Throwable throwable) {
                actionResponse.setOverallStatus(DirectActionConst.getErrorCode(DirectActionConst.RESULT_ERROR_EXCEPTION, throwable.toString()));
                callback.onFinish(actionResponse);
            }
        });
        editTask.execute(context);
    }

    @Override
    public FileAction.ActionType getActionOpcode() {
        return FileAction.ActionType.OP_DOWNLOAD;
    }

    @Override
    public boolean mergeQueryScopeIfAvailable() {
        return false;
    }
}
