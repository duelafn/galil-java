package com.svy.galil;
/*
 * Copyright 2014 CMM, Inc.
 */


public class GalilException extends Exception {
    public String result;
    public int code = 0;

    private static final String[] ERROR_CATEGORY = {
        "UNCLASSIFIED"
    ,   "TIMEOUT"
    ,   "COMMAND"
    ,   "MONITOR"
    ,   "FILE"
    ,   "OPEN"
    ,   "WRONG BUS"
    ,   "INVALID CHARACTER"
    ,   "WARNING"
    ,   "OFFLINE"
    };

    public GalilException(String message) {
        super("0000 " + ERROR_CATEGORY[0] + " ERROR.  " + message);
    }
    public GalilException(String message, int error_code) {
        super(Integer.toString(error_code) + " " + ERROR_CATEGORY[(int) (error_code/1000)] + " ERROR.  " + message);
        code = error_code;
    }
    public GalilException(String message, int error_code, String res) {
        super(message);
        code = error_code;
        result = res;
    }
}
