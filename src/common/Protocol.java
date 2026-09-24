package common;
/*
  Definisce il protocollo di comunicazione dell'architettura distribuita.
  Centralizza i messaggi scambiati tra Nodi Sensore (Client) e Aggregatore (Server)
  e tra i Nodi Sensore stessi, garantendo un vocabolario comune sui socket.
 */
public class Protocol {
    // Comandi di gestione del nodo (Client -> Server)
    public static final String REGISTER_NODE = "REGISTER";
    public static final String UPDATE_RESOURCES = "ADD_RESOURCE";
    public static final String UNREGISTER_NODE = "DISCONNECT";
    public static final String REQUEST_GLOBAL_LIST = "LISTDATA_REMOTE";
    
    // Comandi per il protocollo robusto di download
    public static final String REQUEST_DOWNLOAD = "REQUEST_DOWNLOAD";
    public static final String REPORT_DOWNLOAD_FAILED = "DOWNLOAD_FAIL";
    public static final String RELEASE_TOKEN = "RELEASE_TOKEN";
    
    // Comandi Peer-to-Peer (Nodo -> Nodo)
    public static final String PEER_DOWNLOAD_REQUEST = "DOWNLOAD";
    
    // Risposte standard
    public static final String SUCCESS = "OK";
    public static final String TOKEN_GRANTED = "GRANT";
    public static final String ERROR = "ERROR";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String PEER_DATA = "DATA";
    public static final String LIST_END = "END";
    
}
