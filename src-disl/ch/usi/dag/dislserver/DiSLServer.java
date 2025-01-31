package ch.usi.dag.dislserver;

import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.StandardSocketOptions;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.ClosedByInterruptException;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;

import ch.usi.dag.dislserver.Protocol.InstrumentClassRequest;
import ch.usi.dag.dislserver.Protocol.InstrumentClassResponse;
import ch.usi.dag.dislserver.Protocol.InstrumentClassResult;
import ch.usi.dag.util.logging.Logger;


public final class DiSLServer {

    private static final Logger __log = Logging.getPackageInstance ();

    //

    private static final String PROP_PORT = "dislserver.port";
    private static final int DEFAULT_PORT = 11217;

    private static final String PROP_CONT = "dislserver.continuous";
    private static final boolean continuous = Boolean.getBoolean(PROP_CONT);

    //

    private static final String __PID_FILE__ = "server.pid.file";
    private static final String __STATUS_FILE__ = "server.status.file";

    //

    private enum ElapsedTime {
        RECEIVE, UNPACK, PROCESS, PACK, TRANSMIT;
    }

    //

    private final AtomicInteger __workerCount = new AtomicInteger ();
    private final CounterSet <ElapsedTime> __globalStats = new CounterSet <ElapsedTime> (ElapsedTime.class);

    //

    final class ConnectionHandler implements Runnable {

        private final SocketChannel __clientSocket;
        private final RequestProcessor __requestProcessor;
        private final Thread __serverThread;

        //

        ConnectionHandler (
            final SocketChannel clientSocket,
            final RequestProcessor requestProcessor,
            final Thread serverThread
        ) {
            __clientSocket = clientSocket;
            __requestProcessor = requestProcessor;
            __serverThread = serverThread;
        }

        @Override
        public void run () {
            __workerCount.incrementAndGet ();

            final CounterSet <ElapsedTime> stats = new CounterSet  <ElapsedTime> (ElapsedTime.class);
            final IntervalTimer <ElapsedTime> timer = new IntervalTimer <ElapsedTime> (ElapsedTime.class);

            try {
                //
                // Process requests until a shutdown request is received, a
                // communication error occurs, or an internal error occurs.
                //
                final ByteBuffer headBuffer = __allocDirect (4096);
                ByteBuffer recvBuffer = __allocDirect (4096);
                ByteBuffer sendBuffer = __allocDirect (4096);

                REQUEST_LOOP: while (true) {
                    timer.reset ();

                    //

                    __log.trace ("receiving instrumentation request");

                    headBuffer.clear ();
                    __bufferRecvFrom (__clientSocket, Integer.BYTES, headBuffer);

                    headBuffer.flip ();
                    final int messageLength = headBuffer.getInt ();
                    __log.trace ("expecting message of length %d", messageLength);

                    if (messageLength == 0) {
                        __log.debug ("received empty message, exiting");
                        timer.mark (ElapsedTime.RECEIVE);
                        stats.update (timer);
                        break REQUEST_LOOP;
                    }

                    recvBuffer.clear ();
                    if (recvBuffer.remaining () < messageLength) {
                        recvBuffer = __expandBuffer (recvBuffer, messageLength);
                        __log.debug ("expanded receive buffer to %d bytes", recvBuffer.capacity ());
                    }

                    __bufferRecvFrom (__clientSocket, messageLength, recvBuffer);
                    timer.mark (ElapsedTime.RECEIVE);

                    //

                    __log.trace ("unpacking instrumentation request");

                    recvBuffer.flip ();
                    final CodedInputStream recvStream = CodedInputStream.newInstance (recvBuffer);
                    final InstrumentClassRequest request = InstrumentClassRequest.parseFrom (recvStream);
                    timer.mark (ElapsedTime.UNPACK);

                    //
                    // Process the request and send the response to the client.
                    // Update the timing stats if everything goes well.
                    //
                    __log.trace ("processing instrumentation request");

                    final InstrumentClassResponse response = __requestProcessor.process (request);
                    timer.mark (ElapsedTime.PROCESS);

                    //
                    // Note: the CodedOutputStream remembers buffer position at
                    // creation time, so anything put into a byte buffer after
                    // CodedOutputStream has been created will be lost/corrupted.
                    //
                    // Also, it cannot be reused and needs to be created for
                    // every message packed.
                    //
                    __log.trace ("packing instrumentation response");

                    final int responseLength = response.getSerializedSize ();

                    sendBuffer.clear ();
                    sendBuffer.putInt (responseLength);

                    if (sendBuffer.remaining () < responseLength) {
                        sendBuffer = __expandBuffer (sendBuffer, responseLength);
                        __log.debug ("expanded send buffer to %d bytes", sendBuffer.capacity ());
                    }

                    if (responseLength > 0) {
                        final CodedOutputStream sendStream = CodedOutputStream.newInstance (sendBuffer);
                        response.writeTo (sendStream);
                        sendStream.flush ();
                    }

                    timer.mark (ElapsedTime.PACK);

                    //

                    __log.trace ("sending instrumentation response");

                    sendBuffer.flip ();
                    __bufferSendTo (sendBuffer, __clientSocket);

                    timer.mark (ElapsedTime.TRANSMIT);

                    //

                    stats.update (timer);

                    if (response.getResult () == InstrumentClassResult.ERROR) {
                        //
                        // Error during instrumentation. Report it to the client
                        // and stop receiving requests from this connection.
                        //
                        break REQUEST_LOOP;
                    }

                } // REQUEST_LOOP

                //
                // Merge thread-local stats with global stats when leaving
                // the request loop in a peaceful manner.
                //
                __globalStats.update (stats);


            } catch (final IOException ioe) {
                //
                // Communication error -- just log a message here.
                //
                __log.error (
                    "error communicating with client: %s", ioe.getMessage ()
                );

            } catch (final Throwable t) {
                __log.error (t, "failed to process instrumentation request");

            } finally {
                //
                // If there are no more workers left and we are not operating
                // in continuous mode, shut the server down.
                //
                if (__workerCount.decrementAndGet () == 0) {
                    if (!continuous) {
                        __serverThread.interrupt ();
                    }
                }
            }
        }

