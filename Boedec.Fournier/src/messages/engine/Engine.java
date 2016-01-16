package messages.engine;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.UnknownHostException;

public abstract class Engine {

    /**
     * Call this when something you don't understand happens. This is your
     * suicide, enforcing a fail-stop behavior. It is also a single place where
     * to have a breakpoint to stop when a panic occurs, giving you a chance to
     * see what has happened.
     *
     * @param msg
     */
    static public void panic(String msg) {
        try {
            throw new Exception(msg);
        } catch (Exception ex) {
            System.out.println(msg);
            ex.printStackTrace();
        }
        System.exit(-1);
    }

    PrintWriter pw;
    boolean m_running = true;

    public Engine() {
        File f = new File(String.valueOf("Stats.txt"));
        try {
            this.pw = new PrintWriter(new BufferedWriter(new FileWriter(f)));
        } catch (IOException exception) {
            System.out.println("Erreur lors de la lecture : " + exception.getMessage());
        }

    }

    long lastEcho;
    long startTime;

    long totalAcceptCount;
    long totalConnectCount;
    long totalWriteCount;
    long totalReadCount;
    long acceptCount;
    long connectCount;
    long writeCount;
    long readCount;
    Thread echoThread;
    Runnable echo = new Runnable() {
        public void run() {
            while (m_running) {
                try {
                    Thread.sleep(1000);
                    long now = System.currentTimeMillis();
                    if (now - lastEcho > 1000) {
                        if (Options.VERBOSE_STATS) {
                            long telapsed = (now - startTime) / 1000;
                            long elapsed = (now - lastEcho) / 1000;
//                            pw.println("(" + now + ")");

                            totalAcceptCount += acceptCount;
                            long tavg = totalAcceptCount / telapsed;
                            long avg = acceptCount / elapsed;
                            pw.println("   accept: " + totalAcceptCount + " (" + tavg + "/s)" + " (" + avg + "/s)");
                            acceptCount = 0;

                            totalConnectCount += connectCount;
                            tavg = totalConnectCount / telapsed;
                            avg = connectCount / elapsed;
                            pw.println("   Connect: " + totalConnectCount + " (" + tavg + "/s)" + " (" + avg + "/s)");
                            connectCount = 0;

                            totalReadCount += readCount;
                            tavg = totalReadCount / telapsed;
                            avg = readCount / elapsed;
                            pw.println("   Read: " + totalReadCount + " (" + tavg + "/s)" + " (" + avg + "/s)");
                            readCount = 0;

                            totalWriteCount += writeCount;
                            tavg = totalWriteCount / telapsed;
                            avg = writeCount / elapsed;
                            pw.println("   Write: " + totalWriteCount + " (" + tavg + "/s)" + " (" + avg + "/s)");
                            writeCount = 0;

                            pw.println();
                        } else {
                            totalAcceptCount += acceptCount;
                            acceptCount = 0;
                            totalConnectCount += connectCount;
                            connectCount = 0;
                            totalReadCount += readCount;
                            readCount = 0;
                            totalWriteCount += writeCount;
                            writeCount = 0;
                        }
                        lastEcho = now;
                    }
                } catch (InterruptedException ex) {
                }
            }
        }
    };

    /**
     * Call this to initialize a background thread to echo stats on a regular
     * basis, typically every second.
     */
    public void startEcho() {
        echoThread = new Thread(echo, "Server echo");
        echoThread.start();
    }

    Runnable timer;
    long delay;
    long last;

    /**
     * Allows to set a timer callback that will be called if the nio engine has
     * no events for the given delay.
     *
     * @param timer
     * @param delay
     */
    public void setTimer(Runnable timer, long delay) {
        this.timer = timer;
        this.delay = delay;
    }

    /**
     * NIO engine mainloop Wait for selected events on registered channels
     * Selected events for a given channel may be ACCEPT, CONNECT, READ, WRITE
     * Selected events for a given channel may change over time
     */
    public abstract void mainloop();

    /**
     * Ask for this NioEngine to accept connections on the given port, calling
     * the given callback when a connection has been accepted.
     *
     * @param port
     * @param callback
     * @return an NioServer wrapping the server port accepting connections.
     * @throws IOException if the port is already used or can't be bound.
     */
    public abstract Server listen(int port, AcceptCallback callback) throws IOException;

    /**
     * Ask this NioEngine to connect to the given port on the given host. The
     * callback will be notified when the connection will have succeeded.
     *
     * @param hostAddress
     * @param port
     * @param callback
     */
    public abstract void connect(InetAddress hostAddress, int port,
            ConnectCallback callback) throws UnknownHostException, SecurityException, IOException;

}
