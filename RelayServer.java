import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;

import java.net.InetSocketAddress;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class RelayServer extends WebSocketServer {

    private final Set<WebSocket> clientes = new CopyOnWriteArraySet<>();
    private volatile String cadeiaAtual = null; // JSON da cadeia mais longa já vista

    public RelayServer(int porta) {
        super(new InetSocketAddress(porta));
    }

    public static void main(String[] args) {
        String portaEnv = System.getenv("PORT");
        int porta = portaEnv != null ? Integer.parseInt(portaEnv) : 5000;
        RelayServer servidor = new RelayServer(porta);
        servidor.start();
        System.out.println("Relay (WebSocket) ouvindo na porta " + porta);
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clientes.add(conn);
        System.out.println("Conectou: " + conn.getRemoteSocketAddress());
        if (cadeiaAtual != null) {
            conn.send("CADEIA:" + cadeiaAtual); // já manda a cadeia conhecida, sem precisar pedir
        }
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        clientes.remove(conn);
        System.out.println("Desconectou: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        System.out.println("Recebido: " + message.substring(0, Math.min(80, message.length())));

        if (message.startsWith("CADEIA:")) {
            String json = message.substring(7);
            int novaAltura = contarBlocos(json);
            int atualAltura = cadeiaAtual == null ? 0 : contarBlocos(cadeiaAtual);
            if (novaAltura > atualAltura) {
                cadeiaAtual = json;
                System.out.println("Cadeia guardada em memória, altura " + novaAltura);
            }
        } else if (message.equals("PEDIR_CADEIA") && cadeiaAtual != null) {
            conn.send("CADEIA:" + cadeiaAtual); // responde na hora, sem depender de ninguém
        }

        for (WebSocket cliente : clientes) {
            if (cliente != conn) cliente.send(message);
        }
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        System.out.println("Erro: " + ex.getMessage());
    }

    @Override
    public void onStart() {
        System.out.println("Servidor iniciado.");
    }

    private int contarBlocos(String json) {
        try {
            return new JSONArray(json).length();
        } catch (Exception e) {
            return 0;
        }
    }
}
