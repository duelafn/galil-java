package com.svy.galil;

import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.lang.Character;
import java.lang.StringBuffer;
import java.lang.StringBuilder;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.net.UnknownHostException;
import java.net.SocketTimeoutException;
import java.net.SocketException;

import com.svy.galil.GalilException;


interface MessageListener {
    public void onMessage(String message);
}


public class Galil {
    private static final String VERSION = "1.0.0 May 2014 Galil.java";
    private static final byte   UNSOLICITED_BIT = (byte) 0x80;
    private static final int    READ_BUF_SIZE = 80;

    public int timeout_ms = 500;
    public int linger_s = 1;

    protected List<MessageListener> listeners = new ArrayList<MessageListener>();

    protected Socket socket;
    protected PrintWriter writer;
    protected InputStream reader;

    protected String _connection = new String();
    protected StringBuffer sol_msg = new StringBuffer(256);
    protected StringBuffer unsol_msg = new StringBuffer(256);


    public Galil(String address) throws GalilException {
        try {
            socket = new Socket(address, 7777);
            socket.setSoLinger(true, linger_s);
            socket.setSoTimeout(timeout_ms);

            writer = new PrintWriter(new BufferedOutputStream(socket.getOutputStream()), true) {
                @Override
                public void println(String x) { print(x + "\r"); }
            };
            reader = socket.getInputStream();

            _connection = String.format("%s, %s", address, command("\022\026"));

        } catch (UnknownHostException err) {
            throw new GalilException(err.toString(), 5004);
        } catch (SocketTimeoutException err) {
            throw new GalilException(err.toString(), 1010);
        } catch (SocketException err) {
            throw new GalilException(err.toString(), 5005);
        } catch (IOException err) {
            throw new GalilException(err.toString(), 5006);
        }
    }

//     vector<string> addresses()

    public String connection() {
        return _connection;
    }

    protected void read_to_buffers(int local_timeout) throws GalilException {
        try {
            socket.setSoTimeout(local_timeout);
            read_to_buffers();
        } catch (SocketException err) {
            throw new GalilException(err.toString(), 9011);
        }
        finally {
            try {
                socket.setSoTimeout(timeout_ms);
            } catch (SocketException err) {
                throw new GalilException(err.toString(), 9012);// XXX: TODO: Check code
            }
        }
    }

    protected void read_to_buffers() throws GalilException {
        byte buff[] = new byte[READ_BUF_SIZE];
        StringBuilder sol   = new StringBuilder(READ_BUF_SIZE);
        StringBuilder unsol = new StringBuilder(READ_BUF_SIZE);
        int len;

        try {
            len = reader.read(buff, 0, buff.length);
        } catch (SocketTimeoutException err) {
            throw new GalilException(err.toString(), 1011);
        } catch (IOException err) {
            throw new GalilException(err.toString(), 9011);// XXX: TODO: Check code
        }

        for (int i=0; i < len; i++) {
            if ((buff[i] & UNSOLICITED_BIT) != 0) {
                sol.append(buff[i] ^ UNSOLICITED_BIT);
            } else {
                unsol.append(buff[i]);
            }
        }

        if (sol.length() > 0)   { sol_msg.append(sol); }
        if (unsol.length() > 0) { unsol_msg.append(unsol); }

        if (unsol_msg.length() > 0 && listeners.size() > 0) {
            String msg = unsol_msg.toString();
            unsol_msg.delete(0, unsol_msg.length());

            for (MessageListener ml : listeners) {
                ml.onMessage(msg);
            }
        }
    }

    protected String read_result() throws GalilException { return read_result(':'); }
    protected String read_result(char ack) throws GalilException {
        char ch;
        for (int i = 0; i < sol_msg.length(); i++) {
            ch = sol_msg.charAt(i);

            if (ch == ack) {
                String rv = sol_msg.substring(0, i);
                sol_msg.delete(0, i+1);
                return rv;
            }

            if (ch == '?') {
                String rv = sol_msg.substring(0, i);
                sol_msg.delete(0, i+1);
                throw new GalilException("got ? instead of : response... "+rv, 2010);
            }
        }

        return null;
    }

