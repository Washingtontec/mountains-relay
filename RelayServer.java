import com.google.auth.oauth2.GoogleCredentials;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.DocumentSnapshot;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.cloud.FirestoreClient;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArraySet;

public class RelayServer extends WebSocketServer {

    private final Set<WebSocket> clientes = new CopyOnWriteArraySet<>();
    private volatile String cadeiaAtual = null;
    private Firestore db;

    public RelayServer(int porta) {
        super(new InetSocketAddress(porta));
    }

    public static void main(String[] args) throws Exception {
        String portaEnv = System.getenv("PORT");
        int porta = portaEnv != null ? Integer.parseInt(portaEnv) : 5000;

        RelayServer servidor = new RelayServer(porta);
        servidor.iniciarFirebase();
        servidor.carregarCadeiaDoFirestore();
        servidor.start();
        System.out.println("Relay (WebSocket) ouvindo na porta " + porta);
    }

    private void iniciarFirebase() throws Exception {
        String credencialJson = System.getenv("FIREBASE_CREDENCIAL");
        if (credencialJson == null) {
            System.out.println("AVISO: FIREBASE_CREDENCIAL não configurada, rodando sem persistência.");
            return;
        }
        InputStream in = new ByteArrayInputStream(credencialJson.getBytes(StandardCharsets.UTF_8));
        GoogleCredentials cred = GoogleCredentials.fromStream(in);
        FirebaseOptions options = FirebaseOptions.builder().setCredentials(cred).build();
        FirebaseApp.initializeApp(options);
        db = FirestoreClient.getFirestore();
        System.out.println("Firebase iniciado.");
    }

    private void carregarCadeiaDoFirestore() {
        if (db == null) return;
        try {
            DocumentSnapshot doc = db.collection("mountains").document("cadeia").get().get();
            if (doc.exists()) {
                cadeiaAtual = (String) doc.get("json");
                System.out.println("Cadeia carregada do Firestore, altura " + contarBlocos(cadeiaAtual));
            } else {
                System.out.println("Nenhuma cadeia salva ainda no Firestore.");
            }
        } catch (Exception e) {
            System.out.println("Erro ao carregar do Firestore: " + e.getMessage());
        }
    }

    private void salvarCadeiaNoFirestore(String json) {
        if (db == null) return;
        try {
            Map<String, Object> dados = new HashMap<>();
            dados.put("json", json);
            db.collection("mountains").document("cadeia").set(dados);
        } catch (Exception e) {
            System.out.println("Erro ao salvar no Firestore: " + e.getMessage());
        }
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        clientes.add(conn);
        System.out.println("Conectou: " + conn.getRemoteSocketAddress());
        if (cadeiaAtual != null) {
            conn.send("CADEIA:" + cadeiaAtual);
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
                salvarCadeiaNoFirestore(json);
                System.out.println("Cadeia guardada (memória + Firestore), altura " + novaAltura);
            }
        } else if (message.equals("PEDIR_CADEIA") && cadeiaAtual != null) {
            conn.send("CADEIA:" + cadeiaAtual);
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
