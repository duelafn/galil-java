package com.svy.galil;
/*
 * Copyright 2014 CMM, Inc.
 *
 * This library is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License version 3.0.
 *
 * This library is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU Lesser
 * General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this library.
 *
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
