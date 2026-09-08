package client;

import java.io.IOException;
import java.net.Socket;

public class ChatClient {
    public static void main(String[] args) {

        String host = "localhost";
        int port = 5000;

        try {
            Socket socket = new Socket(host, port);
            System.out.println("Connected to server");
        } catch (IOException e) {
            System.out.println("Could not connect to " + host + ":" + port);
        }

    }
}