    public String command()                                                      throws GalilException
        { return command("MG TIME", "\r", ':', true); }
    public String command(String cmd)                                            throws GalilException
        { return command(cmd, "\r", ':', true); }
    public String command(String cmd, String terminator)                         throws GalilException
        { return command(cmd, terminator, ':', true); }
    public String command(String cmd, String terminator, char ack)               throws GalilException
        { return command(cmd, terminator, ack, true); }
    public String command(String cmd, String terminator, char ack, boolean trim) throws GalilException {
        writer.print( cmd + terminator );
        long timeout = new Date().getTime() + timeout_ms;
        int idx;
        while ((idx = sol_msg.indexOf(Character.toString(ack))) < 0) {
            if (timeout < new Date().getTime()) {
                throw new GalilException("Timeout Error", 1021);// XXX: TODO: Get correct wording
            }
            read_to_buffers();
        }

        String rv = read_result(ack);
        return trim ? rv.trim() : rv + ack;
    }

    public double commandValue(String cmd) throws GalilException {
        return Double.parseDouble(command(cmd, "\r", ':', true));
    }

    public void addMessageListener(MessageListener listener) {
        listeners.add(listener);
    }

    public String message() throws GalilException { return message(500); }
    public String message(int timeout) throws GalilException {
        // Never any messages when we have event listeners, just return an
        // empty string.
        if (listeners.size() > 0) {
            return "";
        }

        if (0 == unsol_msg.length()) {
            read_to_buffers(timeout);
        }

        String rv = unsol_msg.toString();
        unsol_msg.delete(0, unsol_msg.length());
        return rv;
    }

    public void programDownload() throws GalilException { programDownload("MG TIME\rEN"); }
    public void programDownload(String program) throws GalilException {
        command(String.format("DL\r%s", program), "\\");
    }

//     void programDownloadFile(string file = "program.dmc")
//     string programUpload()
//     void programUploadFile(string file = "program.dmc")

    protected String arrayJoin(List<Double> array) {
        StringBuilder rv = new StringBuilder();
        for (int i = 0; i < array.size(); i++) {
            if (i > 0) { rv.append(','); }
            rv.append(array.get(i));
        }
        return rv.toString();
    }

    public void arrayDownload(List<Double> array) throws GalilException { arrayDownload(array, "array"); }
    public void arrayDownload(List<Double> array, String name) throws GalilException {
        command(String.format("DM %s[%d]", name, array.size()));
        command(String.format("QD %s[]\r%s", name, arrayJoin(array)), "\\");
    }

    public List<Double> arrayUpload() throws GalilException { return arrayUpload("array"); }
    public List<Double> arrayUpload(String name) throws GalilException {
        String str = command(String.format("QU %s[]", name));
        ArrayList<Double> rv = new ArrayList<Double>();
        for (String val : str.split("\r")) {
            rv.add(Double.parseDouble(val));
        }
        return rv;
    }

//     void arrayDownloadFile(string file = "arrays.csv")
//     void arrayUploadFile(string file, string names = "")

//     void firmwareDownloadFile(string file = "firmware.hex")

    public String read() throws GalilException {
        byte buff[] = new byte[READ_BUF_SIZE];
        int len;
        try {
            len = reader.read(buff, 0, buff.length);
        } catch (SocketTimeoutException err) {
            throw new GalilException(err.toString(), 1011);// XXX: TODO: Check code
        } catch (IOException err) {
            throw new GalilException(err.toString(), 9211);
        }

        return Arrays.toString(Arrays.copyOfRange(buff, 0, len));
    }

    public void write() throws GalilException { write("\r"); }
    public void write(String bytes) throws GalilException {
        writer.print(bytes);
    }

//     int interrupt(int timeout_ms = 500)    onInterrupt Event
    public static final String libraryVersion() { return VERSION; }

//     vector<string> sources()
//     void recordsStart(double period_ms = -1)
//     vector<char>record(string method = "QR")   onRecord Event
//     double sourceValue(vector<char> record, string source = "TIME")
//     string source( string field = "Description", string source = "TIME")
//     void setSource( string field = "Description", string source="TIME", string to= "Sample counter")
}
