package server;

import java.io.*;
import java.net.Socket;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable{

    private Socket socket;
    private CopyOnWriteArrayList<ClientHandler> clientHandlers;
    private PrintWriter pw;

    public ClientHandler(Socket socket, CopyOnWriteArrayList<ClientHandler> clientHandlers){
        this.socket=socket;
        this.clientHandlers = clientHandlers;
    }

    @Override
    public void run() {
        try {
            // create BufferedReader
            InputStream is = socket.getInputStream(); // Get incoming bytes from client
            InputStreamReader isr = new InputStreamReader(is); // turns those bytes into characters
            BufferedReader br = new BufferedReader(isr); // Makes reading  text easier

            // create PrintWriter
            OutputStream os = socket.getOutputStream(); //gets path for sending data
            pw = new PrintWriter(os, true); //send text easier + auto sends and clears

            // later: read client messages

            pw.println("Welcome to the server");

            String message;
            while ((message = br.readLine()) != null){ //keep reading until client disconnects
                for(ClientHandler clientHandler: clientHandlers){
                    clientHandler.sendMessage(message);
                }
            }
        } catch (IOException e) {
            System.out.println("Client connection error.");
        }
        clientHandlers.remove(this);
    }

    public void sendMessage(String message){
        pw.println(message);

    }
}
