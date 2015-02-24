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
