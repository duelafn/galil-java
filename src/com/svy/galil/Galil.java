package com.svy.galil;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;


public class Galil {
    private static final String VERSION = "1.0.0 Sep 2014 Galil.java";
    private static final byte   UNSOLICITED_BIT = (byte) 0x80;
    private static final int    READ_BUF_SIZE = 80;
    private static final String TERMINATOR = "\015";

    protected int timeout_ms = 500;

    protected String address;
    protected Socket socket;
    protected Socket unsolicited;

    protected String _connection = new String();
    protected StringBuilder sol_msg = new StringBuilder(80);
    protected StringBuilder unsol_msg = new StringBuilder(80);

    private String find_handle(Socket sock) throws GalilException {
        String[] handles = command("TH").split("[\r\n]+");

        // IHA TCP PORT 7777 TO IP ADDRESS 10,10,10,1 PORT 50264
        String port = "PORT " + sock.getLocalPort();
        boolean found = false;
        for (int i = 0; i < handles.length; i++) {
            if (handles[i].matches("^IH[A-Z] .*? " + port + "$")) {
                return handles[i].substring(2, 3);
            }
        }

        return null;
    }

    public void connect_unsolicited() throws GalilException {
        try {
            unsolicited = new Socket(address, 7777);
            unsolicited.setSoTimeout(timeout_ms);
        } catch (UnknownHostException err) {
            throw new GalilException(err.toString());
        } catch (SocketTimeoutException err) {
            throw new GalilException(err.toString());
        } catch (SocketException err) {
            throw new GalilException(err.toString());
        } catch (IOException err) {
            throw new GalilException(err.toString());
        }

        String handle = find_handle(unsolicited);
        if (null == handle) {
            throw new GalilException("Unable to find handle for connected socket");
        }
        command("CF" + handle);
    }


