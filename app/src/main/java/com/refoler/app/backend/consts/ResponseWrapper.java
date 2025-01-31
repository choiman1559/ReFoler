package com.refoler.app.backend.consts;

import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.util.JsonFormat;
import com.refoler.Refoler;

import java.io.IOException;
import io.ktor.http.HttpStatusCode;

public class ResponseWrapper {
    private HttpStatusCode statusCode;
    private Refoler.ResponsePacket refolerPacket;

    public void setStatusCode(HttpStatusCode statusCode) {
        this.statusCode = statusCode;
    }

    public void setRefolerPacket(Refoler.ResponsePacket refolerPacket) {
        this.refolerPacket = refolerPacket;
    }

    public HttpStatusCode getStatusCode() {
        return statusCode;
    }

    public Refoler.ResponsePacket getRefolerPacket() {
        return refolerPacket;
    }

    public String getSerializedData() throws InvalidProtocolBufferException {
        return JsonFormat.printer().print(getRefolerPacket());
    }

    public static Refoler.ResponsePacket parseResponsePacket(String rawData) throws IOException {
        Refoler.ResponsePacket.Builder responsePacketBuilder = Refoler.ResponsePacket.newBuilder();
        JsonFormat.parser().merge(rawData, responsePacketBuilder);
        return responsePacketBuilder.build();
    }
}

