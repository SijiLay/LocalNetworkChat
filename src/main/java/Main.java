import java.io.IOException;
import java.net.Socket;

public class Main {
    static void main() {
        try {
            Socket socket = new Socket("172.23.101.156",5000);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}
