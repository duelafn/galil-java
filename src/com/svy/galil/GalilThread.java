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
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.NoSuchElementException;

import com.svy.galil.Galil;
import com.svy.galil.GalilException;

public class GalilThread extends Thread {
    public  boolean die_on_connection_error = false;
    public  int message_cache_size = 4096;

    private StringBuilder msg_builder = new StringBuilder(1024);

    private boolean just_keep_swimming = true;
    private String address;
    private Galil connection = null;
    private final LinkedList<IdentifiedString> iqueue = new LinkedList<IdentifiedString>();
    private final HashMap<Integer, ReturnValue> oqueue = new HashMap<Integer, ReturnValue>();
    private Integer ident = 0;

    public GalilThread(String addr) throws GalilException {
        address = addr;
    }

    public String connection() {
        synchronized (connection) {
            return (connection == null) ? null : connection.connection();
        }
    }
    public String address()    { return address; }

    public void close() {
        just_keep_swimming = false;
    }

    private Integer next_id() {
        // Abandoned identifiers are a possibility due to: improper use,
        // crashed or interrupted threads, laziness, ... Therefore, the
        // identifier set is intentionally small so that we do not leak
        // excessive memory. Further, in case of abandoned identifiers, we
        // make sure we clean the id from the output queue so we do not
        // accidentally return old data.
        Integer id;
        synchronized (ident) {
            ident = (ident + 1) % 9999;
            id = ident;
        }

        synchronized (oqueue) {
            oqueue.remove(id);
        }

        return id;
    }

    private Integer enqueue(IdentifiedString cmd) {
        synchronized (iqueue) {
            iqueue.add(cmd);
            iqueue.notify();
        }
        return cmd.id;
    }

    public Integer enqueue(String cmd) {
        return enqueue(new IdentifiedString(next_id(), false, cmd));
    }

    public List<Integer> enqueue(List<String> cmds) {
        List<Integer> ids = new ArrayList<Integer>();
        for (String cmd : cmds) {
            ids.add(enqueue(new IdentifiedString(next_id(), false, cmd)));
        }
        return ids;
    }

    public static String galil_hex_to_string(String hex) {
        StringBuilder rv = new StringBuilder();
        String clean = hex.replaceAll("[^0-9a-fA-F]", "").replaceAll("(00)+$", "");

        for (int i = 0; i < clean.length(); i+=2) {
            String str = clean.substring(i, i+2);
            rv.append((char) Integer.parseInt(str, 16));
        }

        return rv.toString();
    }

    public ReturnValue peek_rv(Integer id) {
        synchronized (oqueue) {
            return oqueue.get(id);
        }
    }

    public void rv_ok(Integer id) throws GalilException {
        ReturnValue rv = wait_rv(id);
        if (!rv.ok) { throw rv.error; }
    }
    public void rv_ok(List<Integer> ids) throws GalilException {
        for (Integer id : ids) { wait_rv(id); }
    }

    public ReturnValue wait_rv(Integer id) throws GalilException { return wait_rv(id, 5000); }
    public ReturnValue wait_rv(Integer id, int tout_ms) throws GalilException {
        long timeout = System.currentTimeMillis() + tout_ms;
        while (System.currentTimeMillis() < timeout) {
            synchronized (oqueue) {
                if (oqueue.containsKey(id)) {
                    return oqueue.remove(id);
                }

                if (!isAlive()) {
                    // An answer is not coming...
                    throw new GalilException("Not Connected", 9999);// XXX: TODO: Correct code and message
                }

                try {
                    oqueue.wait(50);
                } catch (InterruptedException err) {
                    close();
                    throw new GalilException("wait_rv InterruptedException: " + err.getMessage(), 9999);// XXX: TODO: Correct code and message
                }
            }
        }
        throw new GalilException("wait_rv timeout", 1000);// XXX: TODO: Correct code and message
    }

    public String wait_string(Integer id) throws GalilException {
        ReturnValue rv = wait_rv(id);
        if (rv.ok) { return rv.value; }
        else       { throw rv.error; }
    }

    public List<String> wait_string(List<Integer> ids) throws GalilException {
        List<String> strings = new ArrayList<String>();
        for (Integer id : ids) { strings.add(wait_string(id)); }
        return strings;
    }

