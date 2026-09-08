package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
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
