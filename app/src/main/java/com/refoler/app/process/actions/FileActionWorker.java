package com.refoler.app.process.actions;

import android.accounts.NetworkErrorException;
import android.content.Context;

import androidx.annotation.Nullable;

import com.refoler.FileAction;
import com.refoler.Refoler;
import com.refoler.app.backend.DeviceWrapper;
import com.refoler.app.backend.ResponseWrapper;
import com.refoler.app.backend.consts.DirectActionConst;
import com.refoler.app.backend.consts.PacketConst;
import com.refoler.app.backend.consts.RecordConst;
import com.refoler.app.process.actions.impl.CopyAction;
import com.refoler.app.process.actions.impl.CutAction;
import com.refoler.app.process.actions.impl.DeleteAction;
import com.refoler.app.process.actions.impl.MkDirAction;
import com.refoler.app.process.actions.impl.NewFileAction;
import com.refoler.app.process.actions.impl.RenameAction;
import com.refoler.app.process.actions.impl.misc.DownloadAction;
import com.refoler.app.process.actions.impl.misc.HashAction;
import com.refoler.app.process.actions.impl.misc.NopAction;
import com.refoler.app.process.actions.impl.socket.RandAccessAction;
import com.refoler.app.process.actions.impl.misc.UploadAction;
import com.refoler.app.process.db.ReFileConst;
import com.refoler.app.process.service.FirebaseMessageService;
import com.refoler.app.utils.JsonRequest;

import org.json.JSONArray;
import org.json.JSONException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;

public class FileActionWorker {

    private static FileActionWorker instance;
    private final HashMap<FileAction.ActionType, ActionOp> opClasses = new HashMap<>();
    private final ConcurrentHashMap<FileAction.ActionRequest, Thread> actionSchedules = new ConcurrentHashMap<>();
    private ActionRequestNotifier actionRequestNotifier;

    public interface ActionCallback {
        void onFinish(FileAction.ActionResponse.Builder response);
    }

    public interface ActionRequestNotifier {
        boolean onRequestRaise(ActionOp requestedOp, Refoler.Device requester, FileAction.ActionRequest request);

        void onRequestTerminated(Refoler.Device requester, FileAction.ActionRequest request, FileAction.ActionResponse.Builder response);
    }

    public static FileActionWorker getInstance() {
        return getInstance(false);
    }

    public static FileActionWorker getInstance(boolean initialize) {
        if(initialize) {
            instance = null;
        }

        if (instance == null) {
            instance = new FileActionWorker();

            // Basic OP implementations
            instance.addActionOp(NopAction.class);
            instance.addActionOp(HashAction.class);
            instance.addActionOp(UploadAction.class);
            instance.addActionOp(DownloadAction.class);
            instance.addActionOp(RandAccessAction.class);

            // File action implementations
            instance.addActionOp(CopyAction.class);
            instance.addActionOp(CutAction.class);
            instance.addActionOp(DeleteAction.class);
            instance.addActionOp(MkDirAction.class);
            instance.addActionOp(NewFileAction.class);
            instance.addActionOp(RenameAction.class);
        }
        return instance;
    }

    public boolean isActionRunning(FileAction.ActionRequest request) {
        return actionSchedules.containsKey(request) && Objects.requireNonNull(actionSchedules.get(request)).isAlive();
    }

