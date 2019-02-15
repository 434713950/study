package bio;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;

/**
 * <p></p>
 *
 * @author PengCheng
 * @date 2019/2/5
 */
public class Client {

    public static void send(String expression){
        send(7777,expression);
    }

    private static void send(int port, String expression) {
        Socket socket = null;
        BufferedReader in = null;

        PrintWriter out = null;
        try {
            socket = new Socket("127.0.0.1",port);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(),true);
            out.println(expression);
            System.out.println("result :" + in.readLine());
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try {
                if (in != null){
                    in.close();
                }
                if (out!=null){
                    out.close();
                }
                if (socket!=null){
                    socket.close();
                }

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
