package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {
    public static void main(String[] args) {

        String host = "localhost";
        int port = 5000;

        try { //This being run will unblock the server and connect a client to the server

            Scanner sc = new Scanner(System.in); //reads keyboard import
            Socket socket = new Socket(host, port); //trying to connect to server
            System.out.println("Connected to server");

            InputStream is = socket.getInputStream(); // get bytes from server
            InputStreamReader isr = new InputStreamReader(is); //turn bytes to characters
            BufferedReader br = new BufferedReader(isr); // makes it easier to read

            ServerListener listener = new ServerListener(br); //creates a server-message listener
            Thread thread = new Thread(listener); //creates listener thread
            thread.start();

            OutputStream os = socket.getOutputStream(); //path for sending data

            PrintWriter pw = new PrintWriter(os, true); //easy way to send text

            while (true) {
                String message = sc.nextLine(); // Wait for keyboard input
                if (message.equalsIgnoreCase("/quit")) {
                    break;
                }
                pw.println(message);
            }
            System.out.println("Disconnected from server");
            socket.close(); //close server connection
            sc.close(); //close keyboard Scanner

        } catch (IOException e) {
            System.out.println("Could not connect to " + host + ":" + port);
        }


    }
}