        //

        private void __bufferSendTo (
            final ByteBuffer buffer, final SocketChannel sc
        ) throws IOException {
            while (buffer.hasRemaining ()) {
                sc.write (buffer);
            }
        }


        private void __bufferRecvFrom (
            final SocketChannel sc, final int length, final ByteBuffer buffer
        ) throws IOException {
            buffer.limit (buffer.position () + length);
            while (buffer.hasRemaining ()) {
                final int bytesRead = sc.read (buffer);
                if (bytesRead < 0) {
                    throw new EOFException ("unexpected end of stream");
                }
            }
        }


        private ByteBuffer __expandBuffer (
            final ByteBuffer buffer, final int messageLength
        ) {
            //
            // The buffer needs to be in receive mode, i.e., the buffer
            // position indicates the first available byte. Any bytes before
            // current position will be copied to the new buffer.
            //
            final int requiredCapacity = buffer.position () + messageLength;

            int newCapacity = 2 * buffer.capacity ();
            while (newCapacity < requiredCapacity) {
                newCapacity *= 2;
            }

            buffer.flip ();
            return __allocDirect (newCapacity).put (buffer);
        }


        private ByteBuffer __allocDirect (final int capacity) {
            return ByteBuffer.allocateDirect (capacity).order (ByteOrder.BIG_ENDIAN);
        }

    }

    //

