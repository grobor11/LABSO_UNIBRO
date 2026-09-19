package common;

import java.io.Serializable;
import java.util.Objects;

public class PeerInfo implements Serializable {
    private String ip;
    private int port;

    public PeerInfo(String ip, int port) {
        this.ip = ip;
        this.port = port;
    }

    public String getIp() { 
        return ip; 
    }
    
    public int getPort() { 
        return port; 
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerInfo peerInfo = (PeerInfo) o;
        return port == peerInfo.port && Objects.equals(ip, peerInfo.ip);
    }

    @Override
    public int hashCode() {
        return Objects.hash(ip, port);
    }
    
    @Override
    public String toString() {
        return ip + ":" + port;
    }
}