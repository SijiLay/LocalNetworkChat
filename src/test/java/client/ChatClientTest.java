package client;

import org.junit.jupiter.api.*;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit/integration tests for ChatClient.
 *
 * ChatClient is a `public static void main` that hardcodes host="localhost"
 * and port=5000, reads keyboard input via a fresh `new Scanner(System.in)`,
 * and prints everything via System.out. As with ChatServerTest, that means:
 *   - System.in is redirected to a pipe we control so we can "type" input.
 *   - System.out is redirected to a buffer we can inspect.
 *   - Tests run sequentially and must not overlap ChatServerTest, since both
 *     hardcode port 5000.
 *
 * IMPORTANT: ServerListener.run() calls System.exit(0) when it detects a
 * *graceful* server disconnect (server closes the socket cleanly while the
 * client is idle). Running that path in-process would kill this whole test
 * JVM. So:
 *   - Every OTHER test here runs ChatClient.main() on a background thread in
 *     this JVM (safe -- an uncaught exception on that thread only kills that
 *     thread, it doesn't crash the test run).
 *   - The one test that triggers a graceful post-login disconnect
 *     (serverDisconnectAfterLoginIsHandledWithoutKillingTheProcess) launches
 *     ChatClient in a real, separate OS process instead, so its System.exit(0)
 *     only ends that subprocess.
 *
 * Two tests here (serverDisconnectsBeforeSendingInitialStatus and
 * serverDisconnectsWhileWaitingForUsernameResponse) deliberately trigger an
 * uncaught NullPointerException on the client's background thread -- that's
 * expected and will print a stack trace to stderr during the test run; it
 * does not fail the test process itself.
 */
class ChatClientTest {

    private static final int PORT = 5000;

    private InputStream originalSystemIn;
    private PrintStream originalSystemOut;
    private PipedOutputStream stdinPipe;
    private ByteArrayOutputStream capturedOut;
    private Thread clientThread;

    @BeforeEach
    void setUp() throws IOException {
        originalSystemIn = System.in;
        originalSystemOut = System.out;

        stdinPipe = new PipedOutputStream();
        PipedInputStream pipedIn = new PipedInputStream(stdinPipe);
        System.setIn(pipedIn);

        capturedOut = new ByteArrayOutputStream();
        System.setOut(new PrintStream(capturedOut, true));
    }

    @AfterEach
    void tearDown() throws Exception {

        if (clientThread != null && clientThread.isAlive()) {
            try {
                typeLine("/quit");
            } catch (IOException ignored) {
                // Client may have already stopped reading input
            }

            clientThread.join(1000);
        }

        System.setIn(originalSystemIn);
        System.setOut(originalSystemOut);
    }

    // ------------------------------------------------------------------
    // Connection / server status
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void connectsUsingTheCorrectHostAndPort() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            // successfully accepting a connection on exactly localhost:5000
            // is the observable proof the client used the right host/port
            server.acceptClient();
            assertTrue(server.accepted.isConnected());
        }
    }

    @Test
    @Timeout(5)
    void handlesServerAvailable() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            server.acceptClient();
            server.send("SERVER_AVAILABLE");

            assertTrue(awaitOutputContains("Enter username: ", 2000));
        }
    }

    @Test
    @Timeout(5)
    void handlesServerFull() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            server.acceptClient();
            server.send("SERVER_FULL");

            assertTrue(awaitOutputContains("Server is full. Try again later.", 2000));
        }
    }

    @Test
    @Timeout(5)
    void closesAndExitsWhenServerIsFull() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            server.acceptClient();
            server.send("SERVER_FULL");

            assertTrue(awaitOutputContains("Server is full. Try again later.", 2000));

            // client should close its socket and main() should return, rather
            // than hanging around waiting for a username
            assertNull(server.receive(), "Client should close the connection instead of proceeding");
            clientThread.join(2000);
            assertFalse(clientThread.isAlive(), "main() should return promptly when the server is full");
        }
    }

    @Test
    @Timeout(5)
    void handlesFailureToConnectThroughTheIOExceptionPath() throws InterruptedException {
        // no server listening on port 5000 at all
        startClient();
        clientThread.join(2000);

        assertFalse(clientThread.isAlive());
        assertTrue(output().contains("Could not connect to localhost:5000"));
    }

    @Test
    @Timeout(5)
    void serverDisconnectsBeforeSendingInitialStatus() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            server.acceptClient();

            // server hangs up immediately, before ever sending SERVER_AVAILABLE/SERVER_FULL
            server.closeClientConnection();

            // KNOWN ISSUE: `serverStatus.equalsIgnoreCase(...)` is called without a
            // null check. A null status (from a closed connection) throws an
            // uncaught NullPointerException on the client thread instead of
            // failing gracefully. This test only verifies the thread still
            // terminates (rather than hanging) -- it does not claim there's a
            // clean, user-friendly error message, because there isn't one.
            clientThread.join(2000);
            assertFalse(clientThread.isAlive(), "Client thread should terminate rather than hang");
        }
    }

    @Test
    @Timeout(5)
    void serverSendsAnUnexpectedStatus() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            server.acceptClient();

            server.send("SOMETHING_WEIRD");

            assertTrue(
                    awaitOutputContains("Unexpected response from server.", 2000)
            );

            clientThread.join(2000);

            assertFalse(
                    clientThread.isAlive(),
                    "Client should exit after an unexpected server response"
            );

            assertNull(
                    server.receive(),
                    "Client should close its connection after an unexpected response"
            );
        }
    }

    // ------------------------------------------------------------------
    // Username setup
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void usernameAlreadyTakenCausesAnotherAttempt() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            server.acceptClient();
            server.send("SERVER_AVAILABLE");
            assertTrue(awaitOutputContains("Enter username: ", 2000));

            typeLine("Alice");
            assertEquals("Alice", server.receive());
            server.send("Username already taken");

            assertTrue(awaitOutputContains("Username already taken, Try Again", 2000));
            assertTrue(awaitOutputContains("Enter username: ", 2000));

            typeLine("Alice2");
            assertEquals("Alice2", server.receive());
            server.send("Welcome to the server");
            assertTrue(awaitOutputContains("Welcome to the server", 2000));
        }
    }

    @Test
    @Timeout(5)
    void invalidUsernameCausesAnotherAttempt() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            server.acceptClient();
            server.send("SERVER_AVAILABLE");
            assertTrue(awaitOutputContains("Enter username: ", 2000));

            typeLine("!!");
            assertEquals("!!", server.receive());
            server.send("Invalid username");

            assertTrue(awaitOutputContains("Invalid Username, Try Again", 2000));
            assertTrue(awaitOutputContains("Enter username: ", 2000));

            typeLine("Bob");
            assertEquals("Bob", server.receive());
            server.send("Welcome to the server");
            assertTrue(awaitOutputContains("Welcome to the server", 2000));
        }
    }

    @Test
    @Timeout(5)
    void blankUsernameIsHandled() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            server.acceptClient();
            server.send("SERVER_AVAILABLE");
            assertTrue(awaitOutputContains("Enter username: ", 2000));

            typeLine(""); // user just presses enter
            assertEquals("", server.receive());
            server.send("Invalid username");
            assertTrue(awaitOutputContains("Invalid Username, Try Again", 2000));

            typeLine("Cara");
            assertEquals("Cara", server.receive());
            server.send("Welcome to the server");
            assertTrue(awaitOutputContains("Welcome to the server", 2000));
        }
    }

    @Test
    @Timeout(5)
    void validUsernameIsEventuallyAcceptedAndClientProceedsNormally() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            server.acceptClient();
            server.send("SERVER_AVAILABLE");
            assertTrue(awaitOutputContains("Enter username: ", 2000));

            typeLine("Derek");
            assertEquals("Derek", server.receive());
            server.send("Welcome to the server");
            assertTrue(awaitOutputContains("Welcome to the server", 2000));

            // "proceeds normally" means the keyboard loop is now live and
            // forwards messages to the server
            typeLine("hello from Derek");
            assertEquals("hello from Derek", server.receive());
        }
    }

    @Test
    @Timeout(5)
    void severalBadUsernamesFollowedByAValidOne() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            server.acceptClient();
            server.send("SERVER_AVAILABLE");
            assertTrue(awaitOutputContains("Enter username: ", 2000));

            typeLine("");
            assertEquals("", server.receive());
            server.send("Invalid username");
            assertTrue(awaitOutputContains("Invalid Username, Try Again", 2000));

            typeLine("Eve");
            assertEquals("Eve", server.receive());
            server.send("Username already taken");
            assertTrue(awaitOutputContains("Username already taken, Try Again", 2000));

            typeLine("!!bad!!");
            assertEquals("!!bad!!", server.receive());
            server.send("Invalid username");
            assertTrue(awaitOutputContains("Invalid Username, Try Again", 2000));

            typeLine("EveFinal");
            assertEquals("EveFinal", server.receive());
            server.send("Welcome to the server");
            assertTrue(awaitOutputContains("Welcome to the server", 2000));
        }
    }

    @Test
    @Timeout(5)
    void serverDisconnectsWhileWaitingForUsernameResponse() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            server.acceptClient();
            server.send("SERVER_AVAILABLE");
            assertTrue(awaitOutputContains("Enter username: ", 2000));

            typeLine("Frank");
            assertEquals("Frank", server.receive());

            // server hangs up instead of accepting or rejecting the username
            server.closeClientConnection();

            // KNOWN ISSUE: the while-loop condition calls
            // `response.equalsIgnoreCase(...)` directly on a possibly-null
            // response, throwing an uncaught NullPointerException just like
            // the initial-status case above.
            clientThread.join(2000);
            assertFalse(clientThread.isAlive(), "Client thread should terminate rather than hang");
        }
    }

    // ------------------------------------------------------------------
    // Sending messages
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void clientSendsANormalMessageToTheServer() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            register(server, "Gina");

            typeLine("hello there");
            assertEquals("hello there", server.receive());
        }
    }

    @Test
    @Timeout(5)
    void printWriterFlushesTheExactMessageContentToTheServer() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            register(server, "Hank");

            String message = "Special chars: !?,. and trailing spaces   ";
            typeLine(message);

            // if the PrintWriter weren't auto-flushing, this read would block
            // (and the test would time out) instead of returning promptly
            // with the exact text typed
            assertEquals(message, server.receive());
        }
    }

    @Test
    @Timeout(5)
    void quitStopsTheSendingLoop() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            register(server, "Ivy");

            typeLine("/quit");
            clientThread.join(2000);
            assertFalse(clientThread.isAlive(), "The keyboard loop should stop after /quit");
        }
    }

    @Test
    @Timeout(5)
    void quitClosesTheClientConnection() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            register(server, "Jack");

            typeLine("/quit");

            assertNull(server.receive(), "Server should see the connection close after /quit");
        }
    }

    @Test
    @Timeout(5)
    void scannerAndSocketAreCleanedUpOnQuit() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            register(server, "Karen");

            typeLine("/quit");
            clientThread.join(2000);

            assertFalse(clientThread.isAlive(), "main() should return after cleanup");
            assertTrue(output().contains("Disconnected from server"),
                    "Client should report that it disconnected as part of cleanup");
            assertNull(server.receive(), "Socket should be closed on the server's end too");
        }
    }

    @Test
    @Timeout(5)
    void quitItselfIsNotSentAsAChatMessage() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            register(server, "Leo");

            typeLine("hello first"); // sanity check the channel is live
            assertEquals("hello first", server.receive());

            typeLine("/quit");

            // the server should see the connection close, never a literal "/quit" line
            assertNull(server.receive(), "\"/quit\" should never be forwarded as a chat message");
        }
    }

    @Test
    @Timeout(5)
    void clientCanSendMoreThanOneMessage() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            register(server, "Mona");

            typeLine("first message");
            assertEquals("first message", server.receive());

            typeLine("second message");
            assertEquals("second message", server.receive());

            typeLine("third message");
            assertEquals("third message", server.receive());
        }
    }

    // ------------------------------------------------------------------
    // Receiving messages
    // ------------------------------------------------------------------

    @Test
    @Timeout(5)
    void clientReceivesAndDisplaysIncomingServerMessages() throws Exception {
        try (FakeServer server = new FakeServer(PORT)) {
            startClient();
            register(server, "Nora");

            server.send("[10:00 AM] OtherUser: hi Nora!");

            assertTrue(awaitOutputContains("[10:00 AM] OtherUser: hi Nora!", 2000),
                    "Incoming server message should be printed to the client's output");
        }
    }

    @Test
    @Timeout(15)
    void serverDisconnectAfterLoginIsHandledWithoutKillingTheProcess() throws Exception {
        // ServerListener calls System.exit(0) on a graceful server disconnect.
        // Running that in-process would kill this whole test JVM, so this one
        // test launches ChatClient as a genuine separate OS process.
        String javaBin = System.getProperty("java.home") + File.separator + "bin" + File.separator
                + (System.getProperty("os.name").toLowerCase().contains("win") ? "java.exe" : "java");
        String classpath = System.getProperty("java.class.path");

        try (FakeServer server = new FakeServer(PORT)) {
            Process process = new ProcessBuilder(javaBin, "-cp", classpath, "client.ChatClient")
                    .redirectErrorStream(true)
                    .start();
            try {
                StringBuffer captured = new StringBuffer();
                Thread reader = startDrainingThread(process.getInputStream(), captured);

                server.acceptClient();
                server.send("SERVER_AVAILABLE");
                awaitContains(captured, "Enter username: ", 3000);

                writeLine(process.getOutputStream(), "Oscar");
                assertEquals("Oscar", server.receive());
                server.send("Welcome to the server");
                awaitContains(captured, "Welcome to the server", 3000);

                // server disconnects right after a successful login
                server.closeClientConnection();

                boolean exited = process.waitFor(5, TimeUnit.SECONDS);
                assertTrue(exited, "Client process should exit after the server disconnects gracefully");
                assertEquals(0, process.exitValue(), "ServerListener exits with status 0 on a graceful disconnect");
                assertTrue(captured.toString().contains("Server disconnected."),
                        "Client should report that the server disconnected");

                reader.join(1000);
            } finally {
                process.destroyForcibly();
            }
        }
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private static class FakeServer implements Closeable {
        final ServerSocket serverSocket;
        Socket accepted;
        PrintWriter out;
        BufferedReader in;

        FakeServer(int port) throws IOException {
            serverSocket = new ServerSocket(port);
        }

        void acceptClient() throws IOException {
            accepted = serverSocket.accept();
            accepted.setSoTimeout(2000);
            out = new PrintWriter(accepted.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(accepted.getInputStream()));
        }

        void send(String line) {
            out.println(line);
        }

        String receive() throws IOException {
            return in.readLine();
        }

        void closeClientConnection() throws IOException {
            if (accepted != null) accepted.close();
        }

        @Override
        public void close() throws IOException {
            if (accepted != null && !accepted.isClosed()) accepted.close();
            if (!serverSocket.isClosed()) serverSocket.close();
        }
    }

    private void register(FakeServer server, String username) throws Exception {
        server.acceptClient();
        server.send("SERVER_AVAILABLE");
        assertTrue(awaitOutputContains("Enter username: ", 2000));
        typeLine(username);
        assertEquals(username, server.receive());
        server.send("Welcome to the server");
        assertTrue(awaitOutputContains("Welcome to the server", 2000));
    }

    private void startClient() {
        clientThread = new Thread(() -> ChatClient.main(new String[0]), "chat-client-test-thread");
        clientThread.setDaemon(true);
        clientThread.start();
    }

    private void typeLine(String line) throws IOException {
        stdinPipe.write((line + System.lineSeparator()).getBytes());
        stdinPipe.flush();
    }

    private String output() {
        return capturedOut.toString();
    }

    private boolean awaitOutputContains(String needle, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (output().contains(needle)) return true;
            Thread.sleep(30);
        }
        return output().contains(needle);
    }

    private Thread startDrainingThread(InputStream in, StringBuffer sink) {
        Thread t = new Thread(() -> {
            try (InputStreamReader reader = new InputStreamReader(in)) {
                int character;

                while ((character = reader.read()) != -1) {
                    sink.append((char) character);
                }
            } catch (IOException ignored) {
            }
        });

        t.setDaemon(true);
        t.start();
        return t;
    }

    private void awaitContains(StringBuffer sink, String needle, long timeoutMillis) throws InterruptedException {
        long deadline = System.currentTimeMillis() + timeoutMillis;
        while (System.currentTimeMillis() < deadline) {
            if (sink.toString().contains(needle)) return;
            Thread.sleep(30);
        }
        assertTrue(sink.toString().contains(needle), "Timed out waiting for: " + needle + " -- got: " + sink);
    }

    private void writeLine(OutputStream out, String line) throws IOException {
        out.write((line + System.lineSeparator()).getBytes());
        out.flush();
    }
}