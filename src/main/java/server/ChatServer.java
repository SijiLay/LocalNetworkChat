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
            Socket clientSocket = serverSocket.accept();

            System.out.println("client connected");

            OutputStream os = clientSocket.getOutputStream();
            PrintWriter pw = new PrintWriter(os,true);
            pw.println("Welcome to the server!");

            InputStream in = clientSocket.getInputStream(); //ask for incoming data stream as raw bytes
            InputStreamReader isr = new InputStreamReader(in); //turns those bytes to character
            BufferedReader br = new BufferedReader(isr); //convenient reading of text


            String message;

            while ((message = br.readLine()) != null) {
                System.out.println(message);
            }
            System.out.println("Client disconnected");

        } catch (IOException e) {
            System.out.println("Error:" +e.getMessage());
        }
    }
}
