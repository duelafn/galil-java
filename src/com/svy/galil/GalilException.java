package com.svy.galil;


public class GalilException extends Exception {
    public String result;
    public int code = 0;

    private static final String[] ERROR_CATEGORY = {
        ""
    ,   "TIMEOUT"
    ,   "COMMAND"
    ,   "MONITOR"
    ,   "FILE"
    ,   "OPEN"
    ,   "WRONG BUS"
    ,   "INVALID"
    ,   "WARNING"
    ,   "OFFLINE"
    };

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
