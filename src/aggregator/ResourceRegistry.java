package aggregator;

import common.PeerInfo;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
/*
  Gestisce la tabella centrale di tutte le rilevazioni disponibili sulla rete.
  È la risorsa condivisa del server: i metodi sono 'synchronized' per garantire 
  la mutua esclusione richiesta dalle specifiche del progetto.
 */
public class ResourceRegistry {
    private Map<String, Set<PeerInfo>> registry;
    public ResourceRegistry() {
    this.registry = new HashMap<>(); 
}

 //Aggiunge un nodo ai possessori di una determinata risorsa.
public synchronized void addResource(String resourceName, PeerInfo peer){
    if(!registry.containsKey(resourceName)){
        registry.put(resourceName, new HashSet<>());
    }

    registry.get(resourceName).add(peer); 
}
/*
 Rimuove un nodo dall'elenco dei possessori di una specifica risorsa.
 Se, dopo la rimozione, nessun altro nodo possiede la risorsa, elimina l'intera entry.
 */
public synchronized void removeResource(String resourceName, PeerInfo peer){
     if(registry.containsKey(resourceName)){
        registry.get(resourceName).remove(peer);
        if (registry.get(resourceName).isEmpty()) {
            registry.remove(resourceName);
        }
    }
} 
/*
Restituisce l'elenco dei nodi che possiedono una specifica rilevazione.
Restituisce una "copia difensiva" (un nuovo HashSet) per impedire che il thread 
chiamante possa modificare inavvertitamente i dati originali del server.
 */
public synchronized Set<PeerInfo> getPeersForResource(String resourceName){
    if (registry.containsKey(resourceName)) {
        return new HashSet<>(registry.get(resourceName));
    }else{
        return new HashSet<>();
    }
}

//Restituisce l'intera mappa delle risorse, utile per il comando 'listdata'.
public synchronized Map<String, Set<PeerInfo>> getGlobalList() {
    Map<String, Set<PeerInfo>> copy = new HashMap<>();
    for (Map.Entry<String, Set<PeerInfo>> entry : registry.entrySet()) {
        copy.put(entry.getKey(), new HashSet<>(entry.getValue()));
        }
        return copy;  
    }
}



