package server;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

public class ChatServer {

    public static void main(String[] args) {
        int port = 5000;
        CopyOnWriteArrayList<ClientHandler> clientHandlers = new CopyOnWriteArrayList<>();
        int MAX_CLIENTS = 2;

        ServerSocket serverSocket = null;

        try {
            serverSocket = new ServerSocket(port); //Opens server to allow accepting clients on port
            ServerConsole serverConsole = new ServerConsole(serverSocket,clientHandlers);
            Thread consoleThread = new Thread(serverConsole);
            consoleThread.start();

            System.out.println("server started");
            System.out.println("Waiting for connection...");

            // call accept() AND save what it returns
            while (true) { //constantly run to accept new clients
                Socket clientSocket = serverSocket.accept(); //waits for and accepts a client, then stores client in client socket

                OutputStream os = clientSocket.getOutputStream();
                PrintWriter pw = new PrintWriter(os, true);

                if (clientHandlers.size() >= MAX_CLIENTS) {
                    // reject this new client

                    pw.println("SERVER_FULL");
                    clientSocket.close();
                } else {

                    pw.println("SERVER_AVAILABLE");
                    ClientHandler clientHandler = new ClientHandler(clientSocket,clientHandlers); //creates handler for client
                    clientHandlers.add(clientHandler);
                    Thread thread = new Thread(clientHandler); //creates thread to run handler
                    thread.start();

                    System.out.println("Client connected");
                }

            }

        } catch (IOException e) {
            if(serverSocket.isClosed()){
                System.out.println("Server stopped");
            }
            else {
                System.out.println("Error: " +e.getMessage());
            }
        }
    }
}
