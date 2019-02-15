package bio;

import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

/**
 * <p></p>
 *
 * @author PengCheng
 * @date 2019/2/5
 */
public class Test {

    public static void main(String[] args) throws InterruptedException {
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Server.start();
                }catch (Exception e){
                    e.printStackTrace();
                }
            }
        }).start();

        Thread.sleep(100);
        final char[] op = {'+','-','*','/'};
        final Random random = new Random(System.currentTimeMillis());
        new Thread(new Runnable() {
            @Override
            public void run() {
                while (true){
                    String expression = random.nextInt(10)+""+op[random.nextInt(4)] + random.nextInt(10);
                    Client.send(expression);
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }).start();
    }
}
