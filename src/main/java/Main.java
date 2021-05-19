import lombok.extern.log4j.Log4j;

import java.io.IOException;


@Log4j
public class Main {
    public static void main(String[] args) {
        try {
            new Server(9909);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


}
