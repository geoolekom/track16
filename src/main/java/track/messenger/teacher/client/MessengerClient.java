package track.messenger.teacher.client;


import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;
import java.util.Arrays;
import java.util.Scanner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import track.container.Container;
import track.messenger.User;
import track.messenger.messages.*;
import track.messenger.net.Protocol;
import track.messenger.net.ProtocolException;


/**
 *
 */
public class MessengerClient {

    public static final int MAX_MSG_SIZE = 32 * 1024;

    static Logger log = LoggerFactory.getLogger(MessengerClient.class);

    private Protocol protocol;
    private Integer port;
    private String host;

    private InputStream in;
    private OutputStream out;

    private User user;

    public Protocol getProtocol() {
        return protocol;
    }

    public void setProtocol(Protocol protocol) {
        this.protocol = protocol;
    }

    public Integer getPort() {
        return port;
    }

    public void setPort(Integer port) {
        this.port = port;
    }

    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }

    public void initSocket() throws IOException {
        Socket socket = new Socket(host, port);
        in = socket.getInputStream();
        out = socket.getOutputStream();

        Thread socketListenerThread = new Thread(() -> {
            final byte[] buf = new byte[MAX_MSG_SIZE];
            log.info("Слушаем сервер...");
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    // Здесь поток блокируется на ожидании данных
                    int read = in.read(buf);
                    if (read > 0) {
                        // По сети передается поток байт, его нужно раскодировать с помощью протокола
                        Message msg = protocol.decode(Arrays.copyOf(buf, read));
                        onMessage(msg);
                    }
                } catch (Exception e) {
                    log.error("Failed to process connection: {}", e);
                    e.printStackTrace();
                    Thread.currentThread().interrupt();
                }
            }
        });
        socketListenerThread.setDaemon(true);
        socketListenerThread.start();
    }

    public void onMessage(Message msg) {
        log.info("Message received: {}", msg);
        if (msg == null) {
            return;
        }

        Type msgType = msg.getType();
        switch (msgType) {
            case MSG_SELF_INFO:
                InfoResultMessage selfInfoMessage = (InfoResultMessage) msg;
                user = selfInfoMessage.getRequestedUser();
                System.out.println(selfInfoMessage.getRequestedUser());
                break;
            case MSG_USER_INFO:
                InfoResultMessage infoResultMessage = (InfoResultMessage) msg;
                System.out.println(infoResultMessage.getRequestedUser());
                break;
            case MSG_STATUS:
                StatusMessage statusMessage = (StatusMessage) msg;
                System.out.println(statusMessage);
                break;
            case MSG_CHAT_HIST_RESULT:
                ChatHistResultMessage histMessage = (ChatHistResultMessage) msg;
                histMessage.getHistory().forEach(System.out::println);
                break;
            default:
                System.out.println(this.getClass() + ": ошибка сервера");
        }
    }

    public void processInput(String line) throws IOException, ProtocolException {
        String[] tokens = line.split(" ");
        log.info("Tokens: {}", Arrays.toString(tokens));
        String cmdType = tokens[0];
        String data = "";
        if (tokens.length > 1) {
            data = line.substring(cmdType.length()).trim();
        }
        switch (cmdType) {
            case "/login":
                try {
                    send(new LoginMessage(user, data));
                } catch (InstantiationException e) {
                    System.out.println("Ошибка авторизации. " + e.toString());
                }
                break;
            case "/register":
                try {
                    send(new RegisterMessage(data));
                } catch (InstantiationException e) {
                    System.out.println("Ошибка регистрации. " + e.toString());
                }
                break;
            case "/help":
                // TODO: реализация
                break;
            case "/info":
                try {
                    send(new InfoMessage(user, data));
                } catch (InstantiationException e) {
                    System.out.println(e.toString());
                }
                break;
            case "/text":
                try {
                    send(new TextMessage(user, data));
                } catch (InstantiationException e) {
                    System.out.println("Неправильно оформленное сообщение." + e.toString());
                }
                break;
            case "/quit":
                send(new QuitMessage(user));
                break;
            case "/chat_create":
                try {
                    send(new ChatCreateMessage(user, data));
                } catch (InstantiationException e) {
                    System.out.println("Неправильно создан чат: " + e.toString());
                }
                break;
            case "/chat_hist":
                try {
                    send(new ChatHistMessage(user, data));
                } catch (InstantiationException e) {
                    System.out.println("ошибка запроса истории: " + e.toString());
                }
                break;
            default:
                log.error("Invalid input: " + line);
                break;
        }
    }

    public void send(Message msg) throws IOException, ProtocolException {
        log.info(msg.toString());
        out.write(protocol.encode(msg));
        out.flush(); // принудительно проталкиваем буфер с данными
    }

    public void close() {
    }

    public void start() {
        try {
            initSocket();

            // Цикл чтения с консоли
            Scanner scanner = new Scanner(System.in);
            while (true) {
                String input = scanner.nextLine();
                try {
                    processInput(input);
                } catch (ProtocolException | IOException e) {
                    log.error("Ошибки при обработке потока ввода.", e);
                }
                if ("/quit".equals(input)) {
                    System.out.println("Завершение сеанса...");
                    break;
                }
            }
        } catch (Exception e) {
            log.error("Приложение рухнуло с оглушительным грохотом.", e);
        } finally {
            close();
        }
    }

    public static void main(String[] args) throws Exception {

        Container container = new Container("client.xml");
        MessengerClient client = (MessengerClient) container.getByName("messengerClient");
        client.start();
    }
}