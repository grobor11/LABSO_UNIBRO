package common;

import java.io.Serializable;
import java.util.Objects;
/*
  Rappresenta le coordinate di rete di un singolo Nodo Sensore.
  Implementa Serializable per permettere la conversione dell'oggetto in un flusso di byte,
  operazione obbligatoria per poterlo spedire e ricevere tramite Socket sulla rete.
 */
public class PeerInfo implements Serializable {
    private String ip;
    private int port;

    public PeerInfo(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }
    // Restituisce l'indirizzo IP del nodo.
    public String getIp() { 
        return ip; 
    }
    // Restituisce la porta su cui il nodo è in ascolto.
    public int getPort() { 
        return port; 
    }
    /* 
    Sovrascritto per definire l'identità logica del nodo.
    Permette alle collezioni (come il Set nel ResourceRegistry) di capire 
    che due oggetti PeerInfo diversi sono in realtà lo stesso nodo 
    se condividono lo stesso indirizzo IP e la stessa porta.
    */
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerInfo peerInfo = (PeerInfo) o;
        return port == peerInfo.port && Objects.equals(ip, peerInfo.ip);
    }
    /* 
    Genera un codice hash basato su IP e porta, indispensabile per il 
    corretto funzionamento delle strutture dati basate su hash (HashSet o HashMap).
    */
    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }
    /*
     Fornisce una rappresentazione testuale pulita.
     Utile per generare l'output formattato richiesto dai comandi "listdata" e "log".
     */
    @Override
    public String toString() {
        return ip + ":" + port;
    }
}
