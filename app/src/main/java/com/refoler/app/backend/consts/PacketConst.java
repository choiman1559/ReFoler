package com.refoler.app.backend.consts;

public class PacketConst {
    public final static String API_HOST_ADDRESS = "https://cuj1559.ddns.net";
    public final static String API_HOST_ADDRESS_WS = "cuj1559.ddns.net";
    public final static Integer API_HOST_PORT_WS = 443;
    public final static String API_ROUTE_SCHEMA = "/refoler_test/{version}/service={service_type}"; //TODO: Change on release into DNS address

    public final static String STATUS_ERROR = "error";
    public final static String STATUS_OK = "ok";

    public final static String ERROR_NONE = "none";
    public final static String ERROR_NOT_FOUND = "not_found";
    public final static String ERROR_SERVICE_NOT_FOUND = "service_type_not_found";
    public final static String ERROR_SERVICE_NOT_IMPLEMENTED = "service_type_not_implemented";
    public final static String ERROR_INTERNAL_ERROR = "server_internal_error";
    public final static String ERROR_ILLEGAL_ARGUMENT = "server_illegal_argument";
}