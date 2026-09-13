package server;

import java.io.*;
import java.net.Socket;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.CopyOnWriteArrayList;

public class ClientHandler implements Runnable {

    private Socket socket;
    private CopyOnWriteArrayList<ClientHandler> clientHandlers;
    private PrintWriter pw;
    private String username;
    private boolean joined = false;

    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern("hh:mm a");


    public ClientHandler(Socket socket, CopyOnWriteArrayList<ClientHandler> clientHandlers) {
        this.socket = socket;
        this.clientHandlers = clientHandlers;
    }

    @Override
    public void run() {
        try {
            // create BufferedReader
            InputStream is = socket.getInputStream(); // Get incoming bytes from client
            InputStreamReader isr = new InputStreamReader(is); // turns those bytes into characters
            BufferedReader br = new BufferedReader(isr); // Makes reading text easier

            username = br.readLine(); //gets first incoming text from reader and saves it as the handlers username
            if (username == null) {
                return;
            }

            // create PrintWriter
            OutputStream os = socket.getOutputStream(); //gets path for sending data
            pw = new PrintWriter(os, true); //send text easier + auto sends and clears

            while (!isUsernameValid(username)) {
                if (username == null || username.isBlank()) {
                    pw.println("Invalid username");
                }
                else if (isUsernameTaken(username)) {
                    pw.println("Username already taken");
                }

                username = br.readLine();
                if (username == null) {
                    return;
                }
            }

            pw.println("Welcome to the server");
            joined = true;
            String joinTime = LocalTime.now().format(FORMATTER);

            for (ClientHandler clientHandler : clientHandlers) {
                if(clientHandler != this) {
                    clientHandler.sendMessage("[" + joinTime + "] " +username + " joined the chat");
                }
            }

            String message;
            while ((message = br.readLine()) != null) { //keep reading until client disconnects
                if(message.isBlank()){
                    continue;
                }
                else if(message.length()>500){
                    pw.println("Message cannot be over 500 characters");
                }
                else {
                    String messageTime = LocalTime.now().format(FORMATTER);
                    for (ClientHandler clientHandler : clientHandlers) {
                        clientHandler.sendMessage("[" + messageTime + "] " + username + ": " + message);
                    }
                }
            }


        } catch (IOException e) {
            System.out.println("Client connection error.");
        }

        finally {
            clientHandlers.remove(this);

            if(joined){
                String leaveTime = LocalTime.now().format(FORMATTER);

                for (ClientHandler clientHandler : clientHandlers) {
                    clientHandler.sendMessage("[" + leaveTime + "] " + username + " left the chat");
                }
            }
        }
    }

    public void sendMessage(String message) {
        pw.println(message);
    }

    public String getUsername() {
        return username;
    }

    private boolean isUsernameTaken(String username) {
        for (ClientHandler clientHandler : clientHandlers) {
            if (clientHandler != this && clientHandler.getUsername() != null && clientHandler.getUsername().equals(username)) {
                return true;
            }
        }
        return false;
    }

    private boolean isUsernameValid(String username) {
            return username != null && !username.isBlank() && !isUsernameTaken(username);
        }
    public void closeConnection() {
        try {
            socket.close();
        } catch (IOException e) {
            System.out.println("Error closing client connection.");
        }
    }
}
