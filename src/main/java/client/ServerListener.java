package client;

import java.io.BufferedReader;
import java.io.IOException;

public class ServerListener implements Runnable{

    private BufferedReader br; //Reader for messages from server

    public ServerListener(BufferedReader br) { //Receives server reader
        this.br = br;
    }

    @Override
    public void run() { //listener thread runs
        String message;

        try {
            while ((message = br.readLine()) != null) { //keeps reading server message
                System.out.println(message);
            }
        } catch (IOException e) {
            System.out.println("Connection to server lost.");
        }
    }
}
