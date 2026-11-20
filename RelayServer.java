import java.io.*;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;

public class RelayServer {
    private static final List<PrintWriter> clientes = new CopyOnWriteArrayList<>();

    public static void main(String[] args) throws IOException {
        String portaEnv = System.getenv("PORT");
        int porta = portaEnv != null ? Integer.parseInt(portaEnv) : 5000;
        ServerSocket servidor = new ServerSocket(porta);
        System.out.println("Relay ouvindo na porta " + porta);

        while (true) {
            Socket s = servidor.accept();
            System.out.println("Conectou: " + s.getInetAddress());
            new Thread(() -> atenderCliente(s)).start();
        }
    }

    private static void atenderCliente(Socket s) {
        PrintWriter out = null;
        try {
            out = new PrintWriter(new OutputStreamWriter(s.getOutputStream(), "UTF-8"), true);
            clientes.add(out);
            BufferedReader in = new BufferedReader(new InputStreamReader(s.getInputStream(), "UTF-8"));

            String linha;
            while ((linha = in.readLine()) != null) {
                System.out.println("Recebido: " + linha);
                retransmitir(linha, out);
            }
        } catch (IOException e) {
            System.out.println("Cliente caiu: " + e.getMessage());
        } finally {
            if (out != null) clientes.remove(out);
            try { s.close(); } catch (IOException ignorar) {}
        }
    }

    private static void retransmitir(String msg, PrintWriter remetente) {
        for (PrintWriter cliente : clientes) {
            if (cliente != remetente) {
                cliente.println(msg);
            }
        }
    }
}
