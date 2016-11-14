package track.messenger.net;

import org.apache.commons.lang.SerializationException;
import org.mockito.internal.util.io.IOUtil;
import track.messenger.messages.*;
import track.messenger.store.UserStore;

import java.io.IOException;
import java.io.InterruptedIOException;
import java.io.StreamCorruptedException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.*;

/**
 *
 */

public class MessengerServer {

    private Integer port;
    private static final int NTHREADS = 4;

    private ServerSocket serverSocket;
    private LinkedBlockingQueue<Session> sessions = new LinkedBlockingQueue<>();
    private ExecutorService service = Executors.newFixedThreadPool(NTHREADS);
    public static UserStore users = new UserStore("store.sqlite3");

    public MessengerServer() {}

    public void setPort(Integer port) {
        this.port = port;
    }

    public void listen() {
        Thread listenerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                Socket clientSocket = null;
                try {
                    clientSocket = serverSocket.accept();
                    sessions.add(new Session(clientSocket));
                } catch (Exception e) {
                    System.out.println("listen: " + e.toString());
                    Thread.currentThread().interrupt();
                }
            }
        });
        listenerThread.setDaemon(true);
        listenerThread.start();
    }

    public void start() throws Exception {
        serverSocket = null;
        try {
            serverSocket = new ServerSocket(port);
            System.out.println("Ждем соединения...");
            listen();

            while (true) {
                Session session = sessions.take();
                Message msg = session.getMessage();
                if (msg != null) {
                    service.submit(() -> {
                        try {
                            session.onMessage(msg);
                            if (session.isAlive()) {
                                sessions.put(session);
                            } else {
                                System.out.println("Клиент отключился.");
                            }
                        } catch (Exception e) {
                            System.out.println("Ошибка обработки сообщения: " + e.toString());
                        }
                    });
                } else if (session.isAlive()) {
                    sessions.put(session);
                }
            }

        } finally {
            IOUtil.closeQuietly(serverSocket);
        }
    }

    public static void main(String[] args) throws Exception {
        MessengerServer server = new MessengerServer();
        server.setPort(19000);
        server.start();
    }
}
