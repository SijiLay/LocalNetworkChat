package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;

public class ChatServer {

    public static void main(String[] args) {
        int port = 5000;

        try {
            ServerSocket serverSocket = new ServerSocket(port);

            System.out.println("server started");
            System.out.println("Waiting for connection...");

            // call accept() AND save what it returns
            while (true) {
                Socket clientSocket = serverSocket.accept();

                ClientHandler clientHandler = new ClientHandler(clientSocket);
                Thread thread = new Thread(clientHandler);
                thread.start();

                System.out.println("Client connected");
            }

        } catch (IOException e) {
            System.out.println("Error:" +e.getMessage());
        }
    }
}
