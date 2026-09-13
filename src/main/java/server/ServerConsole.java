package server;

import java.io.IOException;
import java.net.ServerSocket;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

public class ServerConsole implements Runnable{  // Listens for server console commands like /stop on a separate thread.

    private ServerSocket serverSocket;
    private CopyOnWriteArrayList<ClientHandler> clientHandlers;

    public ServerConsole(ServerSocket serverSocket, CopyOnWriteArrayList<ClientHandler> clientHandlers){
        this.serverSocket = serverSocket;
        this.clientHandlers = clientHandlers;
    }

    @Override
    public void run() {
        Scanner sc = new Scanner(System.in);
        while(true){
            String command = sc.nextLine();
            if(command.equals("/stop")){
                try {
                    for (ClientHandler clientHandler : clientHandlers) {
                        clientHandler.closeConnection();
                    }
                    serverSocket.close();
                } catch (IOException e) {
                    System.out.println("Disconnected");
                }
                break;
            }
        }
    }

}
