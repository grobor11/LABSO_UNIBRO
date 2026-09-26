package client;

import common.Protocol;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

/*
 * Gestisce il download di una risorsa da un nodo remoto.
 *
 * Il downloader chiede all'Aggregator quale peer contattare, prova a
 * comunicare direttamente con quel nodo e, in caso di errore, richiede
 * un peer alternativo. Quando il download riesce, salva la risorsa
 * nell'archivio locale e aggiorna il registro dell'Aggregator.
 */
public class RobustDownloader {

    // Archivio locale in cui salvare le risorse scaricate.
    private final LocalStorage localStorage;

    // Stream usati per richiedere peer e notificare l'esito all'Aggregator.
    private final BufferedReader aggregatorIn;
    private final PrintWriter aggregatorOut;

    // Timeout applicato alla connessione e alla lettura dal peer remoto.
    private static final int SOCKET_TIMEOUT_MS = 3000; // 3 secondi di timeout per la connessione P2P

    // Riceve l'archivio locale e gli stream della connessione con l'Aggregator.
    public RobustDownloader(LocalStorage localStorage, BufferedReader aggregatorIn, PrintWriter aggregatorOut) {
        this.localStorage = localStorage;
        this.aggregatorIn = aggregatorIn;
        this.aggregatorOut = aggregatorOut;
    }

    // Esegue il download con una logica di retry e fallback su nodi alternativi.
    public boolean downloadResource(String resourceName) {
        System.out.println("[Downloader] Inizio procedura di download per: " + resourceName);

        while (true) {
            // Richiede all'Aggregator un token e il peer che possiede la risorsa.
            aggregatorOut.println(Protocol.REQUEST_DOWNLOAD + " " + resourceName);
            String response;
            try {
                response = aggregatorIn.readLine();
            } catch (IOException e) {
                System.err.println("[Downloader] Errore di comunicazione con l'Aggregator: " + e.getMessage());
                return false;
            }

            if (response == null || response.startsWith(Protocol.NOT_FOUND)) {
                System.out.println("[Downloader] Risorsa '" + resourceName + "' non disponibile sulla rete.");
                return false;
            }

            // Formato atteso: "GRANT <tokenId> <peerIp> <peerPort>".
            String[] responseTokens = response.split("\\s+");
            if (responseTokens.length < 4 || !responseTokens[0].equals(Protocol.TOKEN_GRANTED)) {
                System.err.println("[Downloader] Risposta inattesa dall'Aggregator: " + response);
                return false;
            }

            String tokenId = responseTokens[1];
            String peerIp = responseTokens[2];
            int peerPort;
            try {
                peerPort = Integer.parseInt(responseTokens[3]);
            } catch (NumberFormatException e) {
                System.err.println("[Downloader] Porta del peer non valida: " + responseTokens[3]);
                releaseToken(tokenId, resourceName);
                return false;
            }

            System.out.println("[Downloader] Token ottenuto (" + tokenId + "). Connessione a " + peerIp + ":" + peerPort + "...");

            try {
                // Mantiene il token durante l'intero tentativo di accesso al peer.
                String resourceContent = attemptPeerDownload(peerIp, peerPort, resourceName);

                if (resourceContent != null) {
                    // Salva la risorsa e aggiorna l'Aggregator prima di liberare il token.
                    localStorage.addData(resourceName, resourceContent);
                    System.out.println("[Downloader] Rilevazione '" + resourceName + "' salvata con successo.");
                        notifyAggregator(Protocol.REPORT_DOWNLOAD_SUCCESS + " " + resourceName + " "
                            + peerIp + " " + peerPort);
                    notifyAggregator(Protocol.UPDATE_RESOURCES + " " + resourceName);
                    return true;
                }

                // Rimuove il peer non disponibile e richiede un'alternativa al prossimo ciclo.
                System.out.println("[Downloader] Fallimento con " + peerIp + ":" + peerPort + ". Notifico l'Aggregator...");
                notifyAggregator(Protocol.REPORT_DOWNLOAD_FAILED + " " + resourceName + " " + peerIp + " " + peerPort);
                System.out.println("[Downloader] Richiedo un peer alternativo...");
            } finally {
                // Garantisce il rilascio anche se il salvataggio o la notifica falliscono.
                releaseToken(tokenId, resourceName);
            }
        }
    }

    // Restituisce il token all'Aggregator dopo la conclusione del tentativo.
    private void releaseToken(String tokenId, String resourceName) {
        aggregatorOut.println(Protocol.RELEASE_TOKEN + " " + tokenId + " " + resourceName);
        try {
            aggregatorIn.readLine(); // Consuma la conferma dell'Aggregator.
        } catch (IOException e) {
            System.err.println("[Downloader] Errore nel rilascio del token: " + e.getMessage());
        }
    }

    // Invia un comando all'Aggregator e consuma la risposta associata.
    private void notifyAggregator(String command) {
        aggregatorOut.println(command);
        try {
            aggregatorIn.readLine();
        } catch (IOException ignored) {}
    }

    // Contatta il peer via TCP e restituisce il contenuto o null in caso di errore.
    private String attemptPeerDownload(String ip, int port, String resourceName) {
        try (Socket peerSocket = new Socket()) {
            // Usa un timeout per evitare attese infinite se il peer non risponde.
            peerSocket.connect(new InetSocketAddress(ip, port), SOCKET_TIMEOUT_MS);
            peerSocket.setSoTimeout(SOCKET_TIMEOUT_MS);

            PrintWriter peerOut = new PrintWriter(peerSocket.getOutputStream(), true);
            BufferedReader peerIn = new BufferedReader(new InputStreamReader(peerSocket.getInputStream()));

            // Invia la richiesta di download al server del peer.
            peerOut.println(Protocol.PEER_DOWNLOAD_REQUEST + " " + resourceName);

            // Risposta attesa: "DATA <contenuto>" oppure "ERROR".
            String response = peerIn.readLine();
            String dataPrefix = Protocol.PEER_DATA + " ";
            if (response != null && response.startsWith(dataPrefix)) {
                // Estrae il contenuto saltando il prefisso "DATA ".
                return response.substring(dataPrefix.length());
            }
        } catch (IOException e) {
            System.err.println("[Downloader] Impossibile contattare il peer (" + ip + ":" + port + "): " + e.getMessage());
        }
        return null;
    }
}