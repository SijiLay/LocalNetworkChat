package client;

import java.io.*;
import java.net.Socket;
import java.util.Scanner;

public class ChatClient {
    public static void main(String[] args) {

        String host = "localhost";
        int port = 5000;


        try { //This being run will unblock the server and connect a client to the server

            Socket socket = new Socket(host, port); //trying to connect to server
            Scanner sc = new Scanner(System.in); //reads keyboard import
            System.out.println("Connected to server");

            InputStream is = socket.getInputStream(); // get bytes from server
            InputStreamReader isr = new InputStreamReader(is); //turn bytes to characters
            BufferedReader br = new BufferedReader(isr); // makes it easier to read

            OutputStream os = socket.getOutputStream(); //path for sending data
            PrintWriter pw = new PrintWriter(os, true); //easy way to send text

            String serverStatus = br.readLine();

            if (serverStatus == null) {
                System.out.println("Server disconnected.");
                socket.close();
                sc.close();
                return;
            }

            if(serverStatus.equalsIgnoreCase("SERVER_FULL")){
                System.out.println("Server is full. Try again later.");
                socket.close();
                sc.close();
                return;
            }
            else if (serverStatus.equalsIgnoreCase("SERVER_AVAILABLE")){
                System.out.print("Enter username: ");
                String username = sc.nextLine(); //saves the string to move to clienthandler where it saves it as the username and also called for the actuall messaging format
                pw.println(username);

                String response = br.readLine();

                if (response == null) {
                    System.out.println("Server disconnected.");
                    socket.close();
                    sc.close();
                    return;
                }

                while (response.equalsIgnoreCase("Username already taken") || response.equalsIgnoreCase("Invalid username")) {
                    if(response.equalsIgnoreCase("Username already taken")){
                        System.out.println("Username already taken, Try Again");
                    }
                    else{
                        System.out.println("Invalid Username, Try Again");
                    }
                    System.out.print("Enter username: ");
                    username = sc.nextLine();
                    pw.println(username);
                    response = br.readLine();

                    if (response == null) {
                        System.out.println("Server disconnected.");
                        socket.close();
                        sc.close();
                        return;
                    }
                }
                System.out.println(response);
            }
            else {
                System.out.println("Unexpected response from server.");
                socket.close();
                sc.close();
                return;
            }


            ServerListener listener = new ServerListener(br,socket); //creates a server-message listener
            Thread listenerThread = new Thread(listener); //creates listener thread
            listenerThread.start();



            Thread inputThread = new Thread(() -> {

                while (!socket.isClosed()) {

                    String message = sc.nextLine();

                    if (message.equalsIgnoreCase("/quit")) {
                        try {
                            socket.close();
                        } catch (IOException e) {
                            System.out.println("Error closing connection.");
                        }
                        break;
                    }
                    pw.println(message);
                }
            });

            inputThread.setDaemon(true);
            inputThread.start();
            try {
                listenerThread.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            System.out.println("Disconnected from server");

            if (!socket.isClosed()) {
                socket.close();
            } //close keyboard Scanner

        } catch (IOException e) {
            System.out.println("Could not connect to " + host + ":" + port);
        }


    }
}
