package bio;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * <p></p>
 *
 * @author PengCheng
 * @date 2019/2/5
 */
public class Server {

    private static ServerSocket serverSocket;

    public static void start(){
        start(7777);
    }

    private synchronized static void start(int port) {
        if (serverSocket != null){
            return;
        }
        try{
            serverSocket = new ServerSocket(port);
            while (true){
                Socket socket = serverSocket.accept();
                new Thread(new ServerHandler(socket)).start();
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (serverSocket != null){
                    serverSocket.close();
                    serverSocket = null;
                }
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