    public void cancelAction(FileAction.ActionRequest request) {
        Thread thread = actionSchedules.remove(request);
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void addActionOp(Class<?> actionOpClass) {
        try {
            Object opInstance = actionOpClass.newInstance();
            if (opInstance instanceof ActionOp actionOp) {
                opClasses.put(actionOp.getActionOpcode(), actionOp);
            }
        } catch (IllegalAccessException | InstantiationException e) {
            throw new RuntimeException(e);
        }
    }

    public void setActionRequestNotifier(ActionRequestNotifier actionRequestNotifier) {
        this.actionRequestNotifier = actionRequestNotifier;
    }

    @Nullable
    private ActionOp fetchActionOp(FileAction.ActionRequest actionRequest) {
        return opClasses.getOrDefault(actionRequest.getActionType(), null);
    }

    public void handleActionFromRemote(Context context, Refoler.Device requester, FileAction.ActionRequest requestPacket) {
        ActionOp actionOp = fetchActionOp(requestPacket);
        if (actionOp == null) {
            responseResultError(context, requester, requestPacket, DirectActionConst.RESULT_ERROR_NOT_IMPLEMENTED);
            return;
        }

        if (requestPacket.hasQueryScope() && actionOp.mergeQueryScopeIfAvailable()) {
            Refoler.RequestPacket.Builder request = Refoler.RequestPacket.newBuilder();
            request.setActionName(RecordConst.SERVICE_ACTION_TYPE_GET);
            request.setFileQuery(requestPacket.getQueryScope());
            request.addDevice(DeviceWrapper.getSelfDeviceInfo(context));
            JsonRequest.postRequestPacket(context, RecordConst.SERVICE_TYPE_FILE_SEARCH, request,
                    (queryResult -> handleActionWithQuery(actionOp, queryResult, context, requester, requestPacket)));
        } else {
            emitActionFromRemote(actionOp, context, requester, requestPacket);
        }
    }

    private void handleActionWithQuery(ActionOp actionOp, ResponseWrapper responseWrapper, Context context, Refoler.Device requester, FileAction.ActionRequest requestPacket) {
        try {
            if (responseWrapper.gotOk()) {
                JSONArray array = new JSONArray(responseWrapper.getRefolerPacket().getExtraData(0));
                FileAction.ActionRequest.Builder newRequest = FileAction.ActionRequest.newBuilder(requestPacket);
                for (int i = 0; i < array.length(); i++) {
                    newRequest.addTargetFiles(array.getJSONObject(i).getString(ReFileConst.DATA_TYPE_PATH));
                }
                emitActionFromRemote(actionOp, context, requester, requestPacket);
            } else throw new NetworkErrorException(responseWrapper.getRefolerPacket().getStatus());
        } catch (Exception e) {
            responseResultError(context, requester, requestPacket, DirectActionConst.getErrorCode(DirectActionConst.RESULT_ERROR_EXCEPTION, e.toString()));
        }
    }

    private void emitActionFromRemote(ActionOp actionOp, Context context, Refoler.Device requester, FileAction.ActionRequest requestPacket) {
        if (actionRequestNotifier == null || actionRequestNotifier.onRequestRaise(actionOp, requester, requestPacket)) {
            Thread thread = new Thread(() -> {
                try {
                    actionOp.performActionOp(context, requester, requestPacket, response -> responseResult(context, requester, requestPacket, response));
                } catch (Exception e) {
                    responseResultError(context, requester, requestPacket, DirectActionConst.getErrorCode(DirectActionConst.RESULT_ERROR_EXCEPTION, e.toString()));
                }
            });

            actionSchedules.put(requestPacket, thread);
            thread.start();
        } else {
            responseResultError(context, requester, requestPacket, DirectActionConst.RESULT_ERROR_DENIED_BY_RESPONSER);
        }
    }

    public void responseResultError(Context context, Refoler.Device device, FileAction.ActionRequest request, String errorMessage) {
        FileAction.ActionResponse.Builder response = FileAction.ActionResponse.newBuilder();
        response.setOverallStatus(errorMessage);
        responseResult(context, device, request, response);
    }

    public void responseResult(Context context, Refoler.Device device, FileAction.ActionRequest request, FileAction.ActionResponse.Builder response) {
        Refoler.ResponsePacket.Builder responseBuilder = Refoler.ResponsePacket.newBuilder();
        responseBuilder.addDevice(DeviceWrapper.getSelfDeviceInfo(context));
        responseBuilder.addDevice(device);
        responseBuilder.setStatus(PacketConst.STATUS_OK);

        response.setChallengeCode(request.getChallengeCode());
        responseBuilder.setFileAction(response);

        try {
            actionSchedules.remove(request);
            FirebaseMessageService.postResponseMessage(context, DirectActionConst.SERVICE_TYPE_FILE_ACTION, responseBuilder.build());
            if(actionRequestNotifier != null) {
                actionRequestNotifier.onRequestTerminated(device, request, response);
            }
        } catch (JSONException | IOException e) {
            e.printStackTrace();
        }
    }
}
