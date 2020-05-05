import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;


public class Server implements Runnable, AutoCloseable {
    private final Socket connect;

    public Server(Socket c) {
        connect = c;
    }
    public static void main(String[] args) throws Exception {

        ServerSocket serverSocket = new ServerSocket(8080);
        System.out.println("Listening for connection on port 8080 ....");
        while (true) {
            try (Server server = new Server(serverSocket.accept())) {
                Thread thread = new Thread(server);
                thread.start();
                thread.join();
            }
        }
    }

    @Override
    public void run() {
        String httpResponse = "HTTP/1.1 200 OK\r\n\r\n" + "Hello World!";
        try {
            connect.getOutputStream().write(httpResponse.getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void close() throws Exception {
        connect.close();
    }
}
