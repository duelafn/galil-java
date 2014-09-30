
import com.svy.galil.Galil;
import com.svy.galil.GalilException;
import java.util.List;
import java.util.ArrayList;
import java.util.Arrays;

public class Test1 {
    public int tests_run = 0;
    public int tests_failed = 0;

    public static void main(String[] args) throws GalilException {
        String addr = "10.10.10.2";
        Galil galil = new Galil(addr);
        Test1 test = new Test1();

        // Two methods of message retrieval: a separate handle, or "inline"
        // Choose one of the following:
        galil.connect_unsolicited();

        // Basic Connection and command sending
        test.ok(galil.connection().startsWith(addr), "Connection String");
        test.ok(galil.commandValue("MGTIME") > 0, "Can get commandValue()");

        // Invalid command error
        try {
            galil.command("moo");
            test.ok(false, "Receive error when executing invalid command");
        } catch (GalilException err) {
            if (err.code == 2010) {
                test.ok(true, "Receive error when executing invalid command");
            } else {
                throw err;
            }
        }

        // Downloading a program
        galil.programDownload(
            "#TEST1\n"
            + "MG\"Hello World!\"\n"
            + "EN"
        );

        test.is(galil.command("XQ#TEST1"), "", "Execute Program 1");
        // Note: nontrivial message timeout only seemd to be needed when
        // processing messages "inline".
        test.is(galil.message(500), "Hello World!\r\n", "Unsolicited Message");

        // Negative test (run non-existant program)
        try {
            galil.command("XQ#TEST");
            test.ok(false, "Receive command error when executing missing program");
        } catch (GalilException err) {
            if (err.code == 2010) {
                test.ok(true, "Receive command error when executing missing program");
            } else {
                throw err;
            }
        }

        // Program and error to not break output count
        test.is(galil.commandValue("MG1+3"), 4, "Simple math");

        // Unsolicited messages do not interfere with commands
        test.is(galil.command("XQ#TEST1"), "", "Execute Program 2");
        test.is(galil.commandValue("MG1+3"), 4, "Simple math");
        test.is(galil.message(), "Hello World!\r\n", "Unsolicited Message 2");

        // Multi-command processing; Dangerous at the best of times, but we
        // should do reasonable things in the easy cases, especially when
        // our approach is compatible with the output of the standard galil
        // library.
        test.is(galil.command("MG1+2;MG3+4"), "3.0000\r\n: 7.0000", "Multi-commands (with output)");
        test.is(galil.command("x=2;y=5"), ":", "Multi-commands (without output)");

        // The galil library really just punts when it comes to this. Its
        // actual algorithm is clearly to read with a timeout and return
        // whatever it happens to get. Tests you can perform using the
        // standard galil library:
        //
        //     >>> g.command("x=2;WT5000;y=5")   -> no output
        //     >>> g.commandValue("MGx")         -> error
        //     >>> g.commandValue("MGx")         -> 0.0
        //     >>> g.commandValue("MGx")         -> 2.0
        //     >>> g.command("WT5000;x=2;y=5")   -> no output
        //     >>> g.commandValue("MGx")         -> 0.0
        //     >>> g.commandValue("MGx")         -> error
        //     >>> g.commandValue("MGx")         -> error
        //     >>> g.commandValue("MGx")         -> 0.0
        //     >>> g.commandValue("MGx")         -> 2.0
        //     >>> g.command('MG"?"')    -> 2010 COMMAND ERROR.  Galil::command("MG"?"") got ? instead of : response.  TC1 returned "0"
        //     >>> g.command('MG"23?"')  -> '23?'
        //
        // However, that algorithm allows the following to work properly
        // (which is more than we can say for our library).

        // Some of the following (marked BROKEN) aren't exactly desirable,
        // but do test conformance to current behavior. Changes which break
        // these tests by fixing the behavior are welcome (but change which
        // break these tests without fixing the behavior may be undesirable).
        test.is(galil.command("MG\"x=4:y=5\""), "x=4:y=5",   "Colon in MG");
        test.is(galil.command("MG\"Hello\""),   "Hello", "command() clears buffer before execution to aid in recovery.");
        test.is(galil.command("MG\"x=4\",{^58},\"y=5\""), "x=4",   "# TODO Hidden colon in MG");
        test.is(galil.command("MG\"Hello\""),   "Hello", "command() clears buffer before execution to aid in recovery.");


        // Array Download and Upload
        ArrayList<Double> array = new ArrayList<Double>(Arrays.asList(5., 8., 23.));
        galil.arrayDownload(array, "lst");

        // NOTE: array[-1] Feature not available on all controllers
        try {
            test.is(galil.commandValue("MGlst[-1]"), array.size(), "Array length");
        } catch (GalilException err) {
            if (err.code == 2010) {
                test.ok(true, "# SKIP controller does not seem to support array length via array[-1] syntax");
            } else {
                throw err;
            }
        }
        test.is(galil.commandValue("MGlst[0]"),  array.get(0), "array index 0");
        test.is(galil.commandValue("MGlst[1]"),  array.get(1), "array index 1");
        test.is(galil.commandValue("MGlst[2]"),  array.get(2), "array index 2");

        test.is(galil.command("lst[1]=42.42"), "", "Change array value");
        ArrayList<Double> array2 = new ArrayList<Double>(Arrays.asList(5., 42.42, 23.));
        List<Double> array3 = galil.arrayUpload("lst");

        test.is(array2.size(), array3.size(), "Array upload is correct size");
        for (int i = 0; i < array2.size(); i++) {
            test.is(array2.get(i), array3.get(i), "Array upload index " + i);
        }


        // FINISH!
        test.finish();
    }

    public void finish() {
        System.out.println("1.." + tests_run);
        if (tests_failed != 0) {
            System.err.println(String.format("# Looks like you failed %d test of %d.", tests_failed, tests_run));
        }
    }

    public boolean ok(boolean result, String msg) {
        tests_run += 1;
        String sep = msg.startsWith("# ") ? " " : " - ";
        if (result) {
            System.out.println("ok " + tests_run + sep + msg);
        } else {
            tests_failed += 1;
            System.out.println("not ok " + tests_run + sep + msg);
        }
        return result;
    }

    public void is(String value, String expected, String msg) {
        if (!ok(expected.equals(value), msg)) {
            _neq_report(value, expected, msg);
        }
    }

    public void is(double value, double expected, String msg) {
        if (!ok(Math.abs(value - expected) < 1e-6, msg))
            _neq_report(value, expected, msg);
    }
    public void is(double value, Double expected, String msg) {
        if (!ok(Math.abs(value - expected) < 1e-6, msg))
            _neq_report(value, expected, msg);
    }
    public void is(Double value, Double expected, String msg) {
        if (!ok(Math.abs(value - expected) < 1e-6, msg))
            _neq_report(value, expected, msg);
    }

    public <T> void is(T value, T expected, String msg) {
        if (!ok(value == expected, msg)) {
            _neq_report(value, expected, msg);
        }
    }

    public <T> void _neq_report(T value, T expected, String msg) {
        System.err.println("#   Failed test '"+msg+"'");
        System.err.println("#          got: '"+value+"'");
        System.err.println("#     expected: '"+expected+"'");
    }
}