    public double wait_double(Integer id) throws GalilException {
        ReturnValue rv = wait_rv(id);
        if (rv.ok) { return Double.parseDouble(rv.value); }
        else       { throw rv.error; }
    }

    public List<Double> wait_listD(Integer id) throws GalilException {
        ReturnValue rv = wait_rv(id);
        if (rv.ok) { return rv.listD; }
        else       { throw rv.error; }
    }

    public double[] wait_list(Integer id) throws GalilException {
        return wait_list(id, -1);
    }
    public double[] wait_list(Integer id, int n) throws GalilException {
        return wait_list(id, n, "( *, *| +)");
    }
    public double[] wait_list(Integer id, int n, String regex) throws GalilException {
        ReturnValue rv = wait_rv(id);
        if (!rv.ok) { throw rv.error; }

        String[] vals = rv.value.split(regex);
        if (n > 0 && vals.length != n) { throw new GalilException("Not enough values", 9999); }// XXX: TODO: Correct code and message

        double[] list = new double[vals.length];
        for (int i = 0; i < vals.length; i++) {
            try {
                list[i] = Double.parseDouble(vals[i]);
            } catch (NumberFormatException err) {
                throw new GalilException(String.format("Parse error at index %d: %s", i, err.getMessage()), 9999);// XXX: TODO: Correct code and message
            }
        }
        return list;
    }

    public String command(String cmd) throws GalilException {
        return wait_string( enqueue( cmd ) );
    }

    /** Note: command(List<String> cmds) is intentionally slow, waits for
     * each commandd to succeed or fail before sending nect command. If you
     * care more for speed than a known controller state, you can use:
     *
     *    List<Integer> ids = galil.enqueue(cmds);
     *    List<String> vals = galil.wait_string(ids);
     */
    public List<String> command(List<String> cmds) throws GalilException {
        List<String> strings = new ArrayList<String>();
        for (String cmd : cmds) {
            strings.add(wait_string(enqueue(new IdentifiedString(next_id(), false, cmd))));
        }
        return strings;
    }


    public String commandString(String cmd) throws GalilException {
        return galil_hex_to_string( wait_string( enqueue( cmd ) ) );
    }

    public double commandValue(String cmd) throws GalilException {
        return wait_double( enqueue( cmd ) );
    }

    public double[] commandListValue(String cmd) throws GalilException {
        return wait_list( enqueue( cmd ) );
    }
    public double[] commandListValue(String cmd, int n) throws GalilException {
        return wait_list( enqueue( cmd ), n);
    }
    public double[] commandListValue(String cmd, int n, String regex) throws GalilException {
        return wait_list( enqueue( cmd ), n, regex);
    }

    public void connect_unsolicited() throws GalilException {
        wait_string(enqueue(new IdentifiedString(next_id(), true, "connect_unsolicited")));
    }

    public String message() {
        String ret = null;
        synchronized (msg_builder) {
            ret = msg_builder.toString();
            msg_builder.setLength(0);
        }
        return ret;
    }

    public Integer programDownload(String program) {
        return enqueue(new IdentifiedString(next_id(), true, "programDownload", program));
    }

    public Integer arrayDownload(List<Double> array, String name) {
        return enqueue(new IdentifiedString(next_id(), true, "arrayDownload", name, array));
    }

    public List<Double> arrayUpload(String name) throws GalilException {
        return wait_listD( enqueue(new IdentifiedString(next_id(), true, "arrayUpload", name)) );
    }

    public static double[] ListToArray(List<Double> list) {
        double array[] = new double[list.size()];
        for (int i = 0; i < array.length; i++) {
            array[i] = list.get(i).doubleValue();
        }
        return array;
    }

    public static List<Double> ArrayToList(double[] array) {
        ArrayList<Double> list = new ArrayList<Double>(array.length);
        for (int i = 0; i < array.length; i++) {
            list.add(array[i]);
        }
        return list;
    }