    public Galil(String addr) throws GalilException {
        try {
            socket = new Socket(addr, 7777);
            socket.setSoTimeout(timeout_ms);

            address = addr;
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

    public void setTimeout(int timeout) throws GalilException {
        timeout_ms = timeout;
        try {
            socket.setSoTimeout(timeout_ms);
            if (null != unsolicited) {
                unsolicited.setSoTimeout(timeout_ms);
            }
        } catch (SocketException err) {
            throw new GalilException(err.toString(), 9999);
        }
    }
    public int getTimeout() {
        return timeout_ms;
    }

    public void show_buffers() { show_buffers(""); }
    public void show_buffers(String str) {
        if (sol_msg.length() > 0) {
            System.err.print("# " + str + " ");
            System.err.println("Solicited Buffer: " + sol_msg.toString());
        }
        if (unsol_msg.length() > 0) {
            System.err.print("# " + str + " ");
            System.err.println("Unsolicited Buffer: " + unsol_msg.toString());
        }
        if (sol_msg.length() == 0 && unsol_msg.length() == 0) {
            System.err.print("# " + str + " ");
            System.err.println("Buffers Empty");
        }
    }

    protected void read_to_buffers() throws GalilException {
        read_to_buffers(socket);
    }
    protected void read_to_buffers(int local_timeout) throws GalilException {
        read_to_buffers(socket, local_timeout);
    }

    protected void read_to_buffers(Socket sock, int local_timeout) throws GalilException {
        try {
            sock.setSoTimeout(local_timeout);
            read_to_buffers(sock);
        } catch (SocketException err) {
            throw new GalilException(err.toString(), 9011);
        }
        finally {
            try {
                sock.setSoTimeout(timeout_ms);
            } catch (SocketException err) {
                throw new GalilException(err.toString(), 9012);// XXX: TODO: Check code
            }
        }
    }

    protected void read_to_buffers(Socket sock) throws GalilException {
        byte buff[] = new byte[READ_BUF_SIZE];
        ByteArrayOutputStream sol   = new ByteArrayOutputStream();
        ByteArrayOutputStream unsol = new ByteArrayOutputStream();

        int len = 0;

        try {
            len = sock.getInputStream().read(buff, 0, buff.length);
        } catch (SocketTimeoutException err) {
            throw new GalilException(err.toString(), 1011);// Note: error message differs
        } catch (IOException err) {
            throw new GalilException(err.toString(), 9011);// XXX: TODO: Check code
        }

        for (int i=0; i < len; i++) {
            if ((buff[i] & UNSOLICITED_BIT) != 0) {
                unsol.write(buff[i] ^ UNSOLICITED_BIT);
            } else {
                sol.write(buff[i]);
            }
        }

        try {
            sol_msg.append(sol.toString("ISO-8859-1"));
            unsol_msg.append(unsol.toString("ISO-8859-1"));
        } catch (UnsupportedEncodingException err) {
            throw new GalilException(err.toString(), 5006);// XXX: TODO: Correct code and message, but do not expect to happen, all bytes valid!
        }
    }

    // Extract result up to the `count`th occurrence of `ack`.
    protected String read_result(char ack, int count) throws GalilException {
        char ch;
        for (int i = 0; i < sol_msg.length(); i++) {
            ch = sol_msg.charAt(i);

            if (ch == ack) {
                count--;
                if (count <= 0) {
                    String rv = sol_msg.substring(0, i);
                    sol_msg.delete(0, i+1);
                    return rv;
                }
            }

            if (ch == '?') {
                String rv = sol_msg.substring(0, i);
                sol_msg.delete(0, i+1);
                throw new GalilException("got ? instead of : response... '"+rv+"'", 2010);
            }
        }

        return null;
    }

    // Count apparent number of commands (Namely, number of ":"'s we expect
    // in the result)
    public int count_commands(String cmds) {
        return cmds.split("[:;]",-1).length;
    }

    public String command()                                                      throws GalilException
        { return command("MG TIME", TERMINATOR, ':', true); }
    public String command(String cmd)                                            throws GalilException
        { return command(cmd, TERMINATOR, ':', true); }
    public String command(String cmd, String terminator)                         throws GalilException
        { return command(cmd, terminator, ':', true); }
    public String command(String cmd, String terminator, char ack)               throws GalilException
        { return command(cmd, terminator, ack, true); }
    public String command(String cmd, String terminator, char ack, boolean trim) throws GalilException {
        // Clear out any unused solicited messages so that we can provide
        // eventual consistency even in the presence of crazy output
        // oddities (e.g., command("x=2;WT5000;y=5"))
        sol_msg.setLength(0);

        try {
            socket.getOutputStream().write( (cmd + terminator).getBytes("ISO-8859-1") );
        } catch (UnsupportedEncodingException err) {
            throw new GalilException(err.toString(), 5006);// XXX: TODO: Correct code and message
        } catch (IOException err) {
            throw new GalilException(err.toString(), 5006);// XXX: TODO: Correct code and message
        }

        long timeout = new Date().getTime() + timeout_ms;
        String rv;
        int nr_commands = count_commands(cmd);
        while (null == (rv = read_result(ack, nr_commands))) {
            if (timeout < new Date().getTime()) {
                throw new GalilException("Timeout Error", 1021);// XXX: TODO: Get correct wording
            }
            read_to_buffers();
        }

        return trim ? rv.trim() : rv + ack;
    }

    public double commandValue(String cmd) throws GalilException {
        return Double.parseDouble(command(cmd, TERMINATOR, ':', true));
    }

    public String message() { return message(2); }
    public String message(int timeout) {
        Socket sock = (unsolicited == null) ? socket : unsolicited;

        if (timeout <= 0) { timeout = 1; }
        try {
            read_to_buffers(sock, timeout);
        } catch (GalilException err) { }

        String rv = unsol_msg.toString();
        unsol_msg.delete(0, unsol_msg.length());
        return rv;
    }

    public void programDownload() throws GalilException { programDownload("MG TIME" + TERMINATOR + "EN"); }
    public void programDownload(String program) throws GalilException {
        if (program.contains("\\")) {
            throw new GalilException("Galil::programDownload() can't download program with backslash \\ character.  Use {^92} in MG commands", 7060);
        }

        StringBuilder cmd = new StringBuilder("DL");
        cmd.append(TERMINATOR);
        cmd.append(program.replaceAll("[\r\n]+", TERMINATOR));
        if (!TERMINATOR.equals(cmd.substring(cmd.length()-1))) {
            cmd.append(TERMINATOR);
        }
        cmd.append("\\");
        command(cmd.toString(), "");
    }

//     void programDownloadFile(string file = "program.dmc")
//     string programUpload()  // write("LS\r"), read_to_buffers(), then manually process sol_msg (lines starting with line numbers then a space), then finish on /^:/
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
        command(String.format("QD %s[]%s%s", name, TERMINATOR, arrayJoin(array)), "\\");
    }

    public List<Double> arrayUpload() throws GalilException { return arrayUpload("array"); }
    public List<Double> arrayUpload(String name) throws GalilException {
        String str = command(String.format("QU %s[]", name));
        ArrayList<Double> rv = new ArrayList<Double>();
        for (String val : str.split(TERMINATOR)) {
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
            len = socket.getInputStream().read(buff, 0, buff.length);
        } catch (SocketTimeoutException err) {
            throw new GalilException(err.toString(), 9999);// XXX: TODO: Check code
        } catch (IOException err) {
            throw new GalilException(err.toString(), 9211);
        }

        return Arrays.toString(Arrays.copyOfRange(buff, 0, len));
    }

    public void write() throws GalilException { write(TERMINATOR); }
    public void write(String bytes) throws GalilException {
        try {
            socket.getOutputStream().write(bytes.getBytes("ISO-8859-1"));
        } catch (UnsupportedEncodingException err) {
            throw new GalilException(err.toString(), 5006);// XXX: TODO: Correct code and message
        } catch (IOException err) {
            throw new GalilException(err.toString(), 5006);// XXX: TODO: Correct code and message
        }
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
