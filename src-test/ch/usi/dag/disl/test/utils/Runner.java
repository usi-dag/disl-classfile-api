package ch.usi.dag.disl.test.utils;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import ch.usi.dag.dislreserver.DiSLREServer;
import ch.usi.dag.dislserver.DiSLServer;
import ch.usi.dag.util.Duration;
import ch.usi.dag.util.Strings;


public abstract class Runner {

    protected static final Duration _INIT_TIME_LIMIT_ = Duration.of (3, SECONDS);
    protected static final Duration _TEST_TIME_LIMIT_ = Duration.of (45, SECONDS);
    protected static final Duration _WATCH_DELAY_ = Duration.of (100, MILLISECONDS);

    protected static final String _ENV_JAVA_HOME_ = "JAVA_HOME";

    protected static final File _DISL_LIB_DIR_ = new File (System.getProperty ("runner.disl.lib.dir", "lib"));
    protected static final File _DISL_THREAD_DIR_ = new File (System.getProperty ("runner.disl.thread.dir", "disl-thread"));

    protected static final File _DISL_AGENT_LIB_ = __libPath ("runner.disl.agent.lib", "libdislagent.so");
    protected static final File _DISL_BYPASS_JAR_ = __libPath ("runner.disl.bypass.jar", "disl-bypass.jar");
    protected static final File _DISL_SERVER_JAR_ = __libPath ("runner.disl.server.jar", "disl-server.jar");
    protected static final Class <?> _DISL_SERVER_CLASS_ = DiSLServer.class;

    protected static final File _SHVM_AGENT_LIB_ = __libPath ("runner.shvm.agent.lib", "libdislreagent.so");
    protected static final File _SHVM_DISPATCH_JAR_ = __libPath ("runner.shvm.dispatch.jar", "dislre-dispatch.jar");
    protected static final File _SHVM_SERVER_JAR_ = __libPath ("runner.shvm.server.jar", "dislre-server.jar");
    protected static final Class <?> _SHVM_SERVER_CLASS_ = DiSLREServer.class;

    private static File __libPath (final String property, final String defaultValue) {
        return new File (_DISL_LIB_DIR_, System.getProperty (property, defaultValue));
    }

    //

    protected static final File _TEST_LIB_DIR_ = new File (System.getProperty ("runner.lib.dir", "test-jars"));
    static final boolean TEST_DEBUG = Boolean.getBoolean ("runner.debug");

    //

    private final String __testName;
    private final Class <?> __testClass;

    //

    Runner (final Class <?> testClass) {
        __testClass = testClass;
        __testName = __extractTestName (testClass);
    }

    protected static String _getJavaCommand (final String javaHome) {
        final String home = (javaHome != null) ? javaHome : System.getenv (_ENV_JAVA_HOME_);
        if (home != null) {
            if (new File (home, "jre").exists ()) {
                return Strings.join (File.separator, home, "jre", "bin", "java");
            } else {
                return Strings.join (File.separator, home, "bin", "java");
            }
        } else {
            return "java";
        }
    }

    protected static int _getJavaVersion (final String javaCommand) throws IOException {
        final ProcessBuilder builder = new ProcessBuilder(javaCommand, "-version");
        builder.redirectErrorStream(true);
        final Process process = builder.start();
        final InputStream is = process.getInputStream();
        final BufferedReader reader = new BufferedReader(new InputStreamReader(is));

        String line = null;
        final StringBuilder output = new StringBuilder();
        while ((line = reader.readLine()) != null) {
            output.append(line);
        }

        final Pattern pattern = Pattern.compile("\\\"(\\d+)(?:\\.(\\d+))?");
        final Matcher matcher = pattern.matcher(output);
        if (!matcher.find()) {
            throw new RunnerException ("failed to get java version");
        }

        String version = matcher.group(1);
        if (version.equals("1")) {
            version = matcher.group(2);
        }
        
        return Integer.parseInt(version);
    }

    private static String __extractTestName (final Class <?> testClass) {
        final String [] packages = testClass.getPackage ().getName ().split ("\\.");
        return packages [packages.length - 2];
    }

    //

