package com.svy.galil;
/*
 * Copyright 2014 CMM, Inc.
 */

import java.util.ArrayList;
import java.util.List;

public class GalilCommands extends ArrayList<String> {
    public GalilCommands() { super(); }
    public GalilCommands(int size) { super(size); }

    public List<String> pack()          { return pack(80); }
    public List<String> pack(int width) {
        List<String> packed = new ArrayList<String>();
        StringBuilder next  = new StringBuilder(width);

        for (String cmd : this) {
            if (next.length() == 0) {
                next.append(cmd);
            } else if (next.length() + cmd.length() + 1 < width) {
                next.append(';').append(cmd);
            } else {
                packed.add(next.toString());
                next.setLength(0);
                next.append(cmd);
            }
        }

        if (next.length() > 0) {
            packed.add(next.toString());
        }

        return packed;
    }
}
