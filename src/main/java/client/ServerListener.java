package client;

import java.io.BufferedReader;
import java.io.IOException;
import java.net.Socket;

public class ServerListener implements Runnable{

    private BufferedReader br; //Reader for messages from server
    private Socket socket;

    public ServerListener(BufferedReader br, Socket socket) { //Receives server reader
        this.br = br;
        this.socket = socket;
    }

    @Override
    public void run() { //listener thread runs
        String message;

        try {
            while ((message = br.readLine()) != null) { //keeps reading server message
                System.out.println(message);
            }
            System.out.println("Server disconnected.");
            socket.close();
            System.exit(0);
        } catch (IOException e) {
            System.out.println("Connection to server lost.");
        }
    }
}
