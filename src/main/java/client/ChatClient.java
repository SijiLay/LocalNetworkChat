package client;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {
    public static void main(String[] args) {

        String host = "localhost";
        int port = 5000;

        try { //This being run will unblock the server and connect a client to the server

            Scanner sc = new Scanner(System.in);
            Socket socket = new Socket(host, port); //trying to connect to server
            System.out.println("Connected to server");

            OutputStream os = socket.getOutputStream(); //path for sending data

            PrintWriter pw = new PrintWriter(os, true); //easy way to send text

            while (true) {
                String message = sc.nextLine();
                if (message.equalsIgnoreCase("/quit")) {
                    break;
                }
                pw.println(message);
            }
            System.out.println("Client from server disconnected");
            socket.close();
            sc.close();

        } catch (IOException e) {
            System.out.println("Could not connect to " + host + ":" + port);
        }


    }
}
