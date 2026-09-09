package client;

import java.io.BufferedReader;
import java.io.IOException;

public class ServerListener implements Runnable{

    private BufferedReader br;

    public ServerListener(BufferedReader br) {
        this.br = br;
    }

    @Override
    public void run() {
        String message;

        try {
            while ((message = br.readLine()) != null) {
                System.out.println(message);
            }
        } catch (IOException e) {
            System.out.println("Connection to server lost.");
        }
    }
}