    void run (
        final ServerSocketChannel serverSocket,
        final ExecutorService executor,
        final RequestProcessor requestProcessor
    ) {
        try {
            final Thread serverThread = Thread.currentThread ();

            while (!serverThread.isInterrupted ()) {
                final SocketChannel clientSocket = serverSocket.accept ();
                clientSocket.setOption (StandardSocketOptions.TCP_NODELAY, true);

                __log.debug (
                    "connection from %s", clientSocket.getRemoteAddress ()
                );

                // client socket handed off to connection handler
                executor.submit (new ConnectionHandler (
                    clientSocket, requestProcessor, serverThread
                ));
            }

        } catch (final ClosedByInterruptException cbie) {
            //
            // The server was interrupted, we are shutting down.
            //

        } catch (final IOException ioe) {
            //
            // Communication error -- just log a message here.
            //
            __log.error ("error accepting a connection: %s", ioe.getMessage ());
        }

        //

        __log.debug ("receiving data took %d ms", __stats (ElapsedTime.RECEIVE));
        __log.debug ("unpacking data took %d ms", __stats (ElapsedTime.UNPACK));
        __log.debug ("processing took %d ms", __stats (ElapsedTime.PROCESS));
        __log.debug ("packing data took %d ms", __stats (ElapsedTime.PACK));
        __log.debug ("transmitting data took %d ms", __stats (ElapsedTime.TRANSMIT));
    }

    private long __stats (final ElapsedTime et) {
        return TimeUnit.MILLISECONDS.convert (
            __globalStats.get (et), TimeUnit.NANOSECONDS
        );
    }

    //

    public static void main (final String [] args) {
        __log.debug ("server starting");
        __log.debug ("java.home: %s", System.getProperty ("java.home", ""));
        __log.debug ("java.class.path: %s", System.getProperty ("java.class.path", ""));

        __serverStarting ();

        final InetSocketAddress address = __getListenAddressOrDie ();
        final ServerSocketChannel socket = __getServerSocketOrDie (address);

        __log.debug (
            "listening on %s:%d", address.getHostString (), address.getPort ()
        );

        //

        final RequestProcessor processor = __getRequestProcessorOrDie ();
        final ExecutorService executor = Executors.newCachedThreadPool ();
        final DiSLServer server = new DiSLServer ();

        __log.debug ("server started");
        __serverStarted ();

        server.run (socket, executor, processor);

        __log.debug ("server shutting down");
        executor.shutdown ();
        processor.terminate ();
        __closeSocket (socket);

        __log.debug ("server finished");
        System.exit (0);
    }

    //

    private static void __serverStarting () {
        final File file = __getFileProperty (__PID_FILE__);
        if (file != null) {
            file.deleteOnExit ();
        }
    }


    private static void __serverStarted () {
        final File file = __getFileProperty (__STATUS_FILE__);
        if (file != null) {
            file.delete ();
        }
    }


    private static File __getFileProperty (final String name) {
        final String value = System.getProperty (name, "").trim ();
        return value.isEmpty () ? null : new File (value);
    }


    private static InetSocketAddress __getListenAddressOrDie () {
        try {
            final int port = Integer.getInteger (PROP_PORT, DEFAULT_PORT);
            return new InetSocketAddress (port);

        } catch (final IllegalArgumentException iae) {
            __die (iae, "port must be between 1 and 65535 (inclusive)");
            throw new AssertionError ("unreachable");
        }
    }


    private static ServerSocketChannel __getServerSocketOrDie (final SocketAddress addr) {
        try {
            final ServerSocketChannel ssc = ServerSocketChannel.open ();
            ssc.setOption (StandardSocketOptions.SO_REUSEADDR, true);
            ssc.bind (addr);
            return ssc;

        } catch (final IOException ioe) {
            __die (ioe, "failed to create socket");
            throw new AssertionError ("unreachable");
        }
    }


    private static RequestProcessor __getRequestProcessorOrDie () {
        try {
            return RequestProcessor.newInstance ();

        } catch (final Exception e) {
            __die (e, "failed to initialize request processor");
            throw new AssertionError ("unreachable");
        }
    }


    private static void __closeSocket (final Closeable socket) {
        try {
            socket.close ();

        } catch (final IOException ioe) {
            __log.warn ("failed to close socket: %s", ioe.getMessage ());
        }
    }


    private static void __die (final Exception e, final String message) {
        __logExceptionChain (e, 3);
        __log.error (message);
        System.exit (1);
    }


    private static void __logExceptionChain (final Throwable exception, final int limit) {
        if (exception != null && limit > 0) {
            __logExceptionChain (exception.getCause (), limit - 1);
            __log.error (exception.getMessage ());
        }
    }

}
