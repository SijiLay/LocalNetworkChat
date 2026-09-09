package server;

import java.io.*;
import java.net.Socket;

public class ClientHandler implements Runnable{

    private Socket socket;

    public ClientHandler(Socket socket){
        this.socket=socket;
    }

    @Override
    public void run() {
        try {
            // create BufferedReader
            InputStream is = socket.getInputStream();
            InputStreamReader isr = new InputStreamReader(is);
            BufferedReader br = new BufferedReader(isr);

            // create PrintWriter
            OutputStream os = socket.getOutputStream();
            PrintWriter pw = new PrintWriter(os,true);

            // later: read client messages

            pw.println("Welcome to the server");

            String message;
            while ((message = br.readLine()) != null){
                System.out.println(message);
            }
        } catch (IOException e) {
            System.out.println("Client connection error.");
        }
    }
}