    public void start () {
        final File instJar = __getTestJar ("instrumentation", "%s-inst.jar", __testName);
        final File appJar = __getTestJar ("application", "%s-app.jar", __testName);

        try {
            _start (instJar, appJar);

        } catch (final IOException ioe) {
            throw new RunnerException (ioe, "failed to start runner");
        }
    }

    private static File __getTestJar (
        final String jarType, final String format, final Object ... args
    ) {
        final File result = new File (
            _TEST_LIB_DIR_, String.format (format, args)
        );

        if (!result.exists ()) {
            throw new RunnerException ("%s jar not found: %s", jarType, result);
        }

        return result;
    }

    protected abstract void _start (File instJar, File appJar) throws IOException;

    //

    public final boolean waitFor () {
        return _waitFor (_TEST_TIME_LIMIT_);
    }

    protected abstract boolean _waitFor (final Duration duration);

    //

    public final void assertIsStarted () {
        _assertIsStarted ();
    }

    protected abstract void _assertIsStarted ();

    //

    public final void assertIsFinished () {
        _assertIsFinished ();
    }

    protected abstract void _assertIsFinished ();

    //

    public final void assertIsSuccessful () {
        _assertIsSuccessful ();
    }

    protected abstract void _assertIsSuccessful ();

    //

    protected String _loadResource (final String name) throws IOException {
        return Strings.loadFromResource (__testClass, name);
    }

    //

    public final void destroyIfRunningAndDumpOutputs () throws IOException {
        _destroyIfRunningAndDumpOutputs ();
    }

    protected abstract void _destroyIfRunningAndDumpOutputs () throws IOException;


    protected final void _destroyIfRunningAndDumpOutputs (
        final Job job, final String prefix
    ) throws IOException {
        if (job.isRunning ()) {
            job.destroy ();
        }

        Strings.storeToFile (
            String.format ("test.%s.%s.out.txt", __testName, prefix),
            job.getOutput ()
        );


        Strings.storeToFile (
            String.format ("test.%s.%s.err.txt", __testName, prefix),
            job.getError ()
        );
    }

    //

    public final void destroy () {
        _destroy ();
    }

    protected abstract void _destroy ();

    //

    public final void assertRestOutErrEmpty () throws IOException {
        _assertRestOutErrEmpty ();
    }

    protected abstract void _assertRestOutErrEmpty () throws IOException;

    //

    static File createStatusFile (final String prefix) throws IOException {
        final File result = File.createTempFile (prefix, ".status");
        result.deleteOnExit ();
        return result;
    }

    static void killJobs (final Job ... jobs) {
        for (final Job job : jobs) {
            if (job != null) {
                job.destroy ();
            }
        }
    }

    static void ensureJobInitialized (
        final Job job, final String jobName, final File statusFile
    ) {
        final boolean jobTimedOut = __awaitRemoval (statusFile, _INIT_TIME_LIMIT_);

        if (! job.isRunning ()) {
            throw new RunnerException (
                "%s initialization failed:\n%s", jobName, getJobError (job)
            );
        }

        if (jobTimedOut) {
            throw new RunnerException (
                "%s initialization timed out", jobName, getJobError (job)
            );
        }
    }


    private static boolean __awaitRemoval (
        final File file, final Duration duration
    ) {
        final long watchEnd = System.nanoTime () + duration.to (TimeUnit.NANOSECONDS);
        while (file.exists () &&  System.nanoTime () < watchEnd) {
            _WATCH_DELAY_.sleepUninterruptibly ();
        }

        return file.exists ();
    }


    static String getJobError (final Job job) {
        try {
            return job.getError ();

        } catch (final IOException e) {
            return "unknown error, failed to read job error output";
        }
    }


    static List <String> propertiesStartingWith (final String prefix) {
        final List <String> result = new LinkedList <String> ();

        for (final String key : System.getProperties ().stringPropertyNames ()) {
            if (key.startsWith (prefix)) {
                final String value = System.getProperty (key);
                if (value != null) {
                    result.add (String.format ("-D%s=%s", key, value));
                }
            }
        }

        return result;
    }


    static String classPath (final File ... paths) {
        return Strings.join (File.pathSeparator, (Object []) paths);
    }

}
