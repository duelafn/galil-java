package com.svy.galil;

import java.util.HashMap;
import java.util.LinkedList;

public class GalilThread extends Thread {
    public  boolean die_on_connection_error = false;

    private boolean just_keep_swimming = true;
    private String address;
    private Galil connection = null;
    private final LinkedList<IdentifiedString> iqueue = new LinkedList<IdentifiedString>();
    private final HashMap<Integer, ReturnValue> oqueue = new HashMap<Integer, ReturnValue>();
    private Integer ident = 0;

    public GalilThread(String addr) throws GalilException {
        address = addr;
    }

    public String connection() { return (connection == null) ? null : connection.connection(); }
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

    public String galil_hex_to_string(String hex) {
        StringBuilder rv = new StringBuilder();
        String clean = hex.replaceAll("[^0-9a-fA-F]", "").replaceAll("(00)+$", "");

        for (int i = 0; i < clean.length(); i+=2) {
            String str = clean.substring(i, i+2);
            rv.append((char) Integer.parseInt(str, 16));
        }

        return rv.toString();
    }

    public Integer enqueue(String cmd) {
        return enqueue(new IdentifiedString(next_id(), false, cmd));
    }

    public ReturnValue peek_rv(Integer id) {
        synchronized (oqueue) {
            return oqueue.get(id);
        }
    }

    public ReturnValue wait_rv(Integer id) throws GalilException {
        long timeout = System.currentTimeMillis() + 5000;
        while (System.currentTimeMillis() < timeout) {
            synchronized (oqueue) {
                if (oqueue.containsKey(id)) {
                    return oqueue.remove(id);
                }

                try {
                    oqueue.wait(100);
                } catch (InterruptedException err) {
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

    public double wait_double(Integer id) throws GalilException {
        ReturnValue rv = wait_rv(id);
        if (rv.ok) { return Double.parseDouble(rv.value); }
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

    public String message() throws GalilException {
        return wait_string(enqueue(new IdentifiedString(next_id(), true, "message")));
    }

    public Integer programDownload(String program) {
        String args[] = { program };
        return enqueue(new IdentifiedString(next_id(), true, "programDownload", args));
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

        while (just_keep_swimming) {
            try {
                IdentifiedString cmd;
                ReturnValue rv = null;

                synchronized (iqueue) {
                    while (iqueue.isEmpty()) {
                        iqueue.wait();
                    }

                    cmd = iqueue.remove();
                }

                try {
                    if (cmd.meta) {
                        if (cmd.value.equals("connect_unsolicited")) {
                            rv = th_connect_unsolicited(cmd);
                        } else if (cmd.value.equals("message")) {
                            rv = th_message(cmd);
                        } else if (cmd.value.equals("programDownload")) {
                            rv = th_programDownload(cmd);
                        }
                    } else {
                        rv = th_process_command(cmd);
                    }
                } catch (GalilException err) {
                    rv = new ReturnValue(false, err, null);
                }

                if (rv != null) {
                    synchronized (oqueue) {
                        oqueue.put(cmd.id, rv);
                        oqueue.notify();
                    }
                }
            }
            catch ( InterruptedException ie ) {
                break;
            }
        }

        connection.close();
    }

    private ReturnValue th_process_command(IdentifiedString cmd) throws GalilException {
        return new ReturnValue(true, null, connection.command(cmd.value));
    }

    private ReturnValue th_connect_unsolicited(IdentifiedString cmd) throws GalilException {
        connection.connect_unsolicited();
        return new ReturnValue(true, null, "");
    }

    private ReturnValue th_message(IdentifiedString cmd) throws GalilException {
        return new ReturnValue(true, null, connection.message());
    }

    private ReturnValue th_programDownload(IdentifiedString cmd) throws GalilException {
        connection.programDownload(cmd.args[0]);
        return null;
    }

    public class IdentifiedString {
        public final Integer id;
        public final boolean meta;
        public final String value;
        public final String[] args;
        public IdentifiedString(Integer id, boolean meta, String value) {
            this.id = id;
            this.meta = meta;
            this.value = value;
            this.args = null;
        }
        public IdentifiedString(Integer id, boolean meta, String value, String[] args) {
            this.id = id;
            this.meta = meta;
            this.value = value;
            this.args = args;
        }
    }

    public class ReturnValue {
        public final boolean ok;
        public final GalilException error;
        public final String value;
        public ReturnValue(boolean ok, GalilException error, String value) {
            this.ok = ok;
            this.error = error;
            this.value = value;
        }
    }

}
