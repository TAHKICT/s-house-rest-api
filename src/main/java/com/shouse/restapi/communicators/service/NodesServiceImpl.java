package com.shouse.restapi.communicators.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import shouse.core.node.NodeInfo;
import com.shouse.restapi.service.Messages;
import com.shouse.restapi.service.node.NodeStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;
import shouse.core.communication.Communicator;
import shouse.core.communication.Packet;
import shouse.core.controller.NodeContainer;
import shouse.core.node.Node;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentLinkedQueue;

import static java.util.stream.Collectors.toMap;

public class NodesServiceImpl implements NodesService, Communicator {

    private final Logger log = LoggerFactory.getLogger(this.getClass());

    private RestTemplate restTemplate;
    private NodeContainer nodesStorage;
    private static Map<Integer, NodeInfoExtended> nodeInfoMap;

    private ConcurrentLinkedQueue<Packet> packets = new ConcurrentLinkedQueue<>();

    @Autowired
    public NodesServiceImpl(RestTemplate restTemplate, NodeContainer nodesStorage) {
        this.restTemplate = restTemplate;
        this.nodesStorage = nodesStorage;
        this.nodeInfoMap = new LinkedHashMap<>();
    }

    @Override
    public void handleNode(Packet packet) {
        packets.add(packet);
    }

    //Probably remove
    @Override
    public String handleNode(String nodeId, String value) {
        if(nodeId == null)
            return Messages.nodeIdFormatIsNull;

        if(!validNodeId(nodeId))
            return Messages.nodeIdFormatIsNotValid;

        int id = Integer.valueOf(nodeId);

        if (!nodeInfoMap.containsKey(id))
            return Messages.nodeNotFound;
        else if(nodeInfoMap.get(id).getNodeStatus().getStatusCode() == NodeStatus.SWITCHED_OFF.getStatusCode())
            return Messages.nodeIsNotActive;

        nodeInfoMap.get(id).setValue(value);
        // webApplicationService.sendNodeChangeRequestToClient(id,value);

        return Messages.nodeHandledSuccessfully;
    }

    //TODO: refactor in a way to use NodeContainer to get latest data
    public Map<Integer, NodeInfoExtended> getNodesMap() {
        return nodeInfoMap;
    }

    @Override
    public String handleAliveRequestFromNode(String nodeId, String ipAddress) {
        if(nodeId == null)
            return Messages.nodeIdFormatIsNull;

        if(!validNodeId(nodeId))
            return Messages.nodeIdFormatIsNotValid;

        if(!validIP(ipAddress))
            return String.format(Messages.nodeIPAddressIsNotValid, ipAddress);

        int id = Integer.valueOf(nodeId);

        if(nodeInfoMap.containsKey(id)) {
            nodeInfoMap.get(id).setIpAddress(ipAddress);
            nodeInfoMap.get(id).setNodeStatus(NodeStatus.ACTIVE);
            return Messages.nodeRegistered;
        } else
            return Messages.nodeNotFound;
    }

    private static boolean validNodeId(String nodeId){
        try {
            Integer.valueOf(nodeId);
        }catch (NumberFormatException e){
            return false;
        }
        return true;
    }

    private static boolean validIP (String ip) {
        try {
            if ( ip == null || ip.isEmpty() ) {
                return false;
            }

            String[] parts = ip.split( "\\." );
            if ( parts.length != 4 ) {
                return false;
            }

            for ( String s : parts ) {
                int i = Integer.parseInt( s );
                if ( (i < 0) || (i > 255) ) {
                    return false;
                }
            }
            if ( ip.endsWith(".") ) {
                return false;
            }

            return true;
        } catch (NumberFormatException nfe) {
            return false;
        }
    }

    /**
     * When system starts, nodes map should be token from the storage.
     */
    @PostConstruct
    public void nodeInfoMapSynchronization(){
        nodeInfoMap = nodesStorage.getAllNodes().stream().map(Node::getNodeInfo).collect(
                toMap(NodeInfo::getId, NodeInfo -> new NodeInfoExtended(NodeInfo.getId(),NodeInfo.getNodeTypeName(),NodeInfo.getNodeLocation(),NodeInfo.getDescription()))
        );
    }

    @Override
    public void sendPacket(Packet packet) {
        String ip = getNodeIp(packet.getNodeId());
        UriComponentsBuilder url = UriComponentsBuilder.fromHttpUrl("http://" + ip + "/command-from-server")
                .queryParams(getDataFromPacket(packet));

        log.info("sendRequest. url: " + url.toUriString());

        restTemplate.getForEntity(url.toUriString(), String.class);

//        String response = responseEntity.getBody().toString();
//        log.info("sendRequest. " +
//                "nodeId:" + node.getId() + ", request:" + request + ". " +
//                "Response: " + response);
//
//        return response;
    }

    private MultiValueMap<String, String> getDataFromPacket(Packet packet) {
        return new LinkedMultiValueMap<>(packet.getData().entrySet().stream()
                .collect(toMap(Map.Entry::getKey, entry -> {
                    List<String> vals = new ArrayList<>();
                    vals.add(entry.getValue());
                    return vals;
                })));
    }

    private String getNodeIp(int nodeId) {
        NodeInfoExtended nodeInfo = nodeInfoMap.get(nodeId);
        return nodeInfo.getIpAddress();
    }

    @Override
    public Packet receivePacket() {
        return packets.poll();
    }

    @Override
    public boolean hasNewPacket() {
        //because isEmpty() is not constant time operation, should be refactored somehow
        return !packets.isEmpty();
    }
}