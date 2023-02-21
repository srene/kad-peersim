package peersim.kademlia.das;

/**
 * A Kademlia implementation for PeerSim extending the EDProtocol class.<br>
 * See the Kademlia bibliografy for more information about the protocol.
 *
 * @author Daniele Furlan, Maurizio Bonani
 * @version 1.0
 */
import java.math.BigInteger;
import java.util.TreeMap;
import java.util.logging.Logger;
import peersim.config.Configuration;
import peersim.core.CommonState;
import peersim.core.Network;
import peersim.core.Node;
import peersim.edsim.EDProtocol;
import peersim.kademlia.KademliaCommonConfig;
import peersim.kademlia.KademliaProtocol;
import peersim.kademlia.KeyValueStore;
import peersim.kademlia.Message;
import peersim.kademlia.RoutingTable;
import peersim.kademlia.SimpleEvent;
import peersim.transport.UnreliableTransport;

public class DASProtocol implements Cloneable, EDProtocol {

  private static final String PAR_TRANSPORT = "transport";
  private static final String PAR_DASPROTOCOL = "dasprotocol";
  private static final String PAR_KADEMLIA = "kademlia";

  private static String prefix = null;
  private UnreliableTransport transport;
  private int tid;
  private int kademliaId;
  // private int kademliaId;
  /** trace message sent for timeout purpose */
  private TreeMap<Long, Long> sentMsg;

  private KademliaProtocol kadProtocol;
  /** allow to call the service initializer only once */
  private static boolean _ALREADY_INSTALLED = false;

  private Logger logger;

  private BigInteger builderAddress;

  private boolean isBuilder;

  private KeyValueStore kv;

  /**
   * Replicate this object by returning an identical copy.<br>
   * It is called by the initializer and do not fill any particular field.
   *
   * @return Object
   */
  public Object clone() {
    DASProtocol dolly = new DASProtocol(DASProtocol.prefix);
    return dolly;
  }

  /**
   * Used only by the initializer when creating the prototype. Every other instance call CLONE to
   * create the new object.
   *
   * @param prefix String
   */
  public DASProtocol(String prefix) {

    DASProtocol.prefix = prefix;
    _init();
    sentMsg = new TreeMap<Long, Long>();
    tid = Configuration.getPid(prefix + "." + PAR_TRANSPORT);
    // System.out.println("New DASProtocol");

    kademliaId = Configuration.getPid(prefix + "." + PAR_KADEMLIA);
    kv = new KeyValueStore();
  }

  /**
   * This procedure is called only once and allow to inizialize the internal state of
   * KademliaProtocol. Every node shares the same configuration, so it is sufficient to call this
   * routine once.
   */
  private void _init() {
    // execute once
    if (_ALREADY_INSTALLED) return;

    _ALREADY_INSTALLED = true;
  }

  /**
   * manage the peersim receiving of the events
   *
   * @param myNode Node
   * @param myPid int
   * @param event Object
   */
  public void processEvent(Node myNode, int myPid, Object event) {

    // Parse message content Activate the correct event manager fot the particular event
    // this.protocolId = myPid;

    Message m;
    logger.warning("Message received");

    SimpleEvent s = (SimpleEvent) event;
    if (s instanceof Message) {
      m = (Message) event;
      m.dst = this.getKademliaProtocol().getKademliaNode();
    }

    switch (((SimpleEvent) event).getType()) {
      case Message.MSG_INIT_NEW_BLOCK:
        m = (Message) event;
        handleInitNewBlock(m, myPid);
        break;
      case Message.MSG_GET:
        m = (Message) event;
        logger.warning("Get Sample " + m.body);
        break;
    }
  }

  public void setKademliaProtocol(KademliaProtocol prot) {
    this.kadProtocol = prot;
    this.logger = prot.getLogger();
  }

  public KademliaProtocol getKademliaProtocol() {
    // System.out.println(
    //    "getKademliaProtocol " + kademliaId + " " + (Network.prototype).getProtocol(kademliaId));
    // return (KademliaProtocol) (Network.prototype).getProtocol(kademliaId);
    return kadProtocol;
  }

  public boolean isBuilder() {
    return this.isBuilder;
  }

  public void setBuilder(boolean isBuilder) {
    this.isBuilder = isBuilder;
  }

  public void setBuilderAddress(BigInteger address) {
    this.builderAddress = address;
  }

  public BigInteger getBuilderAddress() {
    return this.builderAddress;
  }

  /**
   * Start a topic query opearation.<br>
   *
   * @param m Message received (contains the node to find)
   * @param myPid the sender Pid
   */
  private void handleInitNewBlock(Message m, int myPid) {
    // logger.warning(" handleInitNewBlock");
    Block b = (Block) m.body;

    if (isBuilder()) {
      logger.warning("Building block");

      while (b.hasNext()) {
        // create a put request
        Sample s = b.next();

        // logger.warning("New sample:" + s.getId());
        kv.add(s.getId(), s);
      }
    } else {
      BigInteger radius = b.computeRegionRadius(KademliaCommonConfig.NUM_SAMPLE_COPIES_PER_PEER);
      while (b.hasNext()) {
        Sample s = b.next();

        if (s.isInRegion(getKademliaId(), radius)) {
          logger.warning("Sending get message");
          Message msg = generateGetMessage(s);
          msg.src = this.getKademliaProtocol().getKademliaNode();
          msg.dst =
              this.getKademliaProtocol()
                  .nodeIdtoNode(builderAddress)
                  .getKademliaProtocol()
                  .getKademliaNode();
          sendMessage(msg, builderAddress, myPid);
        }
      }
    }
  }

  // public void refreshBucket(TicketTable rou, BigInteger node, int distance) {
  public void refreshBucket(RoutingTable rou, int distance) {}

  /**
   * send a message with current transport layer and starting the timeout timer (wich is an event)
   * if the message is a request
   *
   * @param m the message to send
   * @param destId the Id of the destination node
   * @param myPid the sender Pid
   */
  private void sendMessage(Message m, BigInteger destId, int myPid) {

    // int destpid;
    assert m.src != null;
    assert m.dst != null;

    Node src = this.kadProtocol.getNode();
    Node dest = this.kadProtocol.nodeIdtoNode(destId);

    // destpid = dest.getKademliaProtocol().getProtocolID();

    transport = (UnreliableTransport) (Network.prototype).getProtocol(tid);
    transport.send(src, dest, m, myPid);
  }

  // ______________________________________________________________________________________________
  /**
   * generates a GET message for t1 key.
   *
   * @return Message
   */
  private Message generateGetMessage(Sample s) {

    Message m = new Message(Message.MSG_GET, s);
    m.timestamp = CommonState.getTime();

    return m;
  }

  public BigInteger getKademliaId() {
    return this.getKademliaProtocol().getKademliaNode().getId();
  }
}