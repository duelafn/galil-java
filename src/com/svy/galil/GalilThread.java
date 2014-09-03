package com.svy.galil;

import java.util.HashMap;
import java.util.LinkedList;

public class GalilThread extends Thread {

    private String address;
    private Galil connection;
    private final LinkedList<IdentifiedString> iqueue = new LinkedList<IdentifiedString>();
    private final HashMap<Integer, ReturnValue> oqueue = new HashMap<Integer, ReturnValue>();
    private Integer ident = 0;

    public GalilThread(String addr) throws GalilException {
        address = addr;
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

    public ReturnValue peek_rv(Integer id) {
        synchronized (oqueue) {
            return oqueue.get(id);
        }
    }

    public ReturnValue wait_rv(Integer id) {
        while (true) {
            synchronized (oqueue) {
                if (oqueue.containsKey(id)) {
                    return oqueue.remove(id);
                }

                try {
                    oqueue.wait();
                } catch (InterruptedException err) {
                    return null;
                }
            }
        }
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


    public String command(String cmd) throws GalilException {
        return wait_string( enqueue( cmd ) );
    }

    public double commandValue(String cmd) throws GalilException {
        return wait_double( enqueue( cmd ) );
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
            throw new RuntimeException("Unable to connect to controller", err);
        }

        while (true) {
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