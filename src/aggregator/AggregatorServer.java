package aggregator;

import java.net.ServerSocket;
import java.net.Socket;
import java.io.IOException;

/*
 * Rappresenta il server di rete dell'Aggregator (il "centralinista").
 * Questa classe viene eseguita in un thread separato in background.
 * Il suo unico scopo è rimanere costantemente in ascolto di nuove connessioni,
 * per poi delegare la comunicazione vera e propria a thread dedicati (ClientHandler).
 */

public class AggregatorServer implements Runnable {
    private int port;

    // Riferimenti alle strutture dati condivise tra i thread: registri risorse e log dei download.
    private ResourceRegistry registry;
    private DownloadLogManager logManager;
    
    // Il costruttore riceve i parametri dal main e li salva nelle variabili di istanza.
    public AggregatorServer(int port, ResourceRegistry registry, DownloadLogManager logManager) {

        this.port = port;
        this.registry = registry;
        this.logManager = logManager;
    }
    
    @Override
    public void run() {

        // Costrutto "try-with-resources": dichiariamo il ServerSocket qui dentro
        // in modo che venga chiuso automaticamente e in modo sicuro quando il blocco termina,
        // anche in caso di eccezioni.
        try (ServerSocket serverSocket = new ServerSocket(port)) {
            System.out.println("Server in ascolto sulla porta: " + port);

            // Ciclo infinito del server: aspetta connessioni all'infinito
            while (true) {

                // Il metodo accept() è BLOCCANTE: il thread rimane fermo qui finché
                // un  nodo remoto non si connette effettivamente a questa porta.
                Socket clientSocket = serverSocket.accept();
                System.out.println("Nuovo nodo connessione accettato: " + clientSocket.getInetAddress());

                // Appena un nodo si connette, creiamo un nuovo thread per gestire la comunicazione con quel nodo,
                // e gli passiamo il socket appena aperto e le memorie condivise (registry e logManager)
                ClientHandler handler = new ClientHandler(clientSocket, registry, logManager);

                // Avviamo il gestore del nodo remoto in un thread nuovo ("fire and forget").
                // Così questo ciclo while può ricominciare subito ad aspettare il prossimo nodo.
                new Thread(handler).start();
            }
        } catch (IOException e) {
            System.out.println("Errore nel server di rete: " + e.getMessage());
        }
    }
}