    @Override
    public void run() {
        try {
            connection = new Galil(address);
        } catch (GalilException err) {
            if (die_on_connection_error) {
                throw new RuntimeException("Unable to connect to controller", err);
            } else {
                return;
            }
        }

        IdentifiedString cmd;
        ReturnValue rv;
        String msg;
        while (just_keep_swimming) {
            cmd = null;
            rv  = null;
            try {

                synchronized (iqueue) {
                    while (just_keep_swimming && iqueue.isEmpty()) {
                        msg = connection.message();
                        if (msg != null && !msg.isEmpty()) {
                            synchronized (msg_builder) {
                                int trim = msg_builder.length() + msg.length() - message_cache_size;
                                if (trim > 128) {
                                    msg_builder.delete(0, trim);
                                }
                                msg_builder.append(msg);
                            }
                        }
                        iqueue.wait(50);
                    }

                    try {
                        cmd = iqueue.remove();
                    } catch (NoSuchElementException err) {
                        if (just_keep_swimming) { continue; } else { break; }
                    }
                }

                try {
                    if (cmd.meta) {
                        if (cmd.value.equals("connect_unsolicited")) {
                            rv = th_connect_unsolicited(cmd);
                        } else if (cmd.value.equals("programDownload")) {
                            rv = th_programDownload(cmd);
                        } else if (cmd.value.equals("arrayDownload")) {
                            rv = th_arrayDownload(cmd);
                        } else if (cmd.value.equals("arrayUpload")) {
                            rv = th_arrayUpload(cmd);
                        }
                    } else {
                        rv = th_process_command(cmd);
                    }
                } catch (GalilException err) {
                    rv = new ReturnValue(cmd, false, err, (String) null);
                    if (err.code >= 5000 && err.code < 6000) {
                        // Socket IO exception, the socket is probably dead
                        close();
                    }
                }

                if (rv != null) {
                    synchronized (oqueue) {
                        oqueue.put(cmd.id, rv);
                        oqueue.notify();
                    }
                }
            }
            catch (InterruptedException err) {
                break;
            }
        }

        synchronized (connection) {
            connection.close();
        }
    }

    private ReturnValue th_process_command(IdentifiedString cmd) throws GalilException {
        return new ReturnValue(cmd, true, null, connection.command(cmd.value));
    }

    private ReturnValue th_connect_unsolicited(IdentifiedString cmd) throws GalilException {
        connection.connect_unsolicited();
        return new ReturnValue(cmd, true, null, "");
    }

    private ReturnValue th_programDownload(IdentifiedString cmd) throws GalilException {
        int timeout_ms = connection.getTimeout();
        connection.setTimeout(5000);
        try {
            connection.programDownload(cmd.str);
        } finally {
            connection.setTimeout(timeout_ms);
        }
        return new ReturnValue(cmd, true, null, "");
    }

    private ReturnValue th_arrayDownload(IdentifiedString cmd) throws GalilException {
        connection.arrayDownload(cmd.listD, cmd.str);
        return new ReturnValue(cmd, true, null, "");
    }

    private ReturnValue th_arrayUpload(IdentifiedString cmd) throws GalilException {
        return new ReturnValue(cmd, true, null, connection.arrayUpload(cmd.str));
    }

    public static class IdentifiedString {
        public final Integer id;
        public final boolean meta;
        public final String value;
        public final String str;
        public final List<Double> listD;
        public IdentifiedString(Integer id, boolean meta, String value) {
            this.id = id;
            this.meta = meta;
            this.value = value;
            this.str = null;
            this.listD = null;
        }
        public IdentifiedString(Integer id, boolean meta, String value, String str) {
            this.id = id;
            this.meta = meta;
            this.value = value;
            this.str = str;
            this.listD = null;
        }
        public IdentifiedString(Integer id, boolean meta, String value, String str, List<Double> listD) {
            this.id = id;
            this.meta = meta;
            this.value = value;
            this.str = str;
            this.listD = listD;
        }

        public String toString() {
            if (!meta) { return value; }
            return "<<" + value + ">>";
        }
    }

    public static class ReturnValue {
        public final IdentifiedString cmd;
        public final boolean ok;
        public final GalilException error;
        public final String value;
        public final List<Double> listD;
        public ReturnValue(IdentifiedString cmd, boolean ok, GalilException error, String value) {
            this.cmd = cmd;
            this.ok = ok;
            this.error = error;
            this.value = value;
            this.listD = null;
        }
        public ReturnValue(IdentifiedString cmd, boolean ok, GalilException error, List<Double> listD) {
            this.cmd = cmd;
            this.ok = ok;
            this.error = error;
            this.value = null;
            this.listD = listD;
        }
    }

}
