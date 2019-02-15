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
public class ServerHandler implements Runnable {

    private Socket socket;

    public ServerHandler(Socket socket) {
        this.socket = socket;
    }

    @Override
    public void run() {
        BufferedReader in = null;
        PrintWriter out = null;
        try{
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(),true);
            String expression;
            String result;
            while (true){
                if ((expression = in.readLine()) == null){
                    break;
                }
                System.out.println("server accept info: "+ expression);

                result = Calculator.cal(expression);
                out.println(result);
            }
        }catch (Exception e){
            e.printStackTrace();
        }finally {
            try {
                if (in != null){
                    in.close();
                    in = null;
                }
                if (out!=null){
                    out.close();
                    out = null;
                }
                if (socket!=null){
                    socket.close();
                    socket = null;
                }

            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }
}
